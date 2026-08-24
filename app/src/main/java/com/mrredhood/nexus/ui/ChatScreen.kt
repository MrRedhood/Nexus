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
import com.mrredhood.nexus.core.ai.NexusActionPolicy
import com.mrredhood.nexus.core.ai.NexusActionStatus
import com.mrredhood.nexus.core.ai.NexusDiffKind
import com.mrredhood.nexus.core.ai.WorkspaceContextService
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem

private val NEXUS_COMMANDS = listOf("/explain", "/fix", "/refactor", "/optimize", "/test", "/build", "/search", "/open")
private val MODEL_FILTERS = listOf("All", "Free", "Premium", "Image", "Video", "Audio")

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
    val actionMessage by vm.actionMessage.collectAsStateWithLifecycle()
    val liveContexts by ChatContextStore.contexts.collectAsStateWithLifecycle()
    val featureSettings by settingsVm.settings.collectAsStateWithLifecycle()
    val models by settingsVm.models.collectAsStateWithLifecycle()
    val loadingModels by settingsVm.loadingModels.collectAsStateWithLifecycle()
    val modelError by settingsVm.modelError.collectAsStateWithLifecycle()
    val androidContext = LocalContext.current
    val contextService = remember(androidContext) { WorkspaceContextService(WorkspaceFileSystem(androidContext.applicationContext)) }
    var input by remember { mutableStateOf("") }
    var showContextInspector by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var modelFilter by remember { mutableStateOf("All") }
    var copiedMessageIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val workspaceContext = liveContexts[workspace.id] ?: context

    LaunchedEffect(workspace.id, featureSettings.provider) {
        vm.open(workspace)
        runCatching { contextService.refresh(workspace) }
        settingsVm.loadModels(featureSettings.provider)
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    if (showContextInspector && contextSnapshot != null) {
        AIContextInspector(snapshot = contextSnapshot!!, onDismiss = { showContextInspector = false })
    }

    val filteredModels = models.filter { model ->
        when (modelFilter) {
            "Free" -> model.pricing.free
            "Premium" -> model.premium
            "Image" -> model.image
            "Video" -> model.video
            "Audio" -> model.audio
            else -> true
        }
    }

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
                    Column(Modifier.weight(1f)) {
                        Text("Nexus AI", style = MaterialTheme.typography.titleLarge)
                        Text(project.name, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = MaterialTheme.shapes.small) {
                            Text("${featureSettings.provider} · ${featureSettings.model}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = vm::clear, enabled = !generating) { Icon(Icons.Outlined.DeleteSweep, "Clear chat") }
                        onClose?.let { IconButton(onClick = it) { Text("×", style = MaterialTheme.typography.titleLarge) } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showContextInspector = true }, enabled = contextSnapshot != null) { Text("Context") }
                    FilterChip(selected = true, onClick = { showModelMenu = true }, label = { Text(if (loadingModels) "Loading models…" else "${filteredModels.size} models") })
                    DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MODEL_FILTERS.forEach { filter -> FilterChip(selected = modelFilter == filter, onClick = { modelFilter = filter }, label = { Text(filter) }) }
                        }
                        if (filteredModels.isEmpty()) {
                            DropdownMenuItem(text = { Text(modelError ?: if (loadingModels) "Loading…" else "No matching models") }, onClick = { if (!loadingModels) settingsVm.loadModels(featureSettings.provider) })
                        } else {
                            filteredModels.take(100).forEach { model ->
                                DropdownMenuItem(text = { Column { Text(model.name); Text(model.id, style = MaterialTheme.typography.labelSmall) } }, onClick = { settingsVm.update { it.copy(model = model.id, apiKeyConfigured = settingsVm.hasApiKey(model.provider)) }; showModelMenu = false })
                            }
                        }
                    }
                    if (usage.total > 0) Text("${usage.total} tokens", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
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
                            if (message.content.isNotBlank() && !generating) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(onClick = { copyMessage(index, message.content) }) { Icon(Icons.Outlined.ContentCopy, if (copiedMessageIndex == index) "Copied" else "Copy") }
                                    if (!assistant) {
                                        IconButton(onClick = { input = message.content }) { Icon(Icons.Outlined.Edit, "Edit message") }
                                    } else if (previousUser != null) {
                                        IconButton(onClick = { vm.regenerate(index, workspaceContext) }) { Icon(Icons.Outlined.Refresh, "Regenerate response") }
                                    }
                                }
                            }
                        }
                        Text(message.content.ifBlank { "Thinking…" })
                        if (copiedMessageIndex == index) Text("Copied to clipboard", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (proposals.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nexus actions", style = MaterialTheme.typography.titleMedium)
                    proposals.forEach { proposal ->
                        val action = proposal.action
                        val review = reviews[proposal.id]
                        var expanded by remember(proposal.id, review?.proposed) { mutableStateOf(false) }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(action.type.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                                action.path?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                if (review != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text("+${review.additions}", style = MaterialTheme.typography.labelMedium); Text("-${review.deletions}", style = MaterialTheme.typography.labelMedium); Text(if (review.changed) "Changes ready" else "No changes", style = MaterialTheme.typography.labelMedium) }
                                    OutlinedButton(onClick = { expanded = !expanded }, enabled = review.changed) { Text(if (expanded) "Hide diff" else "Review diff") }
                                    if (expanded) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { review.diff.forEach { line -> val prefix = when (line.kind) { NexusDiffKind.ADD -> "+"; NexusDiffKind.REMOVE -> "-"; NexusDiffKind.CONTEXT -> " " }; Text("$prefix${line.text}", style = MaterialTheme.typography.bodySmall) } } }
                                } else if (NexusActionPolicy.requiresApproval(action)) Text("Preparing a safe change preview…", style = MaterialTheme.typography.bodySmall)
                                when (proposal.status) {
                                    NexusActionStatus.PROPOSED, NexusActionStatus.APPROVED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (action.type == "open_file" || action.type == "focus_file") OutlinedButton(onClick = { vm.openAction(proposal.id) }) { Icon(Icons.Outlined.OpenInNew, null); Text("Open") }
                                        if (NexusActionPolicy.requiresApproval(action)) Button(onClick = { vm.approveAction(proposal.id) }, enabled = review?.changed != false) { Text("Apply") }
                                        else if (action.type != "open_file" && action.type != "focus_file") Button(onClick = { vm.approveAction(proposal.id) }) { Text("Approve") }
                                        OutlinedButton(onClick = { vm.rejectAction(proposal.id) }) { Text("Reject") }
                                    }
                                    NexusActionStatus.EXECUTING -> Text("Applying…")
                                    NexusActionStatus.COMPLETED -> Text("Applied", color = MaterialTheme.colorScheme.primary)
                                    NexusActionStatus.REJECTED -> Text("Rejected")
                                    NexusActionStatus.FAILED -> Text("Failed", color = MaterialTheme.colorScheme.error)
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
            IconButton(onClick = { if (generating) vm.stop() else { vm.send(input, workspaceContext); input = "" } }) { Icon(if (generating) Icons.Outlined.Stop else Icons.Outlined.Send, if (generating) "Stop" else "Send") }
        }
    }
}
