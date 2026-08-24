package com.mrredhood.nexus.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.nexus.core.ai.ChatContext
import com.mrredhood.nexus.core.ai.ChatContextStore
import com.mrredhood.nexus.core.ai.NexusActionExecutionRegistry
import com.mrredhood.nexus.core.ai.NexusActionPolicy
import com.mrredhood.nexus.core.ai.NexusActionStatus
import com.mrredhood.nexus.core.ai.NexusDiffKind
import com.mrredhood.nexus.core.ai.NexusEditorActionBus
import com.mrredhood.nexus.core.ai.WorkspaceContextService
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceChangeStatus
import com.mrredhood.nexus.core.workspace.WorkspaceChangeTrackerRegistry
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem

private val NEXUS_COMMANDS = listOf("/explain", "/fix", "/refactor", "/optimize", "/test", "/build", "/search", "/open")
private val MODEL_FILTERS = listOf("All", "Free", "Premium", "Image", "Video", "Audio")
private val MUTATING_ACTION_TYPES = setOf("create_file", "create_directory", "patch_file", "replace_file", "delete_file", "rename_file", "copy_file", "move_file")

@Composable
fun ChatScreen(project: NexusProject, workspace: Workspace, context: ChatContext = ChatContext(), onClose: (() -> Unit)? = null) {
    val vm: ChatViewModel = viewModel()
    val settingsVm: AdvancedSettingsViewModel = viewModel()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val usage by vm.tokenUsage.collectAsStateWithLifecycle()
    val contextSnapshot by vm.contextSnapshot.collectAsStateWithLifecycle()
    val proposals by vm.actionProposals.collectAsStateWithLifecycle()
    val reviews by vm.actionReviews.collectAsStateWithLifecycle()
    val executions by NexusActionExecutionRegistry.executions.collectAsStateWithLifecycle()
    val summaries by WorkspaceChangeTrackerRegistry.summaries.collectAsStateWithLifecycle()
    val actionMessage by vm.actionMessage.collectAsStateWithLifecycle()
    val liveContexts by ChatContextStore.contexts.collectAsStateWithLifecycle()
    val featureSettings by settingsVm.settings.collectAsStateWithLifecycle()
    val models by settingsVm.models.collectAsStateWithLifecycle()
    val loadingModels by settingsVm.loadingModels.collectAsStateWithLifecycle()
    val modelError by settingsVm.modelError.collectAsStateWithLifecycle()
    val androidContext = LocalContext.current
    val contextService = remember(androidContext) { WorkspaceContextService(WorkspaceFileSystem(androidContext.applicationContext)) }
    val fileSystem = remember(androidContext) { WorkspaceFileSystem(androidContext.applicationContext) }
    var input by remember { mutableStateOf("") }
    var showContextInspector by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var modelFilter by remember { mutableStateOf("All") }
    var copiedMessageIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val workspaceContext = liveContexts[workspace.id] ?: context
    var chatContext by remember(workspace.id) { mutableStateOf(workspaceContext) }
    val workspaceChanges = summaries[workspace.id]

    LaunchedEffect(workspace.id, featureSettings.provider, executions.size) {
        vm.open(workspace)
        if (executions.isEmpty()) NexusActionExecutionRegistry.clear()
        WorkspaceChangeTrackerRegistry.startAndRefresh(workspace, fileSystem)
        runCatching { contextService.refresh(workspace) }
        settingsVm.loadModels(featureSettings.provider)
    }
    LaunchedEffect(workspaceContext) { chatContext = workspaceContext }
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    if (showContextInspector && contextSnapshot != null) AIContextInspector(snapshot = contextSnapshot!!, onDismiss = { showContextInspector = false })

    val filteredModels = models.filter { model ->
        when (modelFilter) { "Free" -> model.pricing.free; "Premium" -> model.premium; "Image" -> model.image; "Video" -> model.video; "Audio" -> model.audio; else -> true }
    }
    val successfulChanges = executions.values.filter { it.success && it.actionType in MUTATING_ACTION_TYPES }
    val createdCount = successfulChanges.count { it.actionType == "create_file" || it.actionType == "create_directory" }
    val modifiedCount = successfulChanges.count { it.actionType == "patch_file" || it.actionType == "replace_file" }
    val deletedCount = successfulChanges.count { it.actionType == "delete_file" }
    val addedLines = successfulChanges.sumOf { it.additions }
    val removedLines = successfulChanges.sumOf { it.deletions }

    fun copyMessage(index: Int, content: String) {
        if (content.isBlank()) return
        val clipboard = androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nexus message", content))
        copiedMessageIndex = index
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("Nexus AI", style = MaterialTheme.typography.titleLarge); Text(project.name, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = MaterialTheme.shapes.small) { Text("${featureSettings.provider} · ${featureSettings.model}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
                        IconButton(onClick = vm::clear, enabled = !generating) { Icon(Icons.Outlined.DeleteSweep, "Clear chat") }
                        onClose?.let { IconButton(onClick = it) { Text("×", style = MaterialTheme.typography.titleLarge) } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showContextInspector = true }, enabled = contextSnapshot != null) { Text("Context") }
                    FilterChip(selected = true, onClick = { showModelMenu = true }, label = { Text(if (loadingModels) "Loading models…" else "${filteredModels.size} models") })
                    DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { MODEL_FILTERS.forEach { filter -> FilterChip(selected = modelFilter == filter, onClick = { modelFilter = filter }, label = { Text(filter) }) } }
                        if (filteredModels.isEmpty()) DropdownMenuItem(text = { Text(modelError ?: if (loadingModels) "Loading…" else "No matching models") }, onClick = { if (!loadingModels) settingsVm.loadModels(featureSettings.provider) })
                        else filteredModels.take(100).forEach { model -> DropdownMenuItem(text = { Column { Text(model.name); Text(model.id, style = MaterialTheme.typography.labelSmall) } }, onClick = { settingsVm.update { it.copy(model = model.id, apiKeyConfigured = settingsVm.hasApiKey(model.provider)) }; showModelMenu = false }) }
                    }
                    if (usage.total > 0) Text("${usage.total} tokens", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }

        ChatContextAttachmentPanel(workspace = workspace, fileSystem = fileSystem, context = chatContext, onContextChanged = { chatContext = it })

        workspaceChanges?.takeIf { it.changes.isNotEmpty() }?.let { changes ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Workspace changes", style = MaterialTheme.typography.titleMedium)
                        Text("${changes.created} created · ${changes.modified} modified · ${changes.deleted} deleted", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("+${changes.additions} lines · -${changes.deletions} lines", style = MaterialTheme.typography.labelMedium)
                    changes.changes.forEach { change ->
                        Card(onClick = { if (change.status != WorkspaceChangeStatus.DELETED) NexusEditorActionBus.request(workspace.id, change.relativePath, true) }, enabled = change.status != WorkspaceChangeStatus.DELETED) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(when (change.status) { WorkspaceChangeStatus.CREATED -> "C"; WorkspaceChangeStatus.MODIFIED -> "M"; WorkspaceChangeStatus.DELETED -> "D" }, style = MaterialTheme.typography.labelLarge)
                                Column(Modifier.weight(1f)) { Text(change.relativePath); Text("+${change.additions} / -${change.deletions} lines", style = MaterialTheme.typography.labelSmall) }
                                if (change.status != WorkspaceChangeStatus.DELETED) Icon(Icons.Outlined.OpenInNew, "Open in editor")
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(messages) { index, message ->
                val assistant = message.role == "assistant"
                val previousUser = if (assistant) messages.subList(0, index).lastOrNull { it.role == "user" } else null
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (assistant) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (assistant) "Nexus" else "You", style = MaterialTheme.typography.labelMedium)
                            if (message.content.isNotBlank() && !generating) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { copyMessage(index, message.content) }) { Icon(Icons.Outlined.ContentCopy, if (copiedMessageIndex == index) "Copied" else "Copy") }
                                if (!assistant) IconButton(onClick = { input = message.content }) { Icon(Icons.Outlined.Edit, "Edit message") }
                                else if (previousUser != null) IconButton(onClick = { vm.regenerate(index, chatContext) }) { Icon(Icons.Outlined.Refresh, "Regenerate response") }
                            }
                        }
                        Text(message.content.ifBlank { "Thinking…" })
                        if (copiedMessageIndex == index) Text("Copied to clipboard", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (proposals.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI actions", style = MaterialTheme.typography.titleMedium)
                    if (successfulChanges.isNotEmpty()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { Text("Created $createdCount"); Text("Modified $modifiedCount"); Text("Deleted $deletedCount"); Text("+$addedLines"); Text("-$removedLines") } }
                    proposals.forEach { proposal ->
                        val action = proposal.action
                        val review = reviews[proposal.id]
                        val execution = executions[proposal.id]
                        val isMutating = action.type in MUTATING_ACTION_TYPES
                        var expanded by remember(proposal.id, review?.proposed) { mutableStateOf(false) }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(action.type.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                                action.path?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                execution?.let { result ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(if (result.success) "Applied" else "Failed", style = MaterialTheme.typography.labelMedium, color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error); if (result.additions > 0 || result.deletions > 0) Text("+${result.additions} / -${result.deletions} lines", style = MaterialTheme.typography.labelMedium) }
                                    Text(result.message, style = MaterialTheme.typography.bodySmall)
                                }
                                review?.let { diff ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text("+${diff.additions}", style = MaterialTheme.typography.labelMedium); Text("-${diff.deletions}", style = MaterialTheme.typography.labelMedium) }
                                    OutlinedButton(onClick = { expanded = !expanded }, enabled = diff.changed) { Text(if (expanded) "Hide diff" else "Review diff") }
                                    if (expanded) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { diff.diff.forEach { line -> val prefix = when (line.kind) { NexusDiffKind.ADD -> "+"; NexusDiffKind.REMOVE -> "-"; NexusDiffKind.CONTEXT -> " " }; Text("$prefix${line.text}", style = MaterialTheme.typography.bodySmall) } } }
                                }
                                if (action.path != null && action.type != "delete_file" && (execution?.success == true || action.type == "open_file" || action.type == "focus_file")) OutlinedButton(onClick = { NexusEditorActionBus.request(workspace.id, action.path, true) }) { Icon(Icons.Outlined.OpenInNew, null); Text("Open in editor") }
                                when {
                                    execution?.success == true -> Text("Completed", color = MaterialTheme.colorScheme.primary)
                                    execution != null && !execution.success -> Text("Failed", color = MaterialTheme.colorScheme.error)
                                    proposal.status == NexusActionStatus.EXECUTING -> Text("Applying…")
                                    isMutating -> Text("Nexus applied this change automatically.", style = MaterialTheme.typography.bodySmall)
                                    proposal.status == NexusActionStatus.REJECTED -> Text("Rejected")
                                }
                            }
                        }
                    }
                    actionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) } }
        }

        if (input.startsWith("/")) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { NEXUS_COMMANDS.filter { it.startsWith(input.substringBefore(' '), ignoreCase = true) }.take(5).forEach { command -> FilterChip(selected = false, onClick = { input = "$command " }, label = { Text(command) }) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 150.dp), placeholder = { Text("Ask Nexus about your code…  @file  /command") }, maxLines = 6, enabled = !generating)
            IconButton(onClick = { if (generating) vm.stop() else { vm.send(input, chatContext); input = "" } }) { Icon(if (generating) Icons.Outlined.Stop else Icons.Outlined.Send, if (generating) "Pause" else "Send") }
        }
    }
}
