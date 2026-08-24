package com.mrredhood.nexus.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Execution metadata surfaced by the AI change panel. */
data class NexusActionExecutionSummary(
    val actionId: String,
    val actionType: String,
    val path: String?,
    val success: Boolean,
    val additions: Int = 0,
    val deletions: Int = 0,
    val message: String
)

object NexusActionExecutionRegistry {
    private val _executions = MutableStateFlow<Map<String, NexusActionExecutionSummary>>(emptyMap())
    val executions: StateFlow<Map<String, NexusActionExecutionSummary>> = _executions.asStateFlow()

    fun record(summary: NexusActionExecutionSummary) {
        _executions.value = _executions.value + (summary.actionId to summary)
    }

    fun clear() { _executions.value = emptyMap() }
}
