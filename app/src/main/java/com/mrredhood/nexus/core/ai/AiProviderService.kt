package com.mrredhood.nexus.core.ai

import android.content.Context
import com.mrredhood.nexus.core.settings.AdvancedSettingsRepository
import com.mrredhood.nexus.core.settings.ApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Executes the configured AI provider without bundling a provider SDK. */
class AiProviderService(context: Context) {
    private val appContext = context.applicationContext
    private val settingsRepository = AdvancedSettingsRepository(appContext)
    private val apiKeys = ApiKeyStore(appContext)

    suspend fun complete(request: AiRequest): ProviderResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.first()
        val provider = settings.provider.trim()
        val apiKey = apiKeys.get(provider)
            ?: return@withContext ProviderResult(false, "No API key configured for $provider.")
        val model = resolveModel(provider, request.model, settings.model)
        if (model.isBlank()) return@withContext ProviderResult(false, "No model configured for $provider.")

        runCatching {
            if (provider.equals("Gemini", ignoreCase = true)) {
                completeGemini(apiKey, model, request)
            } else {
                completeOpenAiCompatible(provider, apiKey, model, request, settings.endpoint)
            }
        }.getOrElse { error -> ProviderResult(false, error.message ?: "AI request failed.") }
    }

    suspend fun testConnection(): ProviderResult {
        val settings = settingsRepository.settings.first()
        val model = resolveModel(settings.provider, "", settings.model)
        return complete(AiRequest(listOf(AiMessage("user", "Reply with exactly: Nexus connection OK")), model, 0.0, 32, false))
    }

    private fun completeGemini(apiKey: String, model: String, request: AiRequest): ProviderResult {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${encodePath(model)}:generateContent?key=${urlEncode(apiKey)}"
        val contents = JSONArray()
        request.messages.forEach { message ->
            contents.put(JSONObject().apply {
                put("role", if (message.role == "assistant") "model" else "user")
                put("parts", JSONArray().put(JSONObject().put("text", message.content)))
            })
        }
        val body = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxOutputTokens)
            })
        }
        val json = post(endpoint, body.toString(), null)
        val text = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
        if (text.isBlank()) return ProviderResult(false, "Gemini returned an empty response.")
        val usage = json.optJSONObject("usageMetadata")
        return ProviderResult(true, text, AiResponse(text, model, "Gemini", usage?.optIntOrNull("promptTokenCount"), usage?.optIntOrNull("candidatesTokenCount")))
    }

    private fun completeOpenAiCompatible(provider: String, apiKey: String, model: String, request: AiRequest, configuredEndpoint: String): ProviderResult {
        val endpoint = configuredEndpoint.trim().ifBlank { defaultEndpoint(provider) }
        require(endpoint.isNotBlank()) { "No endpoint configured for $provider." }
        val messages = JSONArray()
        request.messages.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", request.temperature)
            put("max_tokens", request.maxOutputTokens)
            put("stream", false)
        }
        val json = post(endpoint, body.toString(), "Bearer $apiKey")
        val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        if (text.isBlank()) return ProviderResult(false, "$provider returned an empty response.")
        val usage = json.optJSONObject("usage")
        return ProviderResult(true, text, AiResponse(text, model, provider, usage?.optIntOrNull("prompt_tokens"), usage?.optIntOrNull("completion_tokens")))
    }

    private fun post(endpoint: String, body: String, authorization: String?): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            authorization?.let { setRequestProperty("Authorization", it) }
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(response) }.getOrElse { JSONObject().put("error", JSONObject().put("message", response)) }
            if (status !in 200..299) throw IllegalStateException("HTTP $status: ${json.optJSONObject("error")?.optString("message") ?: response}")
            json
        } finally { connection.disconnect() }
    }

    private fun resolveModel(provider: String, requested: String, configured: String): String {
        if (requested.isNotBlank() && requested != "default") return requested
        if (configured.isNotBlank() && configured != "default") return configured
        return when {
            provider.equals("Gemini", true) -> "gemini-2.5-flash"
            provider.equals("OpenRouter", true) -> "google/gemini-2.5-flash"
            provider.equals("DeepInfra", true) -> "meta-llama/Llama-3.3-70B-Instruct"
            else -> ""
        }
    }

    private fun defaultEndpoint(provider: String): String = when {
        provider.equals("OpenRouter", true) -> "https://openrouter.ai/api/v1/chat/completions"
        provider.equals("DeepInfra", true) -> "https://api.deepinfra.com/v1/openai/chat/completions"
        provider.equals("LiteLLM", true) -> "http://localhost:4000/v1/chat/completions"
        else -> ""
    }

    private fun encodePath(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun urlEncode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null
}
