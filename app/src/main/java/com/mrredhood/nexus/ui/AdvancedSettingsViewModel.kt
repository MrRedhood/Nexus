package com.mrredhood.nexus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.nexus.core.settings.AdvancedSettingsRepository
import com.mrredhood.nexus.core.settings.ApiKeyStore
import com.mrredhood.nexus.core.settings.NexusFeatureSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdvancedSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AdvancedSettingsRepository(application.applicationContext)
    private val apiKeys = ApiKeyStore(application.applicationContext)

    val settings: StateFlow<NexusFeatureSettings> = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), NexusFeatureSettings()
    )

    fun update(transform: (NexusFeatureSettings) -> NexusFeatureSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun saveApiKey(provider: String, apiKey: String) {
        apiKeys.put(provider, apiKey)
        update { it.copy(apiKeyConfigured = apiKeys.has(provider)) }
    }

    fun clearApiKey(provider: String) {
        apiKeys.remove(provider)
        update { it.copy(apiKeyConfigured = false) }
    }

    fun hasApiKey(provider: String): Boolean = apiKeys.has(provider)
}
