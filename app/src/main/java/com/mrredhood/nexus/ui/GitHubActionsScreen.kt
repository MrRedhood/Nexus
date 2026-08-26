package com.mrredhood.nexus.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.settings.ApiKeyStore
import com.mrredhood.nexus.core.workspace.GitHubArtifact
import com.mrredhood.nexus.core.workspace.GitHubRepositoryService
import com.mrredhood.nexus.core.workspace.GitHubWorkflow
import com.mrredhood.nexus.core.workspace.GitHubWorkflowJob
import com.mrredhood.nexus.core.workspace.GitHubWorkflowRun
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubActionsScreen(project: NexusProject, onBack: () -> Unit) {
    val context = LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val service = remember { GitHubRepositoryService(WorkspaceFileSystem(context)) }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty()
    var workflows by remember { mutableStateOf<List<GitHubWorkflow>>(emptyList()) }
    var runs by remember { mutableStateOf<List<GitHubWorkflowRun>>(emptyList()) }
    var selectedRun by remember { mutableStateOf<GitHubWorkflowRun?>(null) }
    var jobs by remember { mutableStateOf<List<GitHubWorkflowJob>>(emptyList()) }
    var artifacts by remember { mutableStateOf<List<GitHubArtifact>>(emptyList()) }
    var logs by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun token() = tokenStore.get("github")
    fun refresh() {
        val accessToken = token() ?: run { error = "Add a GitHub token in Settings > GitHub first."; return }
        if (repository.isBlank()) { error = "Connect a GitHub repository to this project first."; return }
        scope.launch { loading = true; error = null; runCatching { workflows = service.workflows(repository, accessToken); runs = service.workflowRuns(repository, accessToken); artifacts = service.artifacts(repository, accessToken) }.onFailure { error = it.message }; loading = false }
    }
    fun openRun(run: GitHubWorkflowRun) {
        val accessToken = token() ?: return
        scope.launch { loading = true; error = null; runCatching { selectedRun = service.workflowRun(repository, run.id, accessToken); jobs = service.workflowJobs(repository, run.id, accessToken); artifacts = service.artifacts(repository, accessToken, run.id) }.onFailure { error = it.message }; loading = false }
    }
    fun artifactKind(name: String): String = when {
        name.endsWith(".apk", ignoreCase = true) || name.contains("apk", ignoreCase = true) -> "APK"
        name.endsWith(".aab", ignoreCase = true) || name.contains("aab", ignoreCase = true) -> "AAB"
        else -> "Artifact"
    }
    fun failedJobSummary(job: GitHubWorkflowJob): String = when (job.conclusion) {
        "failure" -> "Failed job — inspect its real GitHub log for the exact error."
        "cancelled" -> "Job was cancelled by GitHub or a user."
        "timed_out" -> "Job exceeded GitHub's execution time limit."
        else -> "No failure conclusion reported by GitHub."
    }

    LaunchedEffect(repository) { if (repository.isNotBlank()) refresh() }
    BackHandler { if (logs != null) logs = null else if (selectedRun != null) { selectedRun = null; jobs = emptyList() } else onBack() }

    Scaffold(topBar = { TopAppBar(title = { Text("Actions") }, navigationIcon = { TextButton(onClick = onBack) { Text("‹") } }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("Builds", style = MaterialTheme.typography.titleLarge); Text(repository) }; OutlinedButton(onClick = ::refresh, enabled = !loading) { Text("Refresh") } } }
            if (loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            runs.firstOrNull()?.let { latest ->
                item { Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(14.dp)) { Text("Latest build", style = MaterialTheme.typography.titleMedium); Text("${latest.name} · ${latest.status} · ${latest.conclusion ?: "running"}"); Text("${latest.branch} · ${latest.sha.take(7)}"); Text("Run #${latest.id} · ${duration(latest.createdAt, latest.updatedAt)}") } } }
            }
            item { Text("Workflows", style = MaterialTheme.typography.titleMedium) }
            items(workflows, key = { it.id }) { workflow -> Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(14.dp)) { Text(workflow.name, style = MaterialTheme.typography.titleMedium); Text(workflow.path); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !loading, onClick = { val accessToken = token() ?: return@Button; scope.launch { loading = true; runCatching { service.dispatchWorkflow(repository, workflow.id, accessToken, project.branch.ifBlank { "main" }) }.onFailure { error = it.message }; runs = runCatching { service.workflowRuns(repository, accessToken) }.getOrDefault(runs); loading = false } }) { Text("Build") }
                TextButton(onClick = { scope.launch { val accessToken = token() ?: return@launch; runCatching { service.workflowRuns(repository, accessToken, workflow.id) }.onSuccess { runs = it }.onFailure { error = it.message } } }) { Text("Runs") }
            } } } }
            item { Text("Recent runs", style = MaterialTheme.typography.titleMedium) }
            items(runs, key = { it.id }) { run -> Card(onClick = { openRun(run) }, modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(14.dp)) { Text(run.name, style = MaterialTheme.typography.titleMedium); Text("${run.status} · ${run.conclusion ?: "running"}"); Text("${run.branch} · ${run.sha.take(7)} · ${duration(run.createdAt, run.updatedAt)}"); Text("${run.createdAt} · Run #${run.id}") } } }
        }
    }

    selectedRun?.let { run ->
        AlertDialog(onDismissRequest = { selectedRun = null }, title = { Text(run.name) }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Status: ${run.status} · ${run.conclusion ?: "running"}") }; item { Text("Branch: ${run.branch}") }; item { Text("Commit: ${run.sha}") }; item { Text("Duration: ${duration(run.createdAt, run.updatedAt)}") }
            item { Text("Jobs", style = MaterialTheme.typography.titleMedium) }
            items(jobs, key = { it.id }) { job -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(modifier = Modifier.weight(1f)) { Text(job.name); Text("${job.status} · ${job.conclusion ?: "running"}"); if (job.conclusion != null && job.conclusion != "success") Text(failedJobSummary(job), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }; TextButton(onClick = { val accessToken = token() ?: return@TextButton; scope.launch { loading = true; runCatching { logs = service.workflowJobLogs(repository, job.id, accessToken) }.onFailure { error = it.message }; loading = false } }) { Text("Logs") } } }
            item { Text("Artifacts", style = MaterialTheme.typography.titleMedium) }
            if (artifacts.isEmpty()) item { Text("No artifacts reported for this run.") }
            items(artifacts, key = { it.id }) { artifact -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(modifier = Modifier.weight(1f)) { Text("${artifactKind(artifact.name)} · ${artifact.name}"); Text("${formatBytes(artifact.sizeBytes)} · ${if (artifact.expired) "expired" else "available"}"); Text("Created ${artifact.createdAt}") }; TextButton(enabled = !artifact.expired && !artifact.downloadUrl.isNullOrBlank(), onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(artifact.downloadUrl))) }) { Text("Open") } } }
            if (run.conclusion == "failure") item { Text("Failure diagnostics", style = MaterialTheme.typography.titleMedium) }
            if (run.conclusion == "failure") items(jobs.filter { it.conclusion != null && it.conclusion != "success" }, key = { "failure-${it.id}" }) { job -> Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(10.dp)) { Text(job.name); Text(failedJobSummary(job)); TextButton(onClick = { val accessToken = token() ?: return@TextButton; scope.launch { loading = true; runCatching { logs = service.workflowJobLogs(repository, job.id, accessToken) }.onFailure { error = it.message }; loading = false } }) { Text("Inspect real log") } } } }
        } }, confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (run.status == "in_progress" || run.status == "queued") TextButton(enabled = !loading, onClick = { val accessToken = token() ?: return@TextButton; scope.launch { loading = true; runCatching { service.cancelWorkflowRun(repository, run.id, accessToken) }.onSuccess { selectedRun = service.workflowRun(repository, run.id, accessToken) }.onFailure { error = it.message }; loading = false } }) { Text("Cancel") }
            if (run.conclusion == "failure") TextButton(enabled = !loading, onClick = { val accessToken = token() ?: return@TextButton; scope.launch { loading = true; runCatching { service.rerunFailedJobs(repository, run.id, accessToken) }.onFailure { error = it.message }; loading = false } }) { Text("Rerun failed") }
            TextButton(onClick = { selectedRun = null }) { Text("Done") }
        } })
    }
    logs?.let { content -> AlertDialog(onDismissRequest = { logs = null }, title = { Text("GitHub job logs") }, text = { LazyColumn { item { Text(content, style = MaterialTheme.typography.bodySmall) } } }, confirmButton = { TextButton(onClick = { logs = null }) { Text("Close") } }) }
}

private fun duration(start: String, end: String): String = runCatching { val seconds = Duration.between(Instant.parse(start), Instant.parse(end)).seconds.coerceAtLeast(0); "${seconds / 60}m ${seconds % 60}s" }.getOrDefault("—")
private fun formatBytes(bytes: Long): String = when { bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0)); bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0); else -> "$bytes B" }
