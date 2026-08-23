package com.mrredhood.nexus.core.settings

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide live settings bridge for non-UI Nexus services.
 *
 * SettingsRepository remains the source of truth. This bridge mirrors it so editor,
 * workspace, AI, terminal, CI and agent services can consume the same live values
 * without each service creating its own DataStore subscription.
 */
object NexusSettingsRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _settings = MutableStateFlow(NexusSettings())
    val settings: StateFlow<NexusSettings> = _settings.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            val repository = SettingsRepository(context.applicationContext)
            scope.launch {
                repository.settings.collect { _settings.value = it }
            }
        }
    }

    fun current(): NexusSettings = _settings.value
}
