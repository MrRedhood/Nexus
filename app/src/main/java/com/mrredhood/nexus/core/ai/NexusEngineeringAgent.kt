package com.mrredhood.nexus.core.ai

import kotlinx.coroutines.CancellationException

/**
 * Orchestrates a complete engineering task without hiding any stage.
 * Concrete integrations (workspace inspection, tests and GitHub Actions builds)
 * are supplied by the caller so this core remains independent of UI.
 *
 * maxIterations limits recovery cycles, not individual workflow stages. A normal
 * inspect -> plan -> approve -> edit -> test -> build -> verify run must always
 * be able to traverse the complete loop.
 */
class NexusEngineeringAgent(private val maxIterations: Int = 5) {
    suspend fun run(
        initial: EngineeringTask,
        permissionMode: String,
        environment: EngineeringTaskEnvironment,
        onTask: (EngineeringTask) -> Unit = {}
    ): EngineeringTask {
        var task = initial
        var recoveryAttempts = 0
        fun publish(next: EngineeringTask): EngineeringTask {
            task = next
            onTask(task)
            return task
        }

        return try {
            while (task.stage !in setOf(EngineeringTaskStage.COMPLETED, EngineeringTaskStage.FAILED, EngineeringTaskStage.CANCELLED)) {
                task = when (task.stage) {
                    EngineeringTaskStage.INSPECT -> {
                        val result = environment.inspect(task.request)
                        task.completeStep("Inspect workspace", result)
                    }
                    EngineeringTaskStage.PLAN -> {
                        val plan = environment.plan(task.request, task.completedSteps.lastOrNull())
                        task.withPlan(plan)
                    }
                    EngineeringTaskStage.APPROVAL -> {
                        if (!EngineeringTaskGate.canAdvance(task, permissionMode)) {
                            return publish(task.fail("Engineering task requires approval, but AI permission is set to Never."))
                        }
                        if (permissionMode.equals("some", true)) {
                            return publish(task)
                        }
                        task.approve()
                    }
                    EngineeringTaskStage.EDIT -> {
                        if (!EngineeringTaskGate.canAdvance(task, permissionMode)) {
                            return publish(task.fail("Editing is blocked by the current AI permission mode."))
                        }
                        val result = environment.edit(task.plan)
                        task.completeStep("Apply planned changes", result)
                    }
                    EngineeringTaskStage.TEST -> {
                        val result = environment.test()
                        if (!result.success) {
                            recoveryAttempts++
                            val diagnosis = environment.verify("Tests failed: ${result.message}")
                            val nextPlan = environment.plan(task.request, diagnosis)
                            if (recoveryAttempts > maxIterations) {
                                task.copy(
                                    stage = EngineeringTaskStage.APPROVAL,
                                    plan = nextPlan,
                                    lastResult = result.message,
                                    error = null
                                )
                            } else {
                                task.copy(
                                    stage = EngineeringTaskStage.APPROVAL,
                                    plan = nextPlan,
                                    lastResult = result.message,
                                    error = null
                                )
                            }
                        } else {
                            task.completeStep("Run tests", result.message)
                        }
                    }
                    EngineeringTaskStage.BUILD -> {
                        val result = environment.build()
                        if (!result.success) {
                            recoveryAttempts++
                            val diagnosis = environment.verify("Build failed: ${result.message}")
                            val nextPlan = environment.plan(task.request, diagnosis)
                            task.copy(
                                stage = EngineeringTaskStage.APPROVAL,
                                plan = nextPlan,
                                lastResult = result.message,
                                error = null
                            )
                        } else {
                            task.completeStep("Build project", result.message)
                        }
                    }
                    EngineeringTaskStage.VERIFY -> {
                        val result = environment.verify(task.lastResult.orEmpty())
                        if (result.isBlank()) task.completeStep("Verify results", "Verification completed.")
                        else task.completeStep("Verify results", result)
                    }
                    else -> task
                }
                publish(task)
            }
            task
        } catch (cancelled: CancellationException) {
            publish(task.cancel())
        } catch (error: Throwable) {
            publish(task.fail(error.message ?: "Engineering task failed."))
        }
    }
}

data class EngineeringCommandResult(
    val success: Boolean,
    val message: String
)

data class EngineeringTaskEnvironment(
    val inspect: suspend (String) -> String,
    val plan: suspend (String, String?) -> List<String>,
    val edit: suspend (List<String>) -> String,
    val test: suspend () -> EngineeringCommandResult,
    val build: suspend () -> EngineeringCommandResult,
    val verify: suspend (String) -> String
)
