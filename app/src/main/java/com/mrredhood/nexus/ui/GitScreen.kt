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
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.settings.ApiKeyStore
import com.mrredhood.nexus.core.settings.GitCredentialStore
import com.mrredhood.nexus.core.workspace.GitBranch
import com.mrredhood.nexus.core.workspace.GitCommit
import com.mrredhood.nexus.core.workspace.GitDiff
import com.mrredhood.nexus.core.workspace.GitHubRepositoryService
import com.mrredhood.nexus.core.workspace.GitHubSyncStatus
import com.mrredhood.nexus.core.workspace.GitRemoteService
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import com.mrredhood.nexus.data.ProjectRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(project: NexusProject, workspace: Workspace, onBack: () -> Unit) {
    val context = LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val gitCredentials = remember { GitCredentialStore(context) }
    val projectRepository = remember { ProjectRepository(context) }
    val fileSystem = remember { WorkspaceFileSystem(context) }
    val service = remember { GitHubRepositoryService(fileSystem) }
    val remoteService = remember { GitRemoteService(context) }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty()
    var branch by remember { mutableStateOf(project.branch.ifBlank { "main" }) }
    var remoteUrl by remember { mutableStateOf(project.remoteUrl?.takeIf { it.isNotBlank() } ?: repository.takeIf { it.contains('/') }?.let { "https://github.com/$it.git" }.orEmpty()) }
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
    var showCopilot by remember { mutableStateOf(false) }
    var showAdvancedGit by remember { mutableStateOf(false) }
    var showRemote by remember { mutableStateOf(false) }

    fun token(): String? = tokenStore.get("github")
    fun nativeReady() = remoteUrl.isNotBlank()

    fun runGit(operation: suspend (String) -> Unit) {
        val accessToken = token()
        if (accessToken.isNullOrBlank()) { error = "No GitHub token. Configure a repository remote and use native HTTPS/SSH instead."; return }
        if (repository.isBlank()) { error = "This project has no GitHub repository configured."; return }
        scope.launch {
            loading = true; error = null
            runCatching { operation(accessToken) }.onFailure { error = it.message ?: "Git operation failed" }
            loading = false
        }
    }

    fun runRemote(operation: suspend () -> GitRemoteService.GitRemoteResult) {
        if (!nativeReady()) { showRemote = true; return }
        scope.launch {
            loading = true; error = null
            runCatching { operation() }.onSuccess { result -> branch = result.branch; info = result.message }.onFailure { error = it.message ?: "Native Git operation failed" }
            loading = false
        }
    }

    fun refresh() {
        if (nativeReady()) {
            runRemote {
                remoteService.fetch(remoteUrl, project.id, branch).also {
                    remoteService.pull(remoteUrl, workspace, project.id, branch)
                    staged = emptySet()
                }
            }
        } else runGit { accessToken ->
            status = service.status(repository, branch, accessToken, workspace)
            diffs = service.diff(repository, branch, accessToken, workspace)
            staged = staged.filter { path -> diffs.any { it.path == path } }.toSet()
        }
    }

    LaunchedEffect(repository, remoteUrl, branch) { if (repository.isNotBlank() || remoteUrl.isNotBlank()) refresh() }

    BackHandler {
        when {
            showAdvancedGit -> showAdvancedGit = false
            showCopilot -> showCopilot = false
            showTerminal -> showTerminal = false
            showArtifacts -> showArtifacts = false
            showRemote -> showRemote = false
            else -> onBack()
        }
    }

    if (showCopilot) { CopilotScreen(project) { showCopilot = false }; return }
    if (showTerminal) { TerminalScreen(project, workspace) { showTerminal = false }; return }
    if (showArtifacts) { ArtifactCenterScreen(project, workspace) { showArtifacts = false }; return }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Source Control") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { showRemote = true }) { Icon(Icons.Outlined.Settings, "Repository remote") }
                IconButton(onClick = { showCopilot = true }) { Icon(Icons.Outlined.AutoAwesome, "GitHub Copilot") }
                IconButton(enabled = !loading, onClick = ::refresh) { Icon(Icons.Outlined.Refresh, "Refresh") }
                IconButton(enabled = !loading && repository.isNotBlank(), onClick = { showBranches = true }) { Icon(Icons.Outlined.AccountTree, "Branches") }
                IconButton(enabled = !loading && repository.isNotBlank(), onClick = { showAdvancedGit = true }) { Icon(Icons.Outlined.AccountTree, "Advanced Git") }
                IconButton(enabled = !loading && repository.isNotBlank(), onClick = { showLog = true }) { Icon(Icons.Outlined.History, "History") }
                IconButton(onClick = { showTerminal = true }) { Icon(Icons.Outlined.Terminal, "Terminal") }
                IconButton(onClick = { showArtifacts = true }) { Icon(Icons.Outlined.Download, "Artifacts") }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(repository.ifBlank { remoteUrl.ifBlank { "No repository" } }, style = MaterialTheme.typography.titleMedium)
                    Text("Branch · $branch")
                    Text(if (nativeReady()) "Transport · Native Git (HTTPS/SSH)" else "Transport · GitHub API")
                    if (remoteUrl.isNotBlank()) {
                        Text(remoteUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(remoteUrl))) }) { Icon(Icons.Outlined.Link, "Open remote"); Text("Open repository link") }
                    }
                    status?.let { Text("Remote ${it.remoteCommit.take(7)}") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = ::refresh, enabled = !loading && nativeReady(), modifier = Modifier.weight(1f)) { Text("Pull") }
                FilledTonalButton(onClick = { runRemote { remoteService.fetch(remoteUrl, project.id, branch) } }, enabled = !loading && nativeReady(), modifier = Modifier.weight(1f)) { Text("Fetch") }
                FilledTonalButton(onClick = { if (staged.isEmpty()) info = "Stage changes first." else showCommit = true }, enabled = !loading && nativeReady(), modifier = Modifier.weight(1f)) { Text("Commit & Push") }
            }
            if (!nativeReady()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = ::refresh, enabled = !loading, modifier = Modifier.weight(1f)) { Text("Refresh") }
                    FilledTonalButton(onClick = { runGit { accessToken -> val count = service.fetch(repository, branch, accessToken, workspace); info = "Fetched $count files from $branch"; status = service.status(repository, branch, accessToken, workspace); diffs = service.diff(repository, branch, accessToken, workspace); staged = emptySet() } }, enabled = !loading, modifier = Modifier.weight(1f)) { Text("Fetch") }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            status?.let { Text("Changes · ${it.changed.size} modified · ${it.added.size} added · ${it.deleted.size} deleted", style = MaterialTheme.typography.titleMedium) }
            if (diffs.isEmpty()) {
                Text("Working tree clean", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(diffs, key = { it.path }) { diff ->
                        Card(onClick = { selectedDiff = diff }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp)) {
                                Checkbox(checked = diff.path in staged, onCheckedChange = { checked -> staged = if (checked) staged + diff.path else staged - diff.path })
                                Column(Modifier.fillMaxWidth()) { Text(diff.path); Text("+${diff.addedLines}  -${diff.removedLines}", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { staged = diffs.map { it.path }.toSet() }, enabled = diffs.isNotEmpty() && !loading, modifier = Modifier.weight(1f)) { Text("Stage All") }
                OutlinedButton(onClick = { staged = emptySet() }, enabled = staged.isNotEmpty() && !loading, modifier = Modifier.weight(1f)) { Text("Unstage All") }
                Button(onClick = { showCommit = true }, enabled = staged.isNotEmpty() && !loading, modifier = Modifier.weight(1f)) { Text(if (nativeReady()) "Commit & Push" else "Commit") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showRemote) {
        var url by remember(remoteUrl) { mutableStateOf(remoteUrl) }
        var username by remember { mutableStateOf(gitCredentials.httpsUsername().orEmpty()) }
        var password by remember { mutableStateOf(gitCredentials.httpsPassword().orEmpty()) }
        var sshKey by remember { mutableStateOf(gitCredentials.sshPrivateKey().orEmpty()) }
        var knownHosts by remember { mutableStateOf(gitCredentials.knownHosts().orEmpty()) }
        AlertDialog(
            onDismissRequest = { showRemote = false },
            title = { Text("Repository remote") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nexus can use native Git over HTTPS or SSH. A GitHub API token is not required for public clone/pull; private/push access can use the credentials below.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(url, { url = it }, label = { Text("HTTPS or SSH repository URL") }, singleLine = true)
                    Text("HTTPS credentials", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, label = { Text("Password / PAT") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Text("SSH credentials", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(sshKey, { sshKey = it }, label = { Text("Private key (OpenSSH format)") }, minLines = 4, maxLines = 6)
                    OutlinedTextField(knownHosts, { knownHosts = it }, label = { Text("known_hosts (optional)") }, minLines = 2, maxLines = 4)
                    Text("For github.com SSH remotes, Nexus uses GitHub's published Ed25519 host key when known_hosts is omitted.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = url.isNotBlank(), onClick = {
                    remoteUrl = url.trim()
                    gitCredentials.setHttpsUsername(username.trim()); gitCredentials.setHttpsPassword(password)
                    gitCredentials.setSshPrivateKey(sshKey); gitCredentials.setKnownHosts(knownHosts)
                    scope.launch { projectRepository.save(project.copy(remoteUrl = remoteUrl, updatedAt = System.currentTimeMillis())) }
                    showRemote = false
                    runRemote { remoteService.connect(remoteUrl, workspace, project.id, branch) }
                }) { Text("Connect & Fetch") }
            },
            dismissButton = { TextButton(onClick = { showRemote = false }) { Text("Cancel") } }
        )
    }

    if (showAdvancedGit) GitAdvancedOperationsDialog(project = project, workspace = workspace, branch = branch, branches = branches, onClose = { showAdvancedGit = false }, onChanged = { refresh() })

    if (showCommit) {
        var message by remember { mutableStateOf("Update from Nexus") }
        AlertDialog(onDismissRequest = { showCommit = false }, title = { Text(if (nativeReady()) "Commit & Push" else "Commit staged changes") }, text = { OutlinedTextField(message, { message = it }, label = { Text("Commit message") }, singleLine = true) }, confirmButton = {
            TextButton(enabled = message.isNotBlank() && !loading, onClick = {
                if (nativeReady()) {
                    showCommit = false
                    runRemote { remoteService.commitAndPush(remoteUrl, workspace, project.id, branch, message) }
                    staged = emptySet()
                } else runGit { accessToken -> val result = service.commitAndPush(repository, branch, accessToken, workspace, message, staged); staged = emptySet(); info = "Committed ${result.commitSha.take(7)}"; showCommit = false; status = service.status(repository, branch, accessToken, workspace); diffs = service.diff(repository, branch, accessToken, workspace) }
            }) { Text(if (nativeReady()) "Commit & Push" else "Commit & Push") }
        }, dismissButton = { TextButton(onClick = { showCommit = false }) { Text("Cancel") } })
    }

    selectedDiff?.let { diff -> AlertDialog(onDismissRequest = { selectedDiff = null }, title = { Text(diff.path) }, text = { LazyColumn { item { Text("+${diff.addedLines}  -${diff.removedLines}") }; item { Spacer(Modifier.height(8.dp)) }; item { Text(diff.after.ifBlank { "File deleted" }, style = MaterialTheme.typography.bodySmall) } } }, confirmButton = { TextButton(onClick = { selectedDiff = null }) { Text("Close") } }) }

    if (showBranches) {
        LaunchedEffect(repository, branch) { runGit { accessToken -> branches = service.branches(repository, branch, accessToken) } }
        AlertDialog(onDismissRequest = { showBranches = false }, title = { Text("Branches") }, text = { if (branches.isEmpty()) CircularProgressIndicator() else LazyColumn { items(branches, key = { it.name }) { gitBranch -> TextButton(enabled = !loading, onClick = { if (gitBranch.name == branch) showBranches = false else runGit { accessToken -> val currentStatus = service.status(repository, branch, accessToken, workspace); if (currentStatus.changed.isNotEmpty() || currentStatus.added.isNotEmpty() || currentStatus.deleted.isNotEmpty()) { error = "Commit or stash local changes before switching branches."; return@runGit }; val count = service.fetch(repository, gitBranch.name, accessToken, workspace); branch = gitBranch.name; staged = emptySet(); info = "Checked out ${gitBranch.name} ($count files)"; showBranches = false; status = service.status(repository, branch, accessToken, workspace); diffs = service.diff(repository, branch, accessToken, workspace) } }) { Text(if (gitBranch.current) "✓ ${gitBranch.name}" else gitBranch.name) } } } }, confirmButton = { TextButton(onClick = { showBranches = false }) { Text("Close") } })
    }

    if (showLog) {
        LaunchedEffect(repository, branch) { runGit { accessToken -> commits = service.log(repository, branch, accessToken) } }
        AlertDialog(onDismissRequest = { showLog = false }, title = { Text("Commit history") }, text = { if (commits.isEmpty()) CircularProgressIndicator() else LazyColumn { items(commits, key = { it.sha }) { commit -> Column(Modifier.padding(vertical = 6.dp)) { Text("${commit.sha.take(7)} · ${commit.message}"); Text("${commit.author} · ${commit.timestamp}"); Text("${commit.filesChanged} files changed") } } } }, confirmButton = { TextButton(onClick = { showLog = false }) { Text("Close") } })
    }
}
