package com.mrredhood.nexus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.nexus.core.ai.AiModel
import com.mrredhood.nexus.core.ai.AiProviderService
import com.mrredhood.nexus.core.ai.ModelCatalog
import com.mrredhood.nexus.core.settings.AdvancedSettingsRepository
import com.mrredhood.nexus.core.settings.ApiKeyStore
import com.mrredhood.nexus.core.settings.NexusFeatureSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdvancedSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AdvancedSettingsRepository(application.applicationContext)
    private val apiKeys = ApiKeyStore(application.applicationContext)
    private val aiService = AiProviderService(application.applicationContext)
    private val catalog = ModelCatalog(application.applicationContext)

    val settings: StateFlow<NexusFeatureSettings> = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NexusFeatureSettings())
    private val _models = MutableStateFlow<List<AiModel>>(emptyList())
    val models: StateFlow<List<AiModel>> = _models.asStateFlow()
    private val _loadingModels = MutableStateFlow(false)
    val loadingModels: StateFlow<Boolean> = _loadingModels.asStateFlow()
    private val _modelError = MutableStateFlow<String?>(null)
    val modelError: StateFlow<String?> = _modelError.asStateFlow()

    fun update(transform: (NexusFeatureSettings) -> NexusFeatureSettings) { viewModelScope.launch { repository.update(transform) } }

    fun saveApiKey(provider: String, apiKey: String) {
        apiKeys.put(provider, apiKey.trim())
        update { it.copy(apiKeyConfigured = apiKeys.has(provider)) }
        loadModels(provider)
    }

    fun clearApiKey(provider: String) {
        apiKeys.remove(provider)
        update { it.copy(apiKeyConfigured = false, model = "default") }
        _models.value = emptyList()
    }

    fun hasApiKey(provider: String): Boolean = apiKeys.has(provider)

    fun loadModels(provider: String = settings.value.provider) {
        viewModelScope.launch {
            _loadingModels.value = true
            _modelError.value = null
            try {
                if (!apiKeys.has(provider)) { _models.value = emptyList(); return@launch }
                val models = catalog.load(provider)
                _models.value = models
                if (models.isEmpty()) _modelError.value = "No models were returned by $provider. Check the API key or endpoint."
            } catch (error: Throwable) {
                _models.value = emptyList()
                _modelError.value = error.message ?: "Unable to load models."
            } finally { _loadingModels.value = false }
        }
    }

    fun testConnection(onResult: (Boolean, String) -> Unit) { viewModelScope.launch { val result = aiService.testConnection(); onResult(result.success, result.message) } }
}
