package com.mrredhood.nexus.core.ai

import java.util.UUID

/**
 * State model for a Nexus engineering task.
 * The model keeps planning, approval, execution, verification and completion
 * explicit so the UI/agent loop cannot silently skip a safety gate.
 */
enum class EngineeringTaskStage {
    INSPECT,
    PLAN,
    APPROVAL,
    EDIT,
    TEST,
    BUILD,
    VERIFY,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class EngineeringTask(
    val id: String = UUID.randomUUID().toString(),
    val request: String,
    val stage: EngineeringTaskStage = EngineeringTaskStage.INSPECT,
    val plan: List<String> = emptyList(),
    val completedSteps: List<String> = emptyList(),
    val lastResult: String? = null,
    val error: String? = null
) {
    fun withPlan(steps: List<String>): EngineeringTask = copy(
        stage = EngineeringTaskStage.APPROVAL,
        plan = steps.filter { it.isNotBlank() },
        error = null
    )

    fun approve(): EngineeringTask {
        require(stage == EngineeringTaskStage.APPROVAL) { "Task is not waiting for approval." }
        return copy(stage = EngineeringTaskStage.EDIT, error = null)
    }

    fun completeStep(step: String, result: String? = null): EngineeringTask {
        require(stage !in setOf(EngineeringTaskStage.COMPLETED, EngineeringTaskStage.FAILED, EngineeringTaskStage.CANCELLED)) {
            "Task is already finished."
        }
        val next = when (stage) {
            EngineeringTaskStage.INSPECT -> EngineeringTaskStage.PLAN
            EngineeringTaskStage.PLAN -> EngineeringTaskStage.APPROVAL
            EngineeringTaskStage.APPROVAL -> EngineeringTaskStage.EDIT
            EngineeringTaskStage.EDIT -> EngineeringTaskStage.TEST
            EngineeringTaskStage.TEST -> EngineeringTaskStage.BUILD
            EngineeringTaskStage.BUILD -> EngineeringTaskStage.VERIFY
            EngineeringTaskStage.VERIFY -> EngineeringTaskStage.COMPLETED
            else -> stage
        }
        return copy(
            stage = next,
            completedSteps = completedSteps + step,
            lastResult = result,
            error = null
        )
    }

    fun fail(message: String): EngineeringTask = copy(
        stage = EngineeringTaskStage.FAILED,
        error = message.ifBlank { "Engineering task failed." }
    )

    fun cancel(): EngineeringTask = copy(stage = EngineeringTaskStage.CANCELLED, error = null)
}

/** Lightweight gate used by the agent before it advances an engineering task. */
object EngineeringTaskGate {
    fun canAdvance(task: EngineeringTask, permissionMode: String): Boolean = when (task.stage) {
        EngineeringTaskStage.INSPECT,
        EngineeringTaskStage.PLAN,
        EngineeringTaskStage.TEST,
        EngineeringTaskStage.BUILD,
        EngineeringTaskStage.VERIFY -> true
        EngineeringTaskStage.APPROVAL -> permissionMode.lowercase() != "never"
        EngineeringTaskStage.EDIT -> permissionMode.lowercase() in setOf("some", "autonomous", "standard", "full")
        EngineeringTaskStage.COMPLETED,
        EngineeringTaskStage.FAILED,
        EngineeringTaskStage.CANCELLED -> false
    }
}
