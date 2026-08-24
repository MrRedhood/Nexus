package com.mrredhood.nexus.ui

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
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.settings.ApiKeyStore
import com.mrredhood.nexus.core.workspace.GitHubRepositoryService
import com.mrredhood.nexus.core.workspace.GitHubSyncStatus
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import kotlinx.coroutines.launch

@Composable
fun GitScreen(project: NexusProject, workspace: Workspace, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val fileSystem = remember { WorkspaceFileSystem(context) }
    val service = remember { GitHubRepositoryService(fileSystem) }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty()
    val branch = project.branch.ifBlank { "main" }
    var status by remember { mutableStateOf<GitHubSyncStatus?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var showCommit by remember { mutableStateOf(false) }

    fun refreshStatus() {
        val token = tokenStore.get("github")
        if (token.isNullOrBlank()) { error = "Add a GitHub token in Settings > GitHub first."; return }
        if (repository.isBlank()) { error = "This project has no GitHub repository configured."; return }
        scope.launch {
            loading = true; error = null
            runCatching { service.status(repository, branch, token, workspace) }
                .onSuccess { status = it }
                .onFailure { error = it.message ?: "Unable to read GitHub status" }
            loading = false
        }
    }

    fun fetch() {
        val token = tokenStore.get("github")
        if (token.isNullOrBlank()) { error = "Add a GitHub token in Settings > GitHub first."; return }
        scope.launch {
            loading = true; error = null
            runCatching { service.fetch(repository, branch, token, workspace) }
                .onSuccess { count -> info = "Fetched $count file${if (count == 1) "" else "s"} from $repository:$branch"; refreshStatus() }
                .onFailure { error = it.message ?: "Fetch failed" }
            loading = false
        }
    }

    fun commit(message: String) {
        val token = tokenStore.get("github")
        if (token.isNullOrBlank()) { error = "Add a GitHub token in Settings > GitHub first."; return }
        scope.launch {
            loading = true; error = null; showCommit = false
            runCatching { service.commitAndPush(repository, branch, token, workspace, message) }
                .onSuccess { result -> info = "Committed and pushed ${result.commitSha.take(7)} to $branch"; refreshStatus() }
                .onFailure { error = it.message ?: "Commit/push failed" }
            loading = false
        }
    }

    LaunchedEffect(repository, branch) { if (repository.isNotBlank()) refreshStatus() }
    BackHandler(onBack = onBack)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Source Control") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            actions = { IconButton(enabled = !loading, onClick = ::refreshStatus) { Icon(Icons.Outlined.Refresh, "Refresh") } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Outlined.Source, null); Text(repository.ifBlank { "No repository" }, style = MaterialTheme.typography.titleMedium) }
                    Text("Branch · $branch", style = MaterialTheme.typography.bodyMedium)
                    status?.let { Text("Remote commit · ${it.remoteCommit.take(7)}", style = MaterialTheme.typography.labelMedium) }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = !loading && repository.isNotBlank(), onClick = ::fetch, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.CloudDownload, null); Spacer(Modifier.padding(horizontal = 3.dp)); Text("Fetch") }
                FilledTonalButton(enabled = !loading && repository.isNotBlank(), onClick = { showCommit = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.CloudUpload, null); Spacer(Modifier.padding(horizontal = 3.dp)); Text("Commit & Push") }
            }

            if (loading) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            info?.let { Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { Text(it, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) } }
            error?.let { Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) } }

            status?.let { sync ->
                Text("Changes", style = MaterialTheme.typography.titleMedium)
                val items = buildList {
                    sync.added.forEach { add("Added · $it") }
                    sync.changed.forEach { add("Modified · $it") }
                    sync.deleted.forEach { add("Deleted · $it") }
                }
                if (items.isEmpty()) Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { Text("Working tree matches the selected GitHub branch.", Modifier.padding(16.dp)) }
                else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(items) { change -> Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { Text(change, Modifier.padding(14.dp)) } } }
            }
        }
    }

    if (showCommit) CommitDialog(onDismiss = { showCommit = false }, onCommit = ::commit)
}

@Composable
private fun CommitDialog(onDismiss: () -> Unit, onCommit: (String) -> Unit) {
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Commit changes") },
        text = { OutlinedTextField(value = message, onValueChange = { message = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Commit message") }, placeholder = { Text("Describe your changes") }, minLines = 2) },
        confirmButton = { FilledTonalButton(enabled = message.isNotBlank(), onClick = { onCommit(message.trim()) }) { Text("Commit & Push") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
