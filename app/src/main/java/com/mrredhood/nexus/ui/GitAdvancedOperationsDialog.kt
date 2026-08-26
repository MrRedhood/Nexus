package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mrredhood.nexus.core.workspace.GitBranch
import com.mrredhood.nexus.core.workspace.GitHubAdvancedGitService
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun GitAdvancedOperationsDialog(
    project: NexusProject,
    branch: String,
    branches: List<GitBranch>,
    onRefreshBranches: suspend (String) -> List<GitBranch>,
    onClose: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val service = remember { GitHubAdvancedGitService() }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty()
    var selectedBase by remember(branch) { mutableStateOf(branch) }
    var selectedHead by remember(branch, branches) { mutableStateOf(branches.firstOrNull { it.name != branch }?.name.orEmpty()) }
    var commitSha by remember { mutableStateOf("") }
    var resetSha by remember { mutableStateOf("") }
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
                    Text("Merge branches", style = MaterialTheme.typography.titleMedium)
                    Text("Merge the selected head into the base branch using GitHub's merge API.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(selectedBase, { selectedBase = it }, label = { Text("Base branch") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(selectedHead, { selectedHead = it }, label = { Text("Head branch") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Button(enabled = !loading && selectedBase.isNotBlank() && selectedHead.isNotBlank(), onClick = { run { token -> service.mergeBranches(repository, selectedBase.trim(), selectedHead.trim(), token) } }, modifier = Modifier.padding(top = 8.dp)) { Text("Merge") }
                }
                item {
                    Text("Cherry-pick commit", style = MaterialTheme.typography.titleMedium)
                    Text("Apply an existing commit to the current branch through GitHub.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(commitSha, { commitSha = it }, label = { Text("Commit SHA") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Button(enabled = !loading && commitSha.isNotBlank(), onClick = { run { token -> service.cherryPick(repository, commitSha.trim(), branch, token).second } }, modifier = Modifier.padding(top = 8.dp)) { Text("Cherry-pick") }
                }
                item {
                    Text("Reset branch", style = MaterialTheme.typography.titleMedium)
                    Text("Move the branch ref to an exact commit. This is destructive when force is enabled.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(resetSha, { resetSha = it }, label = { Text("Target commit SHA") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(enabled = !loading && resetSha.isNotBlank(), onClick = { run { token -> service.resetBranch(repository, branch, resetSha.trim(), token, false); "Reset $branch to ${resetSha.trim().take(12)}" } }, modifier = Modifier.weight(1f)) { Text("Fast reset") }
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
