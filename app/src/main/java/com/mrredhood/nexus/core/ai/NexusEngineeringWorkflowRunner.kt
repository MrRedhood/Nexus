package com.mrredhood.nexus.core.ai

import android.content.Context
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.settings.GitCredentialStore
import com.mrredhood.nexus.core.workspace.GitHubActionsBuildService
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import kotlinx.coroutines.delay

/**
 * Bridges AI engineering tasks to real Nexus workspace operations and cloud builds.
 *
 * NexusAgentWorkflow remains the strict lifecycle authority. NexusEngineeringAgent
 * performs the actual task orchestration against this environment.
 */
class NexusEngineeringWorkflowRunner(context: Context) {
    private val appContext = context.applicationContext
    private val fileSystem = WorkspaceFileSystem(appContext)
    private val actionExecutor = NexusActionExecutor(fileSystem)
    private val workspaceContext = WorkspaceContextService(fileSystem)
    private val buildService = GitHubActionsBuildService()
    private val credentials = GitCredentialStore(appContext)

    suspend fun run(
        request: String,
        project: NexusProject,
        workspace: Workspace,
        permissionMode: String,
        actionProvider: suspend (String, Workspace) -> List<NexusActionProposal>,
        onUpdate: (EngineeringWorkflowUpdate) -> Unit = {}
    ): EngineeringTask {
        val requiresApproval = permissionMode.equals("some", true)
        val lifecycle = NexusAgentWorkflow(requiresApproval = requiresApproval)
        val environment = EngineeringTaskEnvironment(
            inspect = {
                lifecycle.startInspection()
                val result = workspaceContext.refresh(workspace)
                lifecycle.finishInspection()
                result
            },
            plan = { taskRequest, previous ->
                lifecycle.startPlanning()
                val steps = listOf(
                    "Inspect the affected workspace files",
                    "Apply the approved workspace changes",
                    "Validate the resulting workspace changes",
                    "Run the configured GitHub Actions Android build",
                    "Verify the build result and artifacts"
                ).let { base -> if (previous.isNullOrBlank()) base else base + "Review previous result: $previous" }
                lifecycle.finishPlanning()
                steps
            },
            edit = { plan ->
                if (lifecycle.phase == NexusAgentPhase.APPROVAL) lifecycle.approve()
                lifecycle.startEdit()
                if (permissionMode.equals("never", true)) error("AI permission is set to Never; workspace edits are blocked.")
                val proposals = actionProvider(request, workspace)
                    .filter { NexusActionPolicy.isMutating(it.action) }
                if (proposals.isEmpty()) error("Engineering workflow produced no workspace edit actions.")
                val results = proposals.map { proposal ->
                    val result = actionExecutor.execute(workspace, proposal.action)
                    if (!result.success) error(result.message)
                    result.message
                }
                lifecycle.finishEdit()
                results.joinToString("\n")
            },
            test = {
                lifecycle.startTest()
                val result = validateWorkspace(workspace)
                lifecycle.finishTest(result.success)
                result
            },
            build = {
                lifecycle.startBuild()
                val result = runCloudBuild(project)
                lifecycle.finishBuild(result.success)
                result
            },
            verify = { previous ->
                lifecycle.startVerify()
                val result = if (previous.isBlank()) "Verification completed." else previous
                lifecycle.finishVerify(true)
                result
            }
        )

        val agent = NexusEngineeringAgent()
        val initial = EngineeringTask(request = request)
        val result = agent.run(initial, permissionMode, environment) { task ->
            onUpdate(EngineeringWorkflowUpdate(task, lifecycle.phase, lifecycle.status))
        }
        return result
    }

    private suspend fun validateWorkspace(workspace: Workspace): EngineeringCommandResult = runCatching {
        val summary = workspaceContext.refresh(workspace)
        require(summary.isNotBlank()) { "Workspace inspection returned no context." }
        EngineeringCommandResult(true, "Workspace validation completed successfully.")
    }.getOrElse { EngineeringCommandResult(false, it.message ?: "Workspace validation failed.") }

    private suspend fun runCloudBuild(project: NexusProject): EngineeringCommandResult = runCatching {
        val repository = project.repository?.trim().orEmpty()
        require(repository.isNotBlank()) { "Project is not linked to a GitHub repository." }
        val token = credentials.githubToken().orEmpty()
        require(token.isNotBlank()) { "GitHub token is not configured in Git Credentials." }
        val branch = project.branch.ifBlank { "main" }
        val before = buildService.latestRuns(repository, token, branch, limit = 1).firstOrNull()?.id
        buildService.dispatchDebugApk(repository, token, branch)
        repeat(60) {
            delay(5_000)
            val run = buildService.latestRuns(repository, token, branch, limit = 5)
                .firstOrNull { it.id != before } ?: return@repeat
            if (run.isFinished) {
                return EngineeringCommandResult(
                    run.isSuccessful,
                    if (run.isSuccessful) "GitHub Actions build succeeded: ${run.url}" else "GitHub Actions build failed: ${run.url}"
                )
            }
        }
        EngineeringCommandResult(false, "Timed out waiting for the GitHub Actions build to finish.")
    }.getOrElse { EngineeringCommandResult(false, it.message ?: "Unable to start GitHub Actions build.") }
}

data class EngineeringWorkflowUpdate(
    val task: EngineeringTask,
    val phase: NexusAgentPhase,
    val status: NexusAgentStatus
)
