package com.mrredhood.nexus.agent

import kotlinx.serialization.Serializable

@Serializable
enum class AgentState {
    IDLE,
    THINKING,
    INSPECTING,
    PLANNING,
    AWAITING_APPROVAL,
    EXECUTING,
    VALIDATING,
    BUILDING,
    DIAGNOSING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Serializable
data class AgentSnapshot(
    val schemaVersion: Int = 1,
    val operationId: String,
    val state: AgentState,
    val updatedAtEpochMillis: Long,
    val activeActionIds: List<String> = emptyList(),
    val message: String? = null,
)
