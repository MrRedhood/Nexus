package com.mrredhood.nexus.core.ai

import com.mrredhood.nexus.core.workspace.Workspace
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
    val message: String,
    val snapshotId: String? = null
) {
    val canRollback: Boolean get() = success && snapshotId != null
}

object NexusActionExecutionRegistry {
    private val _executions = MutableStateFlow<Map<String, NexusActionExecutionSummary>>(emptyMap())
    val executions: StateFlow<Map<String, NexusActionExecutionSummary>> = _executions.asStateFlow()
    private val rollbackHandlers = mutableMapOf<String, suspend (Workspace, String) -> Boolean>()

    fun record(summary: NexusActionExecutionSummary) {
        _executions.value = _executions.value + (summary.actionId to summary)
    }

    fun registerRollback(actionId: String, handler: suspend (Workspace, String) -> Boolean) {
        rollbackHandlers[actionId] = handler
    }

    suspend fun rollback(workspace: Workspace, actionId: String): Boolean {
        val summary = _executions.value[actionId] ?: return false
        val snapshotId = summary.snapshotId ?: return false
        val handler = rollbackHandlers[actionId] ?: return false
        val restored = handler(workspace, snapshotId)
        if (restored) {
            _executions.value = _executions.value + (actionId to summary.copy(
                message = "Rolled back ${summary.path ?: summary.actionType}."
            ))
            rollbackHandlers.remove(actionId)
        }
        return restored
    }

    fun clear() {
        _executions.value = emptyMap()
        rollbackHandlers.clear()
    }
}
