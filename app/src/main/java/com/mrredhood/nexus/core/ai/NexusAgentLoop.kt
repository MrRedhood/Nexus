package com.mrredhood.nexus.core.ai

import com.mrredhood.nexus.core.settings.NexusSettingsRuntime
import com.mrredhood.nexus.core.workspace.Workspace

/** Executes Nexus actions and feeds real execution results back to the model. */
class NexusAgentLoop(
    private val executor: NexusActionExecutor,
    private val maxRounds: Int = 5
) {
    suspend fun collectToolResults(
        workspace: Workspace,
        proposals: List<NexusActionProposal>,
        permissionMode: String
    ): List<AgentToolResult> {
        val results = mutableListOf<AgentToolResult>()
        proposals.forEach { proposal ->
            val action = proposal.action
            if (NexusActionPolicy.canAutoExecute(action, permissionMode)) {
                val result = executor.execute(workspace, action)
                results += AgentToolResult(action, result)
            } else if (NexusActionPolicy.isMutating(action)) {
                val mode = permissionMode.lowercase()
                val message = if (mode == "never") {
                    "Workspace change blocked: AI permission is set to Never."
                } else {
                    "Workspace change requires approval because AI permission is set to Some."
                }
                results += AgentToolResult(action, ActionExecutionResult(false, message))
            }
        }
        return results
    }

    /** Compatibility entry point used by existing chat flows. */
    suspend fun collectReadOnlyResults(
        workspace: Workspace,
        proposals: List<NexusActionProposal>
    ): List<AgentToolResult> = collectToolResults(
        workspace,
        proposals,
        NexusSettingsRuntime.current().workspacePermission
    )

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
        append("Success: ").append(result.success).append('\n")
        append("Message: ").append(result.message).append('\n')
        result.output?.let {
            append("Output:\n")
            append(it.take(24_000))
        }
        append("\n[/NEXUS TOOL RESULT]")
    }
}
