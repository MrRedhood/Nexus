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
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.mrredhood.nexus.core.workspace.GitBranch
import com.mrredhood.nexus.core.workspace.GitCommit
import com.mrredhood.nexus.core.workspace.GitDiff
import com.mrredhood.nexus.core.workspace.GitHubRepositoryService
import com.mrredhood.nexus.core.workspace.GitHubSyncStatus
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(project: NexusProject, workspace: Workspace, onBack: () -> Unit) {
    val context = LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val fileSystem = remember { WorkspaceFileSystem(context) }
    val service = remember { GitHubRepositoryService(fileSystem) }
    val scope = rememberCoroutineScope()

    val repository = project.repository.orEmpty()
    var branch by remember { mutableStateOf(project.branch.ifBlank { "main" }) }
    var status by remember { mutableStateOf<GitHubSyncStatus?>(null) }
    var diffs by remember { mutableStateOf<List<GitDiff>>(emptyList()) }
    var branches by remember { mutableStateOf<List<GitBranch>>(emptyList()) }
    var commits by remember { mutableStateOf<List<GitCommit>>(emptyList()) }
    var staged by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var showCommit by remember { mutableStateOf(false) }
    var selectedDiff by remember { mutableStateOf<GitDiff?>(null) }
    var showBranches by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showTerminal by remember { mutableStateOf(false) }
    var showArtifacts by remember { mutableStateOf(false) }

    fun token(): String? = tokenStore.get("github")

    fun runGit(operation: suspend (String) -> Unit) {
        val accessToken = token()
        if (accessToken.isNullOrBlank()) {
            error = "Add a GitHub token in Settings > GitHub first."
            return
        }
        if (repository.isBlank()) {
            error = "This project has no GitHub repository configured."
            return
        }
        scope.launch {
            loading = true
            error = null
            runCatching { operation(accessToken) }
                .onFailure { error = it.message ?: "Git operation failed" }
            loading = false
        }
    }

    fun refresh() {
        runGit { accessToken ->
            status = service.status(repository, branch, accessToken, workspace)
            diffs = service.diff(repository, branch, accessToken, workspace)
            staged = staged.filter { path -> diffs.any { it.path == path } }.toSet()
        }
    }

    LaunchedEffect(repository, branch) {
        if (repository.isNotBlank()) refresh()
    }

    BackHandler {
        when {
            showTerminal -> showTerminal = false
            showArtifacts -> showArtifacts = false
            else -> onBack()
        }
    }

    if (showTerminal) {
        TerminalScreen(project, workspace) { showTerminal = false }
        return
    }
    if (showArtifacts) {
        ArtifactCenterScreen(project, workspace) { showArtifacts = false }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Source Control") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(enabled = !loading, onClick = ::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(enabled = !loading, onClick = { showBranches = true }) {
                        Icon(Icons.Outlined.AccountTree, contentDescription = "Branches")
                    }
                    IconButton(enabled = !loading, onClick = { showLog = true }) {
                        Icon(Icons.Outlined.History, contentDescription = "History")
                    }
                    IconButton(onClick = { showTerminal = true }) {
                        Icon(Icons.Outlined.Terminal, contentDescription = "Terminal")
                    }
                    IconButton(onClick = { showArtifacts = true }) {
                        Icon(Icons.Outlined.Download, contentDescription = "Artifacts")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        repository.ifBlank { "No repository" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("Branch · $branch")
                    status?.let { Text("Remote ${it.remoteCommit.take(7)}") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = ::refresh,
                    enabled = !loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Refresh")
                }
                FilledTonalButton(
                    onClick = {
                        runGit { accessToken ->
                            val count = service.fetch(repository, branch, accessToken, workspace)
                            info = "Fetched $count file${if (count == 1) "" else "s"} from $branch"
                            status = service.status(repository, branch, accessToken, workspace)
                            diffs = service.diff(repository, branch, accessToken, workspace)
                            staged = emptySet()
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Fetch")
                }
            }

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            status?.let {
                Text(
                    "Changes · ${it.changed.size} modified · ${it.added.size} added · ${it.deleted.size} deleted",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (diffs.isEmpty()) {
                Text(
                    "Working tree clean",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(diffs, key = { it.path }) { diff ->
                        Card(
                            onClick = { selectedDiff = diff },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp)) {
                                Checkbox(
                                    checked = diff.path in staged,
                                    onCheckedChange = { checked ->
                                        staged = if (checked) staged + diff.path else staged - diff.path
                                    }
                                )
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(diff.path)
                                    Text(
                                        "+${diff.addedLines}  -${diff.removedLines}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { staged = diffs.map { it.path }.toSet() },
                    enabled = diffs.isNotEmpty() && !loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stage All")
                }
                OutlinedButton(
                    onClick = { staged = emptySet() },
                    enabled = staged.isNotEmpty() && !loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Unstage All")
                }
                Button(
                    onClick = { showCommit = true },
                    enabled = staged.isNotEmpty() && !loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Commit")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    if (showCommit) {
        var message by remember { mutableStateOf("Update from Nexus") }
        AlertDialog(
            onDismissRequest = { showCommit = false },
            title = { Text("Commit staged changes") },
            text = {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Commit message") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = message.isNotBlank() && !loading,
                    onClick = {
                        runGit { accessToken ->
                            val result = service.commitAndPush(repository, branch, accessToken, workspace, message, staged)
                            staged = emptySet()
                            info = "Committed ${result.commitSha.take(7)}"
                            showCommit = false
                            status = service.status(repository, branch, accessToken, workspace)
                            diffs = service.diff(repository, branch, accessToken, workspace)
                        }
                    }
                ) {
                    Text("Commit & Push")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommit = false }) { Text("Cancel") }
            }
        )
    }

    selectedDiff?.let { diff ->
        AlertDialog(
            onDismissRequest = { selectedDiff = null },
            title = { Text(diff.path) },
            text = {
                LazyColumn {
                    item { Text("+${diff.addedLines}  -${diff.removedLines}") }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        Text(
                            diff.after.ifBlank { "File deleted" },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDiff = null }) { Text("Close") }
            }
        )
    }

    if (showBranches) {
        LaunchedEffect(repository, branch) {
            runGit { accessToken ->
                branches = service.branches(repository, branch, accessToken)
            }
        }
        AlertDialog(
            onDismissRequest = { showBranches = false },
            title = { Text("Branches") },
            text = {
                if (branches.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    LazyColumn {
                        items(branches, key = { it.name }) { gitBranch ->
                            TextButton(
                                enabled = !loading,
                                onClick = {
                                    if (gitBranch.name == branch) {
                                        showBranches = false
                                    } else {
                                        runGit { accessToken ->
                                            val currentStatus = service.status(repository, branch, accessToken, workspace)
                                            if (currentStatus.changed.isNotEmpty() || currentStatus.added.isNotEmpty() || currentStatus.deleted.isNotEmpty()) {
                                                error = "Commit or discard local changes before switching branches."
                                                return@runGit
                                            }
                                            val count = service.fetch(repository, gitBranch.name, accessToken, workspace)
                                            branch = gitBranch.name
                                            staged = emptySet()
                                            info = "Checked out ${gitBranch.name} ($count files)"
                                            showBranches = false
                                            status = service.status(repository, branch, accessToken, workspace)
                                            diffs = service.diff(repository, branch, accessToken, workspace)
                                        }
                                    }
                                }
                            ) {
                                Text(if (gitBranch.current) "✓ ${gitBranch.name}" else gitBranch.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBranches = false }) { Text("Close") }
            }
        )
    }

    if (showLog) {
        LaunchedEffect(repository, branch) {
            runGit { accessToken ->
                commits = service.log(repository, branch, accessToken)
            }
        }
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Commit history") },
            text = {
                if (commits.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    LazyColumn {
                        items(commits, key = { it.sha }) { commit ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text("${commit.sha.take(7)} · ${commit.message}")
                                Text("${commit.author} · ${commit.timestamp}")
                                Text("${commit.filesChanged} files changed")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLog = false }) { Text("Close") }
            }
        )
    }
}
