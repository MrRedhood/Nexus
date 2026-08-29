package com.mrredhood.nexus.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusAgentWorkflowTest {
    @Test
    fun autonomousWorkflowReachesCompleteAfterVerification() {
        val workflow = NexusAgentWorkflow(requiresApproval = false)

        workflow.startInspection()
        workflow.finishInspection()
        workflow.startPlanning()
        workflow.finishPlanning()
        workflow.startEdit()
        workflow.finishEdit()
        workflow.startTest()
        workflow.finishTest(true)
        workflow.startBuild()
        workflow.finishBuild(true)
        workflow.startVerify()
        workflow.finishVerify(true)

        assertEquals(NexusAgentPhase.COMPLETE, workflow.phase)
        assertEquals(NexusAgentStatus.SUCCEEDED, workflow.status)
        assertTrue(workflow.completed)
    }

    @Test
    fun approvalWorkflowStopsBeforeEditUntilApproved() {
        val workflow = NexusAgentWorkflow(requiresApproval = true)

        workflow.startInspection()
        workflow.finishInspection()
        workflow.startPlanning()
        workflow.finishPlanning()

        assertEquals(NexusAgentPhase.APPROVAL, workflow.phase)
        assertEquals(NexusAgentStatus.PENDING, workflow.status)

        workflow.approve()
        assertEquals(NexusAgentPhase.EDIT, workflow.phase)
    }

    @Test
    fun failedTestStopsWorkflowBeforeBuild() {
        val workflow = NexusAgentWorkflow(requiresApproval = false)

        workflow.startInspection()
        workflow.finishInspection()
        workflow.startPlanning()
        workflow.finishPlanning()
        workflow.startEdit()
        workflow.finishEdit()
        workflow.startTest()
        workflow.finishTest(false)

        assertEquals(NexusAgentPhase.TEST, workflow.phase)
        assertEquals(NexusAgentStatus.FAILED, workflow.status)
        assertTrue(!workflow.completed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotBuildBeforeTestsPass() {
        val workflow = NexusAgentWorkflow(requiresApproval = false)
        workflow.startInspection()
        workflow.finishInspection()
        workflow.startPlanning()
        workflow.finishPlanning()
        workflow.startEdit()
        workflow.finishEdit()
        workflow.startBuild()
    }
}
