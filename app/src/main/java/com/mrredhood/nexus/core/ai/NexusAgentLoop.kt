package com.mrredhood.nexus.core.ai

import com.mrredhood.nexus.core.workspace.Workspace

/**
 * Executes read-only Nexus actions automatically and returns their results so the
 * model can continue the same turn. Mutating actions are deliberately returned
 * to the UI for explicit approval.
 */
class NexusAgentLoop(
    private val executor: NexusActionExecutor,
    private val maxRounds: Int = 5
) {
    suspend fun collectReadOnlyResults(
        workspace: Workspace,
        proposals: List<NexusActionProposal>
    ): List<AgentToolResult> {
        val results = mutableListOf<AgentToolResult>()
        proposals
            .filter { !NexusActionPolicy.requiresApproval(it.action) }
            .forEach { proposal ->
                val result = executor.execute(workspace, proposal.action)
                results += AgentToolResult(proposal.action, result)
            }
        return results
    }

    fun canContinue(round: Int): Boolean = round < maxRounds
}

data class AgentToolResult(
    val action: NexusAction,
    val result: ActionExecutionResult
) {
    fun asPromptMessage(): String = buildString {
        append("[NEXUS TOOL RESULT]\n")
        append("Action: ").append(action.type).append('\n')
        action.path?.let { append("Path: ").append(it).append('\n') }
        append("Success: ").append(result.success).append('\n')
        append("Message: ").append(result.message).append('\n')
        result.output?.let {
            append("Output:\n")
            append(it.take(24_000))
        }
        append("\n[/NEXUS TOOL RESULT]")
    }
}
