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
import com.mrredhood.nexus.core.workspace.GitHubAdvancedGitService
import com.mrredhood.nexus.core.workspace.GitMergeConflict
import com.mrredhood.nexus.core.workspace.GitMergePreview
import kotlinx.coroutines.launch

@Composable
fun GitMergeConflictDialog(
    project: NexusProject,
    baseBranch: String,
    headBranch: String,
    onClose: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val service = remember { GitHubAdvancedGitService() }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty()
    var preview by remember { mutableStateOf<GitMergePreview?>(null) }
    var resolutions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    fun run(operation: suspend (String) -> Unit) {
        val token = tokenStore.get("github")
        if (token.isNullOrBlank()) { error = "Add a GitHub token in Settings > GitHub first."; return }
        if (repository.isBlank()) { error = "This project has no GitHub repository configured."; return }
        scope.launch {
            loading = true
            error = null
            result = null
            runCatching { operation(token) }
                .onFailure { error = it.message ?: "Merge operation failed." }
            loading = false
        }
    }

    val currentPreview = preview
    val unresolved = currentPreview?.conflicts?.count { !it.binary && !resolutions.containsKey(it.path) } ?: 0

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Merge conflicts") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("$headBranch → $baseBranch", style = MaterialTheme.typography.titleMedium)
                    Text("Inspect each conflicting file, choose a side or edit the merged content, then create the merge commit.", style = MaterialTheme.typography.bodySmall)
                    Button(
                        enabled = !loading && baseBranch.isNotBlank() && headBranch.isNotBlank() && baseBranch != headBranch,
                        onClick = {
                            run { token ->
                                preview = service.previewMergeConflicts(repository, baseBranch.trim(), headBranch.trim(), token)
                                resolutions = emptyMap()
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text(if (currentPreview == null) "Inspect conflicts" else "Refresh conflicts") }
                }
                currentPreview?.let { merge ->
                    if (merge.conflicts.isEmpty()) {
                        item { Text("No merge conflicts detected. The branches can be merged normally.", color = MaterialTheme.colorScheme.primary) }
                    } else {
                        item { Text("${merge.conflicts.size} conflicting file${if (merge.conflicts.size == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall) }
                        items(merge.conflicts, key = { it.path }) { conflict ->
                            ConflictEditor(conflict, resolutions[conflict.path]) { value -> resolutions = resolutions + (conflict.path to value) }
                        }
                        item {
                            Button(
                                enabled = !loading && unresolved == 0,
                                onClick = {
                                    run { token ->
                                        val sha = service.resolveMergeConflicts(repository, merge, resolutions, token)
                                        result = "Merge commit created: ${sha.take(12)}"
                                        onChanged()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (unresolved == 0) "Create merge commit" else "$unresolved conflict${if (unresolved == 1) "" else "s"} unresolved") }
                        }
                    }
                }
                if (loading) item { CircularProgressIndicator() }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                result?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
private fun ConflictEditor(conflict: GitMergeConflict, resolution: String?, onResolution: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(conflict.path, style = MaterialTheme.typography.titleSmall)
        if (conflict.binary) {
            Text("Binary or oversized conflict. Nexus cannot safely render this file for inline editing.", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(enabled = conflict.baseContent != null, onClick = { onResolution(conflict.baseContent.orEmpty()) }, modifier = Modifier.weight(1f)) { Text("Use base") }
                OutlinedButton(enabled = conflict.headContent != null, onClick = { onResolution(conflict.headContent.orEmpty()) }, modifier = Modifier.weight(1f)) { Text("Use head") }
            }
            OutlinedTextField(
                value = resolution ?: "",
                onValueChange = onResolution,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Resolved content") },
                minLines = 4,
                maxLines = 12
            )
            Text("Ancestor version is available to the resolver for context; the field above is the exact content that will be committed.", style = MaterialTheme.typography.labelSmall)
        }
    }
}
