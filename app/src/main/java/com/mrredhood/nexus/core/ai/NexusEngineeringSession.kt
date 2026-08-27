package com.mrredhood.nexus.core.ai

/**
 * Event-driven engineering workflow used by the real chat/action pipeline.
 *
 * This session can pause at approval and resume when the UI actually executes an
 * action or receives CI results. It never invents test/build success.
 */
class NexusEngineeringSession(
    request: String,
    private val maxRecoveryAttempts: Int = 3
) {
    private var recoveryAttempts = 0

    var task: EngineeringTask = EngineeringTask(request = request)
        private set

    fun inspected(result: String): EngineeringTask {
        require(task.stage == EngineeringTaskStage.INSPECT) { "Inspection is not the current workflow stage." }
        task = task.completeStep("Inspect workspace", result)
        return task
    }

    fun planned(steps: List<String>): EngineeringTask {
        require(task.stage == EngineeringTaskStage.PLAN) { "Planning is not the current workflow stage." }
        require(steps.any { it.isNotBlank() }) { "Engineering plan must contain at least one step." }
        task = task.withPlan(steps)
        return task
    }

    fun approve(permissionMode: String): EngineeringTask {
        require(task.stage == EngineeringTaskStage.APPROVAL) { "Task is not waiting for approval." }
        if (!EngineeringTaskGate.canAdvance(task, permissionMode)) {
            task = task.fail("Engineering task requires approval, but AI permission is set to Never.")
            return task
        }
        task = task.approve()
        return task
    }

    /** Installs a recovery plan after a failed test/build while retaining the approval gate. */
    fun recoveryPlan(steps: List<String>): EngineeringTask {
        require(task.stage == EngineeringTaskStage.APPROVAL) { "Task is not waiting for a recovery plan." }
        require(steps.any { it.isNotBlank() }) { "Recovery plan must contain at least one step." }
        task = task.copy(plan = steps.filter { it.isNotBlank() }, error = null)
        return task
    }

    fun edited(result: String): EngineeringTask {
        require(task.stage == EngineeringTaskStage.EDIT) { "Editing is not the current workflow stage." }
        task = task.completeStep("Apply planned changes", result)
        return task
    }

    fun tested(result: EngineeringCommandResult): EngineeringTask {
        require(task.stage == EngineeringTaskStage.TEST) { "Testing is not the current workflow stage." }
        if (result.success) {
            task = task.completeStep("Run tests", result.message)
            return task
        }
        return recover("Tests failed: ${result.message}")
    }

    fun built(result: EngineeringCommandResult): EngineeringTask {
        require(task.stage == EngineeringTaskStage.BUILD) { "Building is not the current workflow stage." }
        if (result.success) {
            task = task.completeStep("Build project", result.message)
            return task
        }
        return recover("Build failed: ${result.message}")
    }

    fun verified(result: String): EngineeringTask {
        require(task.stage == EngineeringTaskStage.VERIFY) { "Verification is not the current workflow stage." }
        task = task.completeStep("Verify results", result.ifBlank { "Verification completed." })
        return task
    }

    fun cancel(): EngineeringTask {
        if (task.stage !in TERMINAL_STAGES) task = task.cancel()
        return task
    }

    private fun recover(reason: String): EngineeringTask {
        recoveryAttempts++
        if (recoveryAttempts > maxRecoveryAttempts) {
            task = task.fail("Engineering recovery limit reached after $maxRecoveryAttempts attempts. $reason")
            return task
        }
        task = task.copy(
            stage = EngineeringTaskStage.APPROVAL,
            plan = emptyList(),
            lastResult = reason,
            error = reason
        )
        return task
    }

    companion object {
        private val TERMINAL_STAGES = setOf(
            EngineeringTaskStage.COMPLETED,
            EngineeringTaskStage.FAILED,
            EngineeringTaskStage.CANCELLED
        )
    }
}
