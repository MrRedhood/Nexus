package com.mrredhood.nexus.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusEngineeringWorkflowRunnerTest {
    @Test
    fun workflowLifecycle_reachesCompleteAfterSuccessfulStages() {
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

        assertTrue(workflow.completed)
        assertEquals(NexusAgentPhase.COMPLETE, workflow.phase)
    }

    @Test
    fun workflowLifecycle_requiresApprovalBeforeEdit() {
        val workflow = NexusAgentWorkflow(requiresApproval = true)
        workflow.startInspection()
        workflow.finishInspection()
        workflow.startPlanning()
        workflow.finishPlanning()

        assertEquals(NexusAgentPhase.APPROVAL, workflow.phase)
        workflow.approve()
        workflow.startEdit()
        assertEquals(NexusAgentStatus.RUNNING, workflow.status)
    }
}
