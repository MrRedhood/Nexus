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

/** Live provider model catalogue. Metadata is best-effort and never hard-coded as availability. */
class ModelCatalog(context: Context) {
    private val settings = AdvancedSettingsRepository(context.applicationContext)
    private val keys = ApiKeyStore(context.applicationContext)

    suspend fun load(providerOverride: String? = null): List<AiModel> = withContext(Dispatchers.IO) {
        val config = settings.settings.first()
        val provider = providerOverride?.trim().takeUnless { it.isNullOrBlank() } ?: config.provider.trim()
        val key = keys.get(provider) ?: return@withContext emptyList()
        runCatching {
            when {
                provider.equals("Gemini", true) -> gemini(key)
                provider.equals("Anthropic", true) -> anthropic(key, config.endpoint)
                else -> openAiCompatible(provider, key, config.endpoint)
            }
        }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() }
    }

    private fun gemini(key: String): List<AiModel> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${encode(key)}"
        val json = get(url, null)
        val data = json.optJSONArray("models") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val methods = item.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                var text = false
                for (j in 0 until methods.length()) if (methods.optString(j).contains("generateContent")) text = true
                if (!text) continue
                val raw = item.optString("name").removePrefix("models/")
                if (raw.isBlank()) continue
                add(AiModel(raw, item.optString("displayName", raw), "Gemini", ModelPricing.UNKNOWN, setOf("text")))
            }
        }
    }

    private fun anthropic(key: String, configuredEndpoint: String): List<AiModel> {
        val endpoint = configuredEndpoint.trim().ifBlank { "https://api.anthropic.com/v1/models" }
        val json = get(endpoint, null, mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"))
        val data = json.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isNotBlank()) add(AiModel(id, item.optString("display_name", id), "Anthropic", ModelPricing.UNKNOWN, setOf("text")))
            }
        }
    }

    private fun openAiCompatible(provider: String, key: String, configuredEndpoint: String): List<AiModel> {
        val endpoint = configuredEndpoint.trim().ifBlank { defaultEndpoint(provider) }
        if (endpoint.isBlank()) return emptyList()
        val modelsUrl = endpoint.substringBefore("/chat/completions").trimEnd('/') + "/models"
        val json = get(modelsUrl, "Bearer $key")
        val data = json.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val pricing = item.optJSONObject("pricing")
                val prompt = pricing?.optDouble("prompt", Double.NaN) ?: Double.NaN
                val completion = pricing?.optDouble("completion", Double.NaN) ?: Double.NaN
                val modalities = mutableSetOf<String>()
                collectModalities(item.optJSONObject("architecture")?.optJSONArray("input_modalities"), modalities)
                collectModalities(item.optJSONObject("architecture")?.optJSONArray("output_modalities"), modalities)
                if (modalities.isEmpty()) modalities.add("text")
                val cost = if (!prompt.isNaN() && !completion.isNaN()) ModelPricing(prompt, completion) else ModelPricing.UNKNOWN
                add(AiModel(id, item.optString("name", id), provider, cost, modalities))
            }
        }
    }

    private fun collectModalities(array: JSONArray?, target: MutableSet<String>) {
        if (array == null) return
        for (i in 0 until array.length()) {
            when {
                array.optString(i).contains("image", true) -> target.add("image")
                array.optString(i).contains("video", true) -> target.add("video")
                array.optString(i).contains("audio", true) -> target.add("audio")
                array.optString(i).contains("text", true) -> target.add("text")
            }
        }
    }

    private fun defaultEndpoint(provider: String) = when {
        provider.equals("OpenAI", true) -> "https://api.openai.com/v1/chat/completions"
        provider.equals("OpenRouter", true) -> "https://openrouter.ai/api/v1/chat/completions"
        provider.equals("Groq", true) -> "https://api.groq.com/openai/v1/chat/completions"
        provider.equals("Mistral", true) -> "https://api.mistral.ai/v1/chat/completions"
        provider.equals("DeepSeek", true) -> "https://api.deepseek.com/chat/completions"
        provider.equals("xAI", true) -> "https://api.x.ai/v1/chat/completions"
        provider.equals("Together AI", true) -> "https://api.together.xyz/v1/chat/completions"
        provider.equals("Fireworks AI", true) -> "https://api.fireworks.ai/inference/v1/chat/completions"
        provider.equals("Cerebras", true) -> "https://api.cerebras.ai/v1/chat/completions"
        provider.equals("Perplexity", true) -> "https://api.perplexity.ai/chat/completions"
        provider.equals("DeepInfra", true) -> "https://api.deepinfra.com/v1/openai/chat/completions"
        provider.equals("LiteLLM", true) -> "http://localhost:4000/v1/chat/completions"
        provider.equals("Custom OpenAI-compatible", true) -> ""
        else -> ""
    }

    private fun get(endpoint: String, authorization: String?, headers: Map<String, String> = emptyMap()): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 20_000; readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            authorization?.let { setRequestProperty("Authorization", it) }
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else (connection.errorStream ?: connection.inputStream)
            val body = stream.bufferedReader().use { it.readText() }
            if (status !in 200..299) throw IllegalStateException("HTTP $status: $body")
            return JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

data class ModelPricing(val prompt: Double, val completion: Double) {
    val known: Boolean get() = prompt >= 0.0 && completion >= 0.0
    val free: Boolean get() = known && prompt == 0.0 && completion == 0.0
    companion object { val UNKNOWN = ModelPricing(-1.0, -1.0) }
}

data class AiModel(val id: String, val name: String, val provider: String, val pricing: ModelPricing, val modalities: Set<String>) {
    val premium: Boolean get() = pricing.known && !pricing.free
    val image: Boolean get() = "image" in modalities
    val video: Boolean get() = "video" in modalities
    val audio: Boolean get() = "audio" in modalities
    val text: Boolean get() = "text" in modalities
}
