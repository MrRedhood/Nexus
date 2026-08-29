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

    fun startInspection() = move(NexusAgentPhase.INSPECT, NexusAgentStatus.RUNNING)

    fun finishInspection() {
        requireRunning(NexusAgentPhase.INSPECT)
        move(if (requiresApproval) NexusAgentPhase.PLAN else NexusAgentPhase.PLAN, NexusAgentStatus.PENDING)
    }

    fun startPlanning() = move(NexusAgentPhase.PLAN, NexusAgentStatus.RUNNING)

    fun finishPlanning() {
        requireRunning(NexusAgentPhase.PLAN)
        move(if (requiresApproval) NexusAgentPhase.APPROVAL else NexusAgentPhase.EDIT, NexusAgentStatus.PENDING)
    }

    fun approve() {
        requirePhase(NexusAgentPhase.APPROVAL)
        require(status == NexusAgentStatus.PENDING)
        move(NexusAgentPhase.EDIT, NexusAgentStatus.PENDING)
    }

    fun reject() {
        requirePhase(NexusAgentPhase.APPROVAL)
        status = NexusAgentStatus.BLOCKED
    }

    fun startEdit() = move(NexusAgentPhase.EDIT, NexusAgentStatus.RUNNING)

    fun finishEdit() {
        requireRunning(NexusAgentPhase.EDIT)
        move(NexusAgentPhase.TEST, NexusAgentStatus.PENDING)
    }

    fun startTest() = move(NexusAgentPhase.TEST, NexusAgentStatus.RUNNING)

    fun finishTest(success: Boolean) = finishExecution(NexusAgentPhase.TEST, success, NexusAgentPhase.BUILD)

    fun startBuild() = move(NexusAgentPhase.BUILD, NexusAgentStatus.RUNNING)

    fun finishBuild(success: Boolean) = finishExecution(NexusAgentPhase.BUILD, success, NexusAgentPhase.VERIFY)

    fun startVerify() = move(NexusAgentPhase.VERIFY, NexusAgentStatus.RUNNING)

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

    private fun move(expected: NexusAgentPhase, nextStatus: NexusAgentStatus) {
        requirePhase(expected)
        phase = expected
        status = nextStatus
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
