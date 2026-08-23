package com.mrredhood.nexus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.nexus.core.settings.AdvancedSettingsRepository
import com.mrredhood.nexus.core.settings.NexusFeatureSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdvancedSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AdvancedSettingsRepository(application.applicationContext)
    val settings: StateFlow<NexusFeatureSettings> = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), NexusFeatureSettings()
    )

    fun update(transform: (NexusFeatureSettings) -> NexusFeatureSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
