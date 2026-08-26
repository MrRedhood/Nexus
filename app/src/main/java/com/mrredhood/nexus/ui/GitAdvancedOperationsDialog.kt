package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.mrredhood.nexus.core.workspace.GitHubAdvancedGitService
import com.mrredhood.nexus.core.workspace.GitHubRepositoryService
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import kotlinx.coroutines.launch

@Composable
fun GitAdvancedOperationsDialog(
    project: NexusProject,
    workspace: Workspace,
    branch: String,
    branches: List<GitBranch>,
    onClose: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val fileSystem = remember { WorkspaceFileSystem(context) }
    val repositoryService = remember { GitHubRepositoryService(fileSystem) }
    val service = remember { GitHubAdvancedGitService() }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty()
    var baseBranch by remember(branch) { mutableStateOf(branch) }
    var headBranch by remember(branch, branches) { mutableStateOf(branches.firstOrNull { it.name != branch && !it.name.startsWith("nexus/stash/") }?.name.orEmpty()) }
    var commitSha by remember { mutableStateOf("") }
    var resetSha by remember { mutableStateOf("") }
    var stashName by remember { mutableStateOf("nexus/stash/${System.currentTimeMillis()}") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    fun run(operation: suspend (String) -> String) {
        val token = tokenStore.get("github")
        if (token.isNullOrBlank()) { error = "Add a GitHub token in Settings > GitHub first."; return }
        if (repository.isBlank()) { error = "This project has no GitHub repository configured."; return }
        scope.launch {
            loading = true
            error = null
            result = null
            runCatching { operation(token) }
                .onSuccess { result = it.ifBlank { "Operation completed." }; onChanged() }
                .onFailure { error = it.message ?: "Git operation failed" }
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Advanced Git") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text("Stash", style = MaterialTheme.typography.titleMedium)
                    Text("Nexus stores a stash as a temporary cloud branch so it survives app restarts. It is not a local .git stash.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(stashName, { stashName = it }, label = { Text("Stash name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Button(enabled = !loading && stashName.startsWith("nexus/stash/"), onClick = {
                        run { token ->
                            val name = service.createStashBranch(repository, branch, token, stashName.trim())
                            val status = repositoryService.status(repository, branch, token, workspace)
                            val paths = (status.changed + status.added + status.deleted).toSet()
                            require(paths.isNotEmpty()) { "Working tree is clean; there is nothing to stash." }
                            repositoryService.commitAndPush(repository, name, token, workspace, "Nexus stash: $name")
                            "Stashed ${paths.size} changed path${if (paths.size == 1) "" else "s"} as $name"
                        }
                    }, modifier = Modifier.padding(top = 8.dp)) { Text("Create stash") }
                    val stashes = branches.filter { it.name.startsWith("nexus/stash/") }
                    if (stashes.isNotEmpty()) {
                        Text("Existing stashes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                        stashes.forEach { stash ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stash.name.removePrefix("nexus/stash/"), modifier = Modifier.weight(1f))
                                TextButton(enabled = !loading, onClick = {
                                    run { token ->
                                        val count = repositoryService.fetch(repository, stash.name, token, workspace)
                                        "Applied ${stash.name} ($count files)"
                                    }
                                }) { Text("Apply") }
                                TextButton(enabled = !loading, onClick = {
                                    run { token -> service.dropStash(repository, stash.name, token); "Dropped ${stash.name}" }
                                }) { Text("Drop") }
                            }
                        }
                    }
                }
                item {
                    Text("Merge branches", style = MaterialTheme.typography.titleMedium)
                    Text("Merge the selected head into the base branch using GitHub's merge API.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(baseBranch, { baseBranch = it }, label = { Text("Base branch") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(headBranch, { headBranch = it }, label = { Text("Head branch") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Button(enabled = !loading && baseBranch.isNotBlank() && headBranch.isNotBlank() && baseBranch != headBranch, onClick = { run { token -> service.mergeBranches(repository, baseBranch.trim(), headBranch.trim(), token) } }, modifier = Modifier.padding(top = 8.dp)) { Text("Merge") }
                }
                item {
                    Text("Cherry-pick commit", style = MaterialTheme.typography.titleMedium)
                    Text("Apply an existing commit to the current branch through GitHub.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(commitSha, { commitSha = it }, label = { Text("Commit SHA") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Button(enabled = !loading && commitSha.isNotBlank(), onClick = { run { token -> service.cherryPick(repository, commitSha.trim(), branch, token).second } }, modifier = Modifier.padding(top = 8.dp)) { Text("Cherry-pick") }
                }
                item {
                    Text("Reset branch", style = MaterialTheme.typography.titleMedium)
                    Text("Move the branch ref to an exact commit. Force reset is destructive.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(resetSha, { resetSha = it }, label = { Text("Target commit SHA") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(enabled = !loading && resetSha.isNotBlank(), onClick = { run { token -> service.resetBranch(repository, branch, resetSha.trim(), token, false); "Reset $branch to ${resetSha.trim().take(12)}" } }, modifier = Modifier.weight(1f)) { Text("Reset") }
                        Button(enabled = !loading && resetSha.isNotBlank(), onClick = { run { token -> service.resetBranch(repository, branch, resetSha.trim(), token, true); "Force-reset $branch to ${resetSha.trim().take(12)}" } }, modifier = Modifier.weight(1f)) { Text("Force reset") }
                    }
                }
                if (loading) item { CircularProgressIndicator() }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                result?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}
