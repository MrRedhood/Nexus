package com.mrredhood.nexus.core.ai

/**
 * Deterministic lifecycle for engineering tasks. Execution remains owned by the existing
 * action, terminal and GitHub Actions services; this object only controls workflow state.
 */
class NexusAgentWorkflow(
    val requiresApproval: Boolean
) {
    var phase: NexusAgentPhase = NexusAgentPhase.INSPECT
        private set

    var status: NexusAgentStatus = NexusAgentStatus.PENDING
        private set

    val completed: Boolean
        get() = phase == NexusAgentPhase.COMPLETE && status == NexusAgentStatus.SUCCEEDED

    fun startInspection() = start(NexusAgentPhase.INSPECT)

    fun finishInspection() {
        requireRunning(NexusAgentPhase.INSPECT)
        phase = NexusAgentPhase.PLAN
        status = NexusAgentStatus.PENDING
    }

    fun startPlanning() = start(NexusAgentPhase.PLAN)

    fun finishPlanning() {
        requireRunning(NexusAgentPhase.PLAN)
        phase = if (requiresApproval) NexusAgentPhase.APPROVAL else NexusAgentPhase.EDIT
        status = NexusAgentStatus.PENDING
    }

    fun approve() {
        requirePhase(NexusAgentPhase.APPROVAL)
        require(status == NexusAgentStatus.PENDING)
        phase = NexusAgentPhase.EDIT
        status = NexusAgentStatus.PENDING
    }

    fun reject() {
        requirePhase(NexusAgentPhase.APPROVAL)
        status = NexusAgentStatus.BLOCKED
    }

    fun startEdit() = start(NexusAgentPhase.EDIT)

    fun finishEdit() {
        requireRunning(NexusAgentPhase.EDIT)
        phase = NexusAgentPhase.TEST
        status = NexusAgentStatus.PENDING
    }

    fun startTest() = start(NexusAgentPhase.TEST)

    fun finishTest(success: Boolean) = finishExecution(NexusAgentPhase.TEST, success, NexusAgentPhase.BUILD)

    fun startBuild() = start(NexusAgentPhase.BUILD)

    fun finishBuild(success: Boolean) = finishExecution(NexusAgentPhase.BUILD, success, NexusAgentPhase.VERIFY)

    fun startVerify() = start(NexusAgentPhase.VERIFY)

    fun finishVerify(success: Boolean) {
        requireRunning(NexusAgentPhase.VERIFY)
        if (success) {
            phase = NexusAgentPhase.COMPLETE
            status = NexusAgentStatus.SUCCEEDED
        } else {
            status = NexusAgentStatus.FAILED
        }
    }

    fun fail() {
        require(status != NexusAgentStatus.SUCCEEDED && status != NexusAgentStatus.BLOCKED)
        status = NexusAgentStatus.FAILED
    }

    private fun finishExecution(current: NexusAgentPhase, success: Boolean, next: NexusAgentPhase) {
        requireRunning(current)
        if (success) {
            phase = next
            status = NexusAgentStatus.PENDING
        } else {
            status = NexusAgentStatus.FAILED
        }
    }

    private fun start(expected: NexusAgentPhase) {
        requirePhase(expected)
        require(status == NexusAgentStatus.PENDING) {
            "${expected.label} must be pending before it can start."
        }
        status = NexusAgentStatus.RUNNING
    }

    private fun requireRunning(expected: NexusAgentPhase) {
        requirePhase(expected)
        require(status == NexusAgentStatus.RUNNING) { "${expected.label} must be running before it can finish." }
    }

    private fun requirePhase(expected: NexusAgentPhase) {
        require(phase == expected) { "Expected ${expected.label}, but workflow is at ${phase.label}." }
    }
}

enum class NexusAgentPhase(val label: String) {
    INSPECT("Inspect"),
    PLAN("Plan"),
    APPROVAL("Approval"),
    EDIT("Edit"),
    TEST("Test"),
    BUILD("Build"),
    VERIFY("Verify"),
    COMPLETE("Complete")
}

enum class NexusAgentStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    BLOCKED
}
