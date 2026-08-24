package com.mrredhood.nexus.core.ai

import android.content.Context
import com.mrredhood.nexus.core.settings.AdvancedSettingsRepository
import com.mrredhood.nexus.core.settings.ApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Provider runtime for buffered and incremental AI responses. */
class AiProviderService(context: Context) {
    private val settings = AdvancedSettingsRepository(context.applicationContext)
    private val keys = ApiKeyStore(context.applicationContext)

    suspend fun complete(request: AiRequest): ProviderResult = withContext(Dispatchers.IO) { execute(request, false) { } }
    suspend fun stream(request: AiRequest, onDelta: suspend (String) -> Unit): ProviderResult = withContext(Dispatchers.IO) { execute(request, true, onDelta) }

    suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val config = settings.settings.first()
        val provider = config.provider.trim()
        val key = keys.get(provider) ?: return@withContext ConnectionTestResult(false, "No API key configured for $provider.")
        val model = resolveModel(provider, "", config.model)
        if (model.isBlank()) return@withContext ConnectionTestResult(false, "No model configured for $provider.")
        runCatching {
            val result = execute(AiRequest(listOf(AiMessage("user", "Reply with OK.")), model = model, temperature = 0.0, maxOutputTokens = 8), false) { }
            if (result.success) ConnectionTestResult(true, "Connection successful with $provider / $model.") else ConnectionTestResult(false, result.message.ifBlank { "Connection failed." })
        }.getOrElse { ConnectionTestResult(false, it.message ?: "Connection failed.") }
    }

    private suspend fun execute(request: AiRequest, streaming: Boolean, onDelta: suspend (String) -> Unit): ProviderResult {
        val config = settings.settings.first()
        val provider = config.provider.trim()
        val key = keys.get(provider) ?: return ProviderResult(false, "No API key configured for $provider.")
        val model = resolveModel(provider, request.model, config.model)
        if (model.isBlank()) return ProviderResult(false, "No model configured for $provider.")
        return runCatching {
            if (provider.equals("Gemini", true)) gemini(key, model, request, streaming, onDelta) else openAi(provider, key, model, request, config.endpoint, streaming, onDelta)
        }.getOrElse { ProviderResult(false, it.message ?: "AI request failed.") }
    }

    private suspend fun gemini(key: String, model: String, request: AiRequest, streaming: Boolean, onDelta: suspend (String) -> Unit): ProviderResult {
        val action = if (streaming) "streamGenerateContent?alt=sse" else "generateContent"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${encode(model)}:$action&key=${encode(key)}".replace("generateContent&key", "generateContent?key")
        val body = JSONObject().apply {
            put("contents", JSONArray().also { a -> request.messages.filter { it.role != "system" }.forEach { m -> a.put(JSONObject().put("role", if (m.role == "assistant") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", m.content)))) } })
            request.messages.firstOrNull { it.role == "system" }?.let { put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", it.content)))) }
            put("generationConfig", JSONObject().put("temperature", request.temperature).put("maxOutputTokens", request.maxOutputTokens))
        }
        return readResponse(url, body.toString(), null, streaming, "Gemini", model, onDelta) { json -> json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty() }
    }

    private suspend fun openAi(provider: String, key: String, model: String, request: AiRequest, configuredEndpoint: String, streaming: Boolean, onDelta: suspend (String) -> Unit): ProviderResult {
        val endpoint = configuredEndpoint.trim().ifBlank { when {
            provider.equals("OpenRouter", true) -> "https://openrouter.ai/api/v1/chat/completions"
            provider.equals("DeepInfra", true) -> "https://api.deepinfra.com/v1/openai/chat/completions"
            provider.equals("LiteLLM", true) -> "http://localhost:4000/v1/chat/completions"
            else -> ""
        } }
        require(endpoint.isNotBlank()) { "No endpoint configured for $provider." }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().also { a -> request.messages.forEach { a.put(JSONObject().put("role", it.role).put("content", it.content)) } })
            put("temperature", request.temperature); put("max_tokens", request.maxOutputTokens); put("stream", streaming)
        }
        return readResponse(endpoint, body.toString(), "Bearer $key", streaming, provider, model, onDelta) { json -> if (streaming) json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty() else json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty() }
    }

    private suspend fun readResponse(endpoint: String, body: String, auth: String?, streaming: Boolean, provider: String, model: String, onDelta: suspend (String) -> Unit, extract: (JSONObject) -> String): ProviderResult {
        val c = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 120_000; doOutput = true
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("Accept", if (streaming) "text/event-stream" else "application/json")
            auth?.let { setRequestProperty("Authorization", it) }
        }
        currentCoroutineContext()[Job]?.invokeOnCompletion { c.disconnect() }
        val text = StringBuilder()
        try {
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = c.responseCode
            if (status !in 200..299) {
                val error = (c.errorStream ?: c.inputStream).bufferedReader().use { it.readText() }
                throw IllegalStateException("HTTP $status: $error")
            }
            if (streaming) {
                c.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (!line.startsWith("data:")) return@forEach
                        val data = line.removePrefix("data:").trim()
                        if (data.isBlank() || data == "[DONE]") return@forEach
                        val delta = runCatching { extract(JSONObject(data)) }.getOrDefault("")
                        if (delta.isNotEmpty()) { text.append(delta); onDelta(delta) }
                    }
                }
            } else {
                val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                text.append(extract(json))
                val usage = json.optJSONObject("usage") ?: json.optJSONObject("usageMetadata")
                return ProviderResult(true, text.toString(), AiResponse(text.toString(), model, provider, usage?.optIntOrNull("prompt_tokens", "promptTokenCount"), usage?.optIntOrNull("completion_tokens", "candidatesTokenCount")))
            }
            return ProviderResult(true, text.toString(), AiResponse(text.toString(), model, provider))
        } finally { c.disconnect() }
    }

    private fun resolveModel(provider: String, requested: String, configured: String) = when {
        requested.isNotBlank() && requested != "default" -> requested
        configured.isNotBlank() && configured != "default" -> configured
        provider.equals("Gemini", true) -> "gemini-2.5-flash"
        provider.equals("OpenRouter", true) -> "google/gemini-2.5-flash"
        provider.equals("DeepInfra", true) -> "meta-llama/Llama-3.3-70B-Instruct"
        else -> ""
    }

    private fun encode(v: String) = URLEncoder.encode(v, Charsets.UTF_8.name()).replace("+", "%20")
    private fun JSONObject.optIntOrNull(vararg names: String): Int? = names.firstOrNull { has(it) && !isNull(it) }?.let { optInt(it) }
}

data class ConnectionTestResult(val success: Boolean, val message: String)
