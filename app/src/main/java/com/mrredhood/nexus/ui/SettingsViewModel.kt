package com.mrredhood.nexus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.nexus.core.settings.NexusSettings
import com.mrredhood.nexus.core.settings.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application.applicationContext)
    val settings: StateFlow<NexusSettings> = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NexusSettings())

    fun update(transform: (NexusSettings) -> NexusSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
