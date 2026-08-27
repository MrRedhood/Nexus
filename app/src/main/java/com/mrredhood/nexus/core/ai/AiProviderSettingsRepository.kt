package com.mrredhood.nexus.core.ai

import android.content.Context
import com.mrredhood.nexus.core.settings.AdvancedSettingsRepository
import com.mrredhood.nexus.core.settings.ApiKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** AI-provider-only configuration facade. GitHub and Copilot credentials are deliberately absent. */
data class AiProviderSettings(
    val provider: String = "Gemini",
    val model: String = "default",
    val endpoint: String = ""
)

class AiProviderSettingsRepository(context: Context) {
    private val settings = AdvancedSettingsRepository(context.applicationContext)
    private val keys = ApiKeyStore(context.applicationContext)

    val configuration: Flow<AiProviderSettings> = settings.settings.map {
        AiProviderSettings(provider = it.provider, model = it.model, endpoint = it.endpoint)
    }

    suspend fun update(transform: (AiProviderSettings) -> AiProviderSettings) {
        val current = configuration.first()
        val next = transform(current)
        settings.update {
            it.copy(
                provider = next.provider.trim().ifBlank { current.provider },
                model = next.model.trim().ifBlank { "default" },
                endpoint = next.endpoint.trim()
            )
        }
    }

    fun saveApiKey(provider: String, key: String) = keys.put(provider.trim(), key.trim())
    fun removeApiKey(provider: String) = keys.remove(provider.trim())
    fun hasApiKey(provider: String): Boolean = keys.has(provider.trim())
    fun getApiKey(provider: String): String? = keys.get(provider.trim())
}
