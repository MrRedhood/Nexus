package com.mrredhood.nexus.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.mrredhood.nexus.core.workspace.GitHubCheckAnnotation
import com.mrredhood.nexus.core.workspace.GitHubCheckRun
import com.mrredhood.nexus.core.workspace.GitHubChecksService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubChecksScreen(project: NexusProject, onBack: () -> Unit) {
    val context = LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val service = remember { GitHubChecksService() }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty()

    var ref by remember { mutableStateOf(project.branch.ifBlank { "main" }) }
    var runs by remember { mutableStateOf<List<GitHubCheckRun>>(emptyList()) }
    var selected by remember { mutableStateOf<GitHubCheckRun?>(null) }
    var annotations by remember { mutableStateOf<List<GitHubCheckAnnotation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var annotationLoading by remember { mutableStateOf(false) }

    fun load() {
        val token = tokenStore.get("github")
        if (token.isNullOrBlank()) { error = "Add a GitHub token in Settings > GitHub first."; return }
        if (repository.isBlank()) { error = "Connect a GitHub repository to this project first."; return }
        if (ref.isBlank()) { error = "Enter a branch or commit SHA."; return }
        scope.launch {
            loading = true
            error = null
            runCatching { service.checkRuns(repository, ref.trim(), token) }
                .onSuccess { runs = it }
                .onFailure { error = it.message ?: "Could not load GitHub checks." }
            loading = false
        }
    }

    fun openCheck(run: GitHubCheckRun) {
        selected = run
        annotations = emptyList()
        val token = tokenStore.get("github") ?: return
        scope.launch {
            annotationLoading = true
            runCatching { service.annotations(repository, run.id, token) }
                .onSuccess { annotations = it }
                .onFailure { error = it.message ?: "Could not load check annotations." }
            annotationLoading = false
        }
    }

    LaunchedEffect(repository, ref) { if (repository.isNotBlank() && ref.isNotBlank()) load() }
    BackHandler(onBack = onBack)

    val passed = runs.count { it.conclusion == "success" }
    val failed = runs.count { it.conclusion in setOf("failure", "timed_out", "cancelled", "startup_failure", "action_required") }
    val pending = runs.count { it.status != "completed" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checks") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                actions = { IconButton(onClick = ::load, enabled = !loading) { Icon(Icons.Outlined.Refresh, "Refresh") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("What do you want to inspect?", style = MaterialTheme.typography.titleLarge)
                        Text("Nexus reads the repository's real GitHub Check Runs using your connected account.", style = MaterialTheme.typography.bodyMedium)
                        Text(repository.ifBlank { "No repository connected" }, style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(ref, { ref = it }, label = { Text("Branch or commit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        FilledTonalButton(onClick = ::load, enabled = !loading && repository.isNotBlank() && ref.isNotBlank()) { Text("Inspect checks") }
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (runs.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryCard("Passed", passed.toString(), Icons.Outlined.CheckCircle, Modifier.weight(1f))
                        SummaryCard("Failed", failed.toString(), Icons.Outlined.ErrorOutline, Modifier.weight(1f))
                        SummaryCard("Running", pending.toString(), Icons.Outlined.Schedule, Modifier.weight(1f))
                    }
                }
            }
            item { Text("Check runs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
            if (runs.isEmpty() && !loading && error == null) item {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("No check runs", style = MaterialTheme.typography.titleMedium); Text("GitHub returned no checks for $ref.", style = MaterialTheme.typography.bodySmall) } }
            }
            items(runs, key = { it.id }) { run ->
                Card(onClick = { openCheck(run) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(run.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            CheckStatusIcon(run)
                        }
                        Text("${run.status}${run.conclusion?.let { " · $it" } ?: ""} · ${run.sha.take(7)}", style = MaterialTheme.typography.bodySmall)
                        run.appName?.let { Text("App · $it", style = MaterialTheme.typography.labelMedium) }
                        run.summary?.let { Text(it.trim(), maxLines = 3, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    selected?.let { run ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(run.name) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    item { Text("Status · ${run.status}") }
                    item { Text("Conclusion · ${run.conclusion ?: "in progress"}") }
                    item { Text("Commit · ${run.sha}", style = MaterialTheme.typography.bodySmall) }
                    run.appName?.let { item { Text("Provider · $it", style = MaterialTheme.typography.bodySmall) } }
                    run.summary?.let { item { Text(it) } }
                    run.text?.takeIf { it.isNotBlank() }?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
                    item { Text("Annotations", style = MaterialTheme.typography.titleMedium) }
                    if (annotationLoading) item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(); Text("Loading annotations…") } }
                    if (!annotationLoading && annotations.isEmpty()) item { Text("No annotations reported by GitHub.", style = MaterialTheme.typography.bodySmall) }
                    items(annotations) { annotation ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(annotation.title ?: annotation.path, style = MaterialTheme.typography.labelLarge)
                                Text(annotation.path + annotation.startLine?.let { ":$it" }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                                annotation.annotationLevel?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                                annotation.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    run.detailsUrl?.let { url -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Icon(Icons.Outlined.OpenInNew, null); Text("Details") } }
                    run.htmlUrl?.let { url -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("GitHub") } }
                    TextButton(onClick = { selected = null }) { Text("Done") }
                }
            }
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(10.dp)) { Icon(icon, null); Text(value, style = MaterialTheme.typography.titleLarge); Text(title, style = MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun CheckStatusIcon(run: GitHubCheckRun) {
    val icon = when {
        run.conclusion == "success" -> Icons.Outlined.CheckCircle
        run.conclusion in setOf("failure", "timed_out", "cancelled", "startup_failure", "action_required") -> Icons.Outlined.ErrorOutline
        run.conclusion == "neutral" || run.conclusion == "skipped" -> Icons.Outlined.WarningAmber
        else -> Icons.Outlined.Schedule
    }
    Icon(icon, contentDescription = run.conclusion ?: run.status)
}
