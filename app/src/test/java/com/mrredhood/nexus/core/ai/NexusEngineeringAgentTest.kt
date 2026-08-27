package com.mrredhood.nexus.core.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusEngineeringAgentTest {
    @Test
    fun autonomousTask_advancesThroughFullEngineeringLoop() = runBlocking {
        val calls = mutableListOf<String>()
        val environment = EngineeringTaskEnvironment(
            inspect = { calls += "inspect"; "workspace inspected" },
            plan = { _, _ -> calls += "plan"; listOf("edit source", "add regression test") },
            edit = { steps -> calls += "edit:${steps.size}"; "changes applied" },
            test = { calls += "test"; EngineeringCommandResult(true, "tests passed") },
            build = { calls += "build"; EngineeringCommandResult(true, "build passed") },
            verify = { calls += "verify"; "verification passed" }
        )

        val result = NexusEngineeringAgent().run(
            initial = EngineeringTask(request = "Fix the bug"),
            permissionMode = "autonomous",
            environment = environment
        )

        assertEquals(EngineeringTaskStage.COMPLETED, result.stage)
        assertEquals(listOf("inspect", "plan", "edit:2", "test", "build", "verify"), calls)
        assertEquals(6, result.completedSteps.size)
    }

    @Test
    fun somePermission_stopsAtApprovalGate() = runBlocking {
        val environment = EngineeringTaskEnvironment(
            inspect = { "workspace inspected" },
            plan = { _, _ -> listOf("edit source") },
            edit = { "must not execute" },
            test = { EngineeringCommandResult(true, "tests passed") },
            build = { EngineeringCommandResult(true, "build passed") },
            verify = { "verification passed" }
        )

        val result = NexusEngineeringAgent().run(
            initial = EngineeringTask(request = "Fix the bug"),
            permissionMode = "some",
            environment = environment
        )

        assertEquals(EngineeringTaskStage.APPROVAL, result.stage)
        assertTrue(result.plan.isNotEmpty())
    }

    @Test
    fun failedTest_returnsToApprovalWithRecoveryPlan() = runBlocking {
        var testRuns = 0
        val environment = EngineeringTaskEnvironment(
            inspect = { "workspace inspected" },
            plan = { _, reason ->
                if (reason?.contains("Tests failed") == true) listOf("fix failing test") else listOf("edit source")
            },
            edit = { "changes applied" },
            test = { testRuns++; EngineeringCommandResult(false, "unit test failed") },
            build = { EngineeringCommandResult(true, "build passed") },
            verify = { reason -> "diagnosed: $reason" }
        )

        val result = NexusEngineeringAgent(maxIterations = 1).run(
            initial = EngineeringTask(request = "Fix the bug"),
            permissionMode = "autonomous",
            environment = environment
        )

        assertEquals(EngineeringTaskStage.APPROVAL, result.stage)
        assertEquals(listOf("fix failing test"), result.plan)
        assertEquals(1, testRuns)
    }

    @Test
    fun permissionGate_blocksNeverModeBeforeEdit() {
        val task = EngineeringTask(request = "Change code", stage = EngineeringTaskStage.EDIT)
        assertTrue(!EngineeringTaskGate.canAdvance(task, "never"))
        assertTrue(EngineeringTaskGate.canAdvance(task, "some"))
        assertTrue(EngineeringTaskGate.canAdvance(task, "autonomous"))
    }
}
