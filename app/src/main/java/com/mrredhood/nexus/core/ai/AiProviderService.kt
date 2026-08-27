package com.mrredhood.nexus.core.ai

import android.content.Context
import com.mrredhood.nexus.core.settings.AdvancedSettingsRepository
import com.mrredhood.nexus.core.settings.ApiKeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Provider runtime with bounded retries, explicit same-provider fallbacks and usage accounting. */
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
        if (model.isBlank()) return@withContext ConnectionTestResult(false, "Select a model for $provider first.")
        try {
            val result = execute(AiRequest(listOf(AiMessage("user", "Reply with OK.")), model, temperature = 0.0, maxOutputTokens = 8), false) { }
            if (result.success) ConnectionTestResult(true, "Connection successful with $provider / $model.")
            else ConnectionTestResult(false, result.message.ifBlank { "Connection failed." })
        } catch (e: CancellationException) { throw e }
        catch (e: Throwable) { ConnectionTestResult(false, e.message ?: "Connection failed.") }
    }

    private suspend fun execute(request: AiRequest, streaming: Boolean, onDelta: suspend (String) -> Unit): ProviderResult {
        val config = settings.settings.first()
        val provider = config.provider.trim()
        val key = keys.get(provider) ?: return failure("missing_api_key", "No API key configured for $provider.")
        val primary = resolveModel(provider, request.model, config.model)
        if (primary.isBlank()) return failure("missing_model", "No model configured for $provider.")
        val candidates = buildList {
            add(primary)
            request.fallbackModels.map { it.trim() }.filter { it.isNotBlank() && it != primary }.forEach(::add)
        }

        var totalAttempts = 0
        var last: ProviderResult? = null
        for (model in candidates) {
            val result = runWithRetry(provider, key, model, request, config.endpoint, streaming, onDelta)
            totalAttempts += result.attempts
            if (result.success) return result.copy(attempts = totalAttempts)
            last = result
            // Streaming responses cannot be safely replayed after deltas were emitted.
            if (streaming) break
            if (result.error?.code == "missing_model" || result.error?.code == "invalid_request") break
        }
        return (last ?: failure("request_failed", "AI request failed.")).copy(attempts = totalAttempts.coerceAtLeast(1))
    }

    private suspend fun runWithRetry(
        provider: String,
        key: String,
        model: String,
        request: AiRequest,
        endpoint: String,
        streaming: Boolean,
        onDelta: suspend (String) -> Unit
    ): ProviderResult {
        val maxAttempts = if (streaming) 1 else 3
        var last: ProviderResult? = null
        for (attempt in 1..maxAttempts) {
            val result = try {
                when {
                    provider.equals("Gemini", true) -> gemini(key, model, request, streaming, onDelta)
                    provider.equals("Anthropic", true) -> anthropic(key, model, request, endpoint, streaming, onDelta)
                    else -> openAi(provider, key, model, request, endpoint, streaming, onDelta)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { failure("request_failed", e.message ?: "AI request failed.") }
            last = result.copy(attempts = attempt)
            if (result.success || result.error?.retryable != true || attempt == maxAttempts) return last
            delay(250L * attempt)
        }
        return last ?: failure("request_failed", "AI request failed.")
    }

    private suspend fun gemini(key: String, model: String, request: AiRequest, streaming: Boolean, onDelta: suspend (String) -> Unit): ProviderResult {
        val url = if (streaming) "https://generativelanguage.googleapis.com/v1beta/models/${encode(model)}:streamGenerateContent?alt=sse&key=${encode(key)}" else "https://generativelanguage.googleapis.com/v1beta/models/${encode(model)}:generateContent?key=${encode(key)}"
        val body = JSONObject().apply {
            put("contents", JSONArray().also { a -> request.messages.filter { it.role != "system" }.forEach { m -> a.put(JSONObject().put("role", if (m.role == "assistant") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", m.content)))) } })
            request.messages.firstOrNull { it.role == "system" }?.let { put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", it.content)))) }
            put("generationConfig", JSONObject().put("temperature", request.temperature).put("maxOutputTokens", request.maxOutputTokens))
        }
        return readResponse(url, body.toString(), null, streaming, "Gemini", model, onDelta) { json ->
            json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
        }
    }

    private suspend fun anthropic(key: String, model: String, request: AiRequest, configuredEndpoint: String, streaming: Boolean, onDelta: suspend (String) -> Unit): ProviderResult {
        val endpoint = configuredEndpoint.trim().ifBlank { "https://api.anthropic.com/v1/messages" }
        val system = request.messages.firstOrNull { it.role == "system" }?.content
        val body = JSONObject().apply {
            put("model", model); put("max_tokens", request.maxOutputTokens); put("temperature", request.temperature); put("stream", streaming)
            system?.let { put("system", it) }
            put("messages", JSONArray().also { a -> request.messages.filter { it.role != "system" }.forEach { m -> a.put(JSONObject().put("role", if (m.role == "assistant") "assistant" else "user").put("content", m.content)) } })
        }
        return readResponse(endpoint, body.toString(), key, streaming, "Anthropic", model, onDelta) { json ->
            if (streaming) json.optJSONObject("delta")?.optString("text").orEmpty() else json.optJSONArray("content")?.optJSONObject(0)?.optString("text").orEmpty()
        }
    }

    private suspend fun openAi(provider: String, key: String, model: String, request: AiRequest, configuredEndpoint: String, streaming: Boolean, onDelta: suspend (String) -> Unit): ProviderResult {
        val endpoint = configuredEndpoint.trim().ifBlank { defaultEndpoint(provider) }
        if (endpoint.isBlank()) return failure("missing_endpoint", "No endpoint configured for $provider.")
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().also { a -> request.messages.forEach { a.put(JSONObject().put("role", it.role).put("content", it.content)) } })
            put("temperature", request.temperature); put("max_tokens", request.maxOutputTokens); put("stream", streaming)
            if (streaming) put("stream_options", JSONObject().put("include_usage", true))
        }
        return readResponse(endpoint, body.toString(), "Bearer $key", streaming, provider, model, onDelta) { json ->
            if (streaming) json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty() else json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        }
    }

    private suspend fun readResponse(endpoint: String, body: String, auth: String?, streaming: Boolean, provider: String, model: String, onDelta: suspend (String) -> Unit, extract: (JSONObject) -> String): ProviderResult {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 120_000; doOutput = true
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("Accept", if (streaming) "text/event-stream" else "application/json")
            if (provider.equals("Anthropic", true)) { setRequestProperty("x-api-key", auth ?: ""); setRequestProperty("anthropic-version", "2023-06-01") }
            else auth?.let { setRequestProperty("Authorization", it) }
        }
        currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
        val text = StringBuilder(); var inputTokens: Int? = null; var outputTokens: Int? = null; var totalTokens: Int? = null
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = (connection.errorStream ?: connection.inputStream).bufferedReader().use { it.readText() }
                val error = classifyHttpError(status, errorBody)
                return ProviderResult(false, error.message, error = error)
            }
            if (streaming) {
                connection.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                    if (!line.startsWith("data:")) return@forEach
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") return@forEach
                    val json = runCatching { JSONObject(data) }.getOrNull() ?: return@forEach
                    val usage = extractUsage(json)
                    if (usage != null) { inputTokens = usage.first ?: inputTokens; outputTokens = usage.second ?: outputTokens; totalTokens = usage.third ?: totalTokens }
                    val delta = runCatching { extract(json) }.getOrDefault("")
                    if (delta.isNotEmpty()) { text.append(delta); onDelta(delta) }
                } }
            } else {
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                text.append(extract(json))
                val usage = extractUsage(json)
                inputTokens = usage?.first; outputTokens = usage?.second; totalTokens = usage?.third
            }
            val response = AiResponse(text.toString(), model, provider, inputTokens, outputTokens, totalTokens)
            return ProviderResult(true, text.toString(), response = response)
        } catch (e: CancellationException) { throw e }
        catch (e: java.net.SocketTimeoutException) {
            val error = AiError("timeout", "The $provider request timed out.", retryable = true)
            return ProviderResult(false, error.message, error = error)
        } catch (e: java.io.IOException) {
            val error = AiError("network_error", e.message ?: "Network error while contacting $provider.", retryable = true)
            return ProviderResult(false, error.message, error = error)
        } catch (e: Throwable) {
            val error = AiError("parse_error", e.message ?: "Invalid response from $provider.")
            return ProviderResult(false, error.message, error = error)
        } finally { connection.disconnect() }
    }

    private fun classifyHttpError(status: Int, body: String): AiError {
        val code = when (status) { 401, 403 -> "authentication_error"; 404 -> "not_found"; 408 -> "timeout"; 409 -> "conflict"; 429 -> "rate_limited"; in 500..599 -> "provider_unavailable"; else -> "http_error" }
        val retryable = status == 408 || status == 429 || status in 500..599
        val message = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty().ifBlank { body.take(500).ifBlank { "HTTP $status from AI provider." } }
        return AiError(code, message, retryable, status)
    }

    private fun extractUsage(json: JSONObject): Triple<Int?, Int?, Int?>? {
        val usage = json.optJSONObject("usage") ?: json.optJSONObject("usageMetadata") ?: json.optJSONObject("message")?.optJSONObject("usage") ?: return null
        val input = firstInt(usage, "prompt_tokens", "promptTokenCount", "input_tokens", "inputTokens")
        val output = firstInt(usage, "completion_tokens", "candidatesTokenCount", "output_tokens", "outputTokens")
        val total = firstInt(usage, "total_tokens", "totalTokenCount", "totalTokens") ?: if (input != null && output != null) input + output else null
        return Triple(input, output, total)
    }

    private fun firstInt(json: JSONObject, vararg names: String): Int? = names.firstOrNull { json.has(it) && !json.isNull(it) }?.let { json.optInt(it).takeIf { value -> value >= 0 } }

    private fun failure(code: String, message: String, retryable: Boolean = false) = ProviderResult(false, message, error = AiError(code, message, retryable))

    private fun defaultEndpoint(provider: String) = when {
        provider.equals("OpenAI", true) -> "https://api.openai.com/v1/chat/completions"; provider.equals("OpenRouter", true) -> "https://openrouter.ai/api/v1/chat/completions"; provider.equals("Groq", true) -> "https://api.groq.com/openai/v1/chat/completions"; provider.equals("Mistral", true) -> "https://api.mistral.ai/v1/chat/completions"; provider.equals("DeepSeek", true) -> "https://api.deepseek.com/chat/completions"; provider.equals("xAI", true) -> "https://api.x.ai/v1/chat/completions"; provider.equals("Together AI", true) -> "https://api.together.xyz/v1/chat/completions"; provider.equals("Fireworks AI", true) -> "https://api.fireworks.ai/inference/v1/chat/completions"; provider.equals("Cerebras", true) -> "https://api.cerebras.ai/v1/chat/completions"; provider.equals("Perplexity", true) -> "https://api.perplexity.ai/chat/completions"; provider.equals("DeepInfra", true) -> "https://api.deepinfra.com/v1/openai/chat/completions"; provider.equals("LiteLLM", true) -> "http://localhost:4000/v1/chat/completions"; else -> ""
    }

    private fun resolveModel(provider: String, requested: String, configured: String) = when {
        requested.isNotBlank() && requested != "default" -> requested; configured.isNotBlank() && configured != "default" -> configured; provider.equals("Gemini", true) -> "gemini-2.5-flash"; provider.equals("OpenAI", true) -> "gpt-4.1-mini"; provider.equals("Anthropic", true) -> "claude-sonnet-4-20250514"; provider.equals("OpenRouter", true) -> "google/gemini-2.5-flash"; provider.equals("Groq", true) -> "llama-3.3-70b-versatile"; provider.equals("Mistral", true) -> "mistral-small-latest"; provider.equals("DeepSeek", true) -> "deepseek-chat"; provider.equals("xAI", true) -> "grok-4-1-fast-reasoning"; provider.equals("Together AI", true) -> "meta-llama/Llama-3.3-70B-Instruct-Turbo"; provider.equals("Fireworks AI", true) -> "accounts/fireworks/models/llama-v3p1-70b-instruct"; provider.equals("Cerebras", true) -> "llama-3.3-70b"; provider.equals("Perplexity", true) -> "sonar"; provider.equals("DeepInfra", true) -> "meta-llama/Llama-3.3-70B-Instruct"; else -> ""
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

data class ConnectionTestResult(val success: Boolean, val message: String)
