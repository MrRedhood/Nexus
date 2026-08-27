package com.mrredhood.nexus.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusEngineeringSessionTest {
    @Test
    fun liveWorkflow_advancesThroughAllStages() {
        val session = NexusEngineeringSession("Fix the failing feature")

        assertEquals(EngineeringTaskStage.INSPECT, session.task.stage)
        session.inspected("workspace inspected")
        assertEquals(EngineeringTaskStage.PLAN, session.task.stage)
        session.planned(listOf("edit source", "add regression test"))
        assertEquals(EngineeringTaskStage.APPROVAL, session.task.stage)
        session.approve("autonomous")
        assertEquals(EngineeringTaskStage.EDIT, session.task.stage)
        session.edited("changes applied")
        assertEquals(EngineeringTaskStage.TEST, session.task.stage)
        session.tested(EngineeringCommandResult(true, "tests passed"))
        assertEquals(EngineeringTaskStage.BUILD, session.task.stage)
        session.built(EngineeringCommandResult(true, "build passed"))
        assertEquals(EngineeringTaskStage.VERIFY, session.task.stage)
        session.verified("CI verification passed")

        assertEquals(EngineeringTaskStage.COMPLETED, session.task.stage)
        assertEquals(
            listOf("Inspect workspace", "Create engineering plan", "Apply planned changes", "Run tests", "Build project", "Verify results"),
            session.task.completedSteps
        )
    }

    @Test
    fun somePermission_stopsAtApprovalWithoutApplyingChanges() {
        val session = NexusEngineeringSession("Fix the failing feature")
        session.inspected("workspace inspected")
        session.planned(listOf("edit source"))

        assertEquals(EngineeringTaskStage.APPROVAL, session.task.stage)
        assertTrue(EngineeringTaskGate.canAdvance(session.task, "some"))
    }

    @Test
    fun failedTest_returnsToApprovalForRecovery() {
        val session = NexusEngineeringSession("Fix the failing feature", maxRecoveryAttempts = 2)
        session.inspected("workspace inspected")
        session.planned(listOf("edit source"))
        session.approve("autonomous")
        session.edited("changes applied")

        val result = session.tested(EngineeringCommandResult(false, "unit test failed"))

        assertEquals(EngineeringTaskStage.APPROVAL, result.stage)
        assertEquals("Tests failed: unit test failed", result.lastResult)
        assertTrue(result.plan.isEmpty())
    }

    @Test
    fun recoveryLimit_failsInsteadOfRetryingForever() {
        val session = NexusEngineeringSession("Fix the failing feature", maxRecoveryAttempts = 1)
        session.inspected("workspace inspected")
        session.planned(listOf("edit source"))
        session.approve("autonomous")
        session.edited("changes applied")
        session.tested(EngineeringCommandResult(false, "first failure"))
        session.planned(listOf("retry fix"))
        session.approve("autonomous")
        session.edited("retry applied")

        val result = session.tested(EngineeringCommandResult(false, "second failure"))

        assertEquals(EngineeringTaskStage.FAILED, result.stage)
        assertTrue(result.error?.contains("recovery limit") == true)
    }
}
