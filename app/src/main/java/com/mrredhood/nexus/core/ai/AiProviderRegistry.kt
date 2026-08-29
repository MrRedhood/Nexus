package com.mrredhood.nexus.core.ai

import android.content.Context
import com.mrredhood.nexus.core.settings.ApiKeyStore
import org.json.JSONArray
import org.json.JSONObject

/** Persistent metadata for API model providers. Secrets remain in ApiKeyStore. */
class AiProviderRegistry(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keys = ApiKeyStore(context.applicationContext)

    data class Provider(
        val name: String,
        val model: String = "default",
        val endpoint: String = ""
    ) {
        val configured: Boolean get() = model.isNotBlank() && model != "default"
    }

    fun list(): List<Provider> = runCatching {
        val json = JSONArray(prefs.getString(PROVIDERS, "[]") ?: "[]")
        buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isNotBlank()) add(Provider(name, item.optString("model", "default"), item.optString("endpoint")))
            }
        }
    }.getOrDefault(emptyList())

    fun save(provider: Provider, apiKey: String) {
        val normalized = provider.copy(name = provider.name.trim(), model = provider.model.trim().ifBlank { "default" }, endpoint = provider.endpoint.trim())
        if (normalized.name.isBlank()) return
        val providers = list().filterNot { it.name.equals(normalized.name, true) } + normalized
        prefs.edit().putString(PROVIDERS, JSONArray().apply { providers.forEach { put(JSONObject().apply { put("name", it.name); put("model", it.model); put("endpoint", it.endpoint) }) } }.toString()).apply()
        if (apiKey.isNotBlank()) keys.put(normalized.name, apiKey.trim())
    }

    fun remove(name: String) {
        val providers = list().filterNot { it.name.equals(name, true) }
        prefs.edit().putString(PROVIDERS, JSONArray().apply { providers.forEach { put(JSONObject().apply { put("name", it.name); put("model", it.model); put("endpoint", it.endpoint) }) } }.toString()).apply()
        keys.remove(name.trim())
    }

    fun getKey(name: String): String? = keys.get(name.trim())
    fun hasKey(name: String): Boolean = keys.has(name.trim())

    companion object { private const val PREFERENCES = "nexus_ai_provider_registry"; private const val PROVIDERS = "providers" }
}
