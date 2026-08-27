package com.mrredhood.nexus.core.ai

import kotlinx.coroutines.CancellationException

/**
 * Orchestrates a complete engineering task without hiding any stage.
 * Concrete integrations (workspace inspection, tests and GitHub Actions builds)
 * are supplied by the caller so this core remains independent of UI.
 *
 * The stateful NexusEngineeringSession owns stage transitions and recovery gates;
 * this runner supplies the actual environment operations.
 */
class NexusEngineeringAgent(private val maxIterations: Int = 5) {
    suspend fun run(
        initial: EngineeringTask,
        permissionMode: String,
        environment: EngineeringTaskEnvironment,
        onTask: (EngineeringTask) -> Unit = {}
    ): EngineeringTask {
        val session = NexusEngineeringSession(initial.request, maxIterations)
        fun publish(): EngineeringTask {
            onTask(session.task)
            return session.task
        }

        return try {
            while (session.task.stage !in TERMINAL_STAGES) {
                when (session.task.stage) {
                    EngineeringTaskStage.INSPECT -> {
                        session.inspected(environment.inspect(session.task.request))
                    }
                    EngineeringTaskStage.PLAN -> {
                        val plan = environment.plan(session.task.request, session.task.completedSteps.lastOrNull())
                        session.planned(plan)
                    }
                    EngineeringTaskStage.APPROVAL -> {
                        if (!EngineeringTaskGate.canAdvance(session.task, permissionMode)) {
                            session.approve(permissionMode)
                        } else if (permissionMode.equals("some", true)) {
                            return publish()
                        } else {
                            session.approve(permissionMode)
                        }
                    }
                    EngineeringTaskStage.EDIT -> {
                        session.edited(environment.edit(session.task.plan))
                    }
                    EngineeringTaskStage.TEST -> {
                        val result = environment.test()
                        session.tested(result)
                        if (!result.success && session.task.stage == EngineeringTaskStage.APPROVAL) {
                            val diagnosis = environment.verify("Tests failed: ${result.message}")
                            session.recoveryPlan(environment.plan(session.task.request, diagnosis))
                            if (session.recoveryLimitReached()) return publish()
                        }
                    }
                    EngineeringTaskStage.BUILD -> {
                        val result = environment.build()
                        session.built(result)
                        if (!result.success && session.task.stage == EngineeringTaskStage.APPROVAL) {
                            val diagnosis = environment.verify("Build failed: ${result.message}")
                            session.recoveryPlan(environment.plan(session.task.request, diagnosis))
                            if (session.recoveryLimitReached()) return publish()
                        }
                    }
                    EngineeringTaskStage.VERIFY -> {
                        session.verified(environment.verify(session.task.lastResult.orEmpty()))
                    }
                    else -> Unit
                }
                publish()
            }
            session.task
        } catch (cancelled: CancellationException) {
            session.cancel()
            publish()
        } catch (error: Throwable) {
            val failed = session.task.fail(error.message ?: "Engineering task failed.")
            onTask(failed)
            failed
        }
    }

    companion object {
        private val TERMINAL_STAGES = setOf(
            EngineeringTaskStage.COMPLETED,
            EngineeringTaskStage.FAILED,
            EngineeringTaskStage.CANCELLED
        )
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
