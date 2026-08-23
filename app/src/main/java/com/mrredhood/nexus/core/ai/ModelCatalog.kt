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
                add(AiModel(id = raw, name = item.optString("displayName", raw), provider = "Gemini", pricing = ModelPricing.UNKNOWN, modalities = setOf("text")))
            }
        }
    }

    private fun openAiCompatible(provider: String, key: String, configuredEndpoint: String): List<AiModel> {
        val endpoint = configuredEndpoint.trim().ifBlank {
            when {
                provider.equals("OpenRouter", true) -> "https://openrouter.ai/api/v1/chat/completions"
                provider.equals("DeepInfra", true) -> "https://api.deepinfra.com/v1/openai/chat/completions"
                provider.equals("LiteLLM", true) -> "http://localhost:4000/v1/chat/completions"
                else -> ""
            }
        }
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
                val inputModalities = item.optJSONObject("architecture")?.optJSONArray("input_modalities")
                val outputModalities = item.optJSONObject("architecture")?.optJSONArray("output_modalities")
                val modalities = mutableSetOf<String>()
                collectModalities(inputModalities, modalities)
                collectModalities(outputModalities, modalities)
                if (modalities.isEmpty()) modalities.add("text")
                val cost = if (!prompt.isNaN() && !completion.isNaN()) ModelPricing(prompt, completion) else ModelPricing.UNKNOWN
                add(AiModel(id, item.optString("name", id), provider, cost, modalities))
            }
        }
    }

    private fun collectModalities(array: JSONArray?, target: MutableSet<String>) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val value = array.optString(i).lowercase()
            when {
                value.contains("image") -> target.add("image")
                value.contains("video") -> target.add("video")
                value.contains("audio") -> target.add("audio")
                value.contains("text") -> target.add("text")
            }
        }
    }

    private fun get(endpoint: String, authorization: String?): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            authorization?.let { setRequestProperty("Authorization", it) }
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

data class AiModel(
    val id: String,
    val name: String,
    val provider: String,
    val pricing: ModelPricing,
    val modalities: Set<String>
) {
    val premium: Boolean get() = pricing.known && !pricing.free
    val image: Boolean get() = "image" in modalities
    val video: Boolean get() = "video" in modalities
    val audio: Boolean get() = "audio" in modalities
    val text: Boolean get() = "text" in modalities
}
