package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.mrredhood.nexus.core.ai.ChatAttachment
import com.mrredhood.nexus.core.ai.ChatContext
import com.mrredhood.nexus.core.workspace.EntryType
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceEntry
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import kotlinx.coroutines.launch

@Composable
fun ChatContextAttachmentPanel(
    workspace: Workspace,
    fileSystem: WorkspaceFileSystem,
    context: ChatContext,
    onContextChanged: (ChatContext) -> Unit
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val enabledCount = listOf(context.includeCurrentFile, context.includeSelection, context.includeGitDiff, context.includeTerminalOutput, context.includeWorkspaceSummary).count { it }
    val sourceCount = enabledCount + context.attachedFiles.size

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Chat context", style = MaterialTheme.typography.titleSmall)
                    Text("$sourceCount sources selected", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Configure") }
                Button(onClick = { pickerOpen = true }) { Text("Attach file") }
            }
            if (context.attachedFiles.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    context.attachedFiles.take(4).forEach { attachment ->
                        FilterChip(selected = true, onClick = { onContextChanged(context.copy(attachedFiles = context.attachedFiles.filterNot { it.path == attachment.path })) }, label = { Text(attachment.path.substringAfterLast('/')) })
                    }
                    if (context.attachedFiles.size > 4) Text("+${context.attachedFiles.size - 4} more", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (expanded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ContextToggle("Current file", context.includeCurrentFile) { onContextChanged(context.copy(includeCurrentFile = !context.includeCurrentFile)) }
                    ContextToggle("Selection", context.includeSelection) { onContextChanged(context.copy(includeSelection = !context.includeSelection)) }
                    ContextToggle("Git diff", context.includeGitDiff) { onContextChanged(context.copy(includeGitDiff = !context.includeGitDiff)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ContextToggle("Terminal", context.includeTerminalOutput) { onContextChanged(context.copy(includeTerminalOutput = !context.includeTerminalOutput)) }
                    ContextToggle("Workspace", context.includeWorkspaceSummary) { onContextChanged(context.copy(includeWorkspaceSummary = !context.includeWorkspaceSummary)) }
                }
                Text("Selected sources are still bounded by Nexus AI context token limits.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (pickerOpen) {
        WorkspaceFilePicker(
            workspace = workspace,
            fileSystem = fileSystem,
            selectedPaths = context.attachedFiles.map { it.path }.toSet(),
            onDismiss = { pickerOpen = false },
            onAttach = { attachment ->
                val next = context.attachedFiles.filterNot { it.path == attachment.path } + attachment
                onContextChanged(context.copy(attachedFiles = next))
            }
        )
    }
}

@Composable
private fun ContextToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun WorkspaceFilePicker(
    workspace: Workspace,
    fileSystem: WorkspaceFileSystem,
    selectedPaths: Set<String>,
    onDismiss: () -> Unit,
    onAttach: (ChatAttachment) -> Unit
) {
    var directory by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<WorkspaceEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(workspace.id, directory) {
        loading = true
        error = null
        runCatching { fileSystem.list(workspace, directory) }
            .onSuccess { entries = it }
            .onFailure { error = it.message ?: "Unable to list workspace files." }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attach workspace file") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (directory.isBlank()) "/" else "/$directory", style = MaterialTheme.typography.labelMedium)
                if (directory.isNotBlank()) OutlinedButton(onClick = { directory = directory.substringBeforeLast('/', "") }) { Text("Up") }
                if (loading) Text("Loading workspace…")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(entries) { entry ->
                        if (entry.type == EntryType.DIRECTORY) {
                            OutlinedButton(onClick = { directory = entry.relativePath }, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Text("Folder: ${entry.name}") }
                        } else {
                            val attached = entry.relativePath in selectedPaths
                            OutlinedButton(
                                onClick = {
                                    if (!attached && !loading) {
                                        loading = true
                                        scope.launch {
                                            runCatching { fileSystem.read(workspace, entry.relativePath) }
                                                .onSuccess { file -> onAttach(ChatAttachment(file.relativePath, file.content, file.sizeBytes)) }
                                                .onFailure { error = it.message ?: "Unable to read file." }
                                            loading = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loading
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name)
                                    Text("${entry.sizeBytes} bytes", style = MaterialTheme.typography.labelSmall)
                                }
                                Text(if (attached) "Attached" else "Attach")
                            }
                        }
                    }
                }
                Text("Attachments are read through the workspace filesystem and remain subject to Nexus file/context limits.", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
