package com.mrredhood.nexus.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.nexus.core.ai.ChatContext
import com.mrredhood.nexus.core.ai.ChatContextStore
import com.mrredhood.nexus.core.ai.NexusActionExecutionRegistry
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
fun ChatScreen(
    project: NexusProject,
    workspace: Workspace,
    context: ChatContext = ChatContext(),
    onClose: (() -> Unit)? = null
) {
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
    var showTools by remember { mutableStateOf(false) }
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // Minimal top bar: model-first rather than a large Material card header.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("N", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column {
                        Text("Nexus", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${featureSettings.provider} · ${featureSettings.model}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        Surface(
                            onClick = { showModelMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text("Change", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                MODEL_FILTERS.forEach { filter ->
                                    Surface(
                                        onClick = { modelFilter = filter },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (modelFilter == filter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(filter, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            if (filteredModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(modelError ?: if (loadingModels) "Loading…" else "No matching models") },
                                    onClick = { if (!loadingModels) settingsVm.loadModels(featureSettings.provider) }
                                )
                            } else {
                                filteredModels.take(100).forEach { model ->
                                    DropdownMenuItem(
                                        text = { Column { Text(model.name); Text(model.id, style = MaterialTheme.typography.labelSmall) } },
                                        onClick = {
                                            settingsVm.update { it.copy(model = model.id, apiKeyConfigured = settingsVm.hasApiKey(model.provider)) }
                                            showModelMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (usage.total > 0) Text("${usage.total} tokens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = vm::clear, enabled = !generating) { Icon(Icons.Outlined.DeleteSweep, "Clear chat") }
                    onClose?.let { IconButton(onClick = it) { Text("×", style = MaterialTheme.typography.titleLarge) } }
                }
            }

            // The conversation itself is intentionally borderless. User turns float as small bubbles;
            // assistant turns use the full reading width like modern AI chat clients.
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Column(Modifier.fillMaxWidth().widthIn(max = 820.dp).padding(horizontal = 4.dp)) {
                        ChatContextAttachmentPanel(
                            workspace = workspace,
                            fileSystem = fileSystem,
                            context = chatContext,
                            onContextChanged = { chatContext = it }
                        )
                    }
                }

                workspaceChanges?.takeIf { it.changes.isNotEmpty() }?.let { changes ->
                    item {
                        ChatWorkspaceChanges(
                            changes = changes,
                            workspaceId = workspace.id
                        )
                    }
                }

                itemsIndexed(messages) { index, message ->
                    val assistant = message.role == "assistant"
                    val previousUser = if (assistant) messages.subList(0, index).lastOrNull { it.role == "user" } else null
                    val content = message.content

                    Box(Modifier.fillMaxWidth(), contentAlignment = if (assistant) Alignment.CenterStart else Alignment.CenterEnd) {
                        Column(
                            Modifier.fillMaxWidth(if (assistant) 0.94f else 0.84f).widthIn(max = 820.dp),
                            horizontalAlignment = if (assistant) Alignment.Start else Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (assistant) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("N", Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text("Nexus", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (assistant) {
                                Text(content.ifBlank { "Thinking…" }, style = MaterialTheme.typography.bodyLarge)
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(content, Modifier.padding(horizontal = 15.dp, vertical = 11.dp), style = MaterialTheme.typography.bodyLarge)
                                }
                            }

                            if (content.isNotBlank() && !generating) {
                                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                    IconButton(onClick = { copyMessage(index, content) }) {
                                        Icon(Icons.Outlined.ContentCopy, if (copiedMessageIndex == index) "Copied" else "Copy")
                                    }
                                    if (!assistant) {
                                        IconButton(onClick = { input = content }) { Icon(Icons.Outlined.Edit, "Edit message") }
                                    } else if (previousUser != null) {
                                        IconButton(onClick = { vm.regenerate(index, chatContext) }) { Icon(Icons.Outlined.Refresh, "Regenerate response") }
                                    }
                                }
                            }
                            if (copiedMessageIndex == index) {
                                Text("Copied", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                if (proposals.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().widthIn(max = 820.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Nexus actions", style = MaterialTheme.typography.titleMedium)
                            if (successfulChanges.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Text("$createdCount created")
                                        Text("$modifiedCount modified")
                                        Text("$deletedCount deleted")
                                        Text("+$addedLines")
                                        Text("-$removedLines")
                                    }
                                }
                            }
                            proposals.forEach { proposal ->
                                ChatActionCard(
                                    proposal = proposal,
                                    review = reviews[proposal.id],
                                    execution = executions[proposal.id],
                                    workspaceId = workspace.id,
                                    onToggleDiff = {},
                                    onOpenEditor = { path -> NexusEditorActionBus.request(workspace.id, path, true) }
                                )
                            }
                            actionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }

                error?.let { message ->
                    item {
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                    }
                }
            }

            // Floating composer, deliberately detached from the old outlined-text-field/card stack.
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (input.startsWith("/")) {
                    Row(
                        Modifier.fillMaxWidth().widthIn(max = 820.dp).align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NEXUS_COMMANDS.filter { it.startsWith(input.substringBefore(' '), ignoreCase = true) }.take(5).forEach { command ->
                            Surface(
                                onClick = { input = "$command " },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(command, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(Modifier.heightIn(min = 5.dp))
                }

                Surface(
                    Modifier.fillMaxWidth().widthIn(max = 820.dp).align(Alignment.CenterHorizontally),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            IconButton(onClick = { showTools = !showTools }) {
                                Icon(Icons.Outlined.Tune, "Tools and context")
                            }
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f).heightIn(min = 46.dp, max = 150.dp),
                                placeholder = { Text("Message Nexus…") },
                                maxLines = 6,
                                enabled = !generating,
                                shape = RoundedCornerShape(22.dp),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                                    focusedBorderColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            Surface(
                                onClick = { if (generating) vm.stop() else if (input.isNotBlank()) { vm.send(input, chatContext); input = "" } },
                                enabled = generating || input.isNotBlank(),
                                shape = CircleShape,
                                color = if (generating) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurface
                            ) {
                                Box(Modifier.padding(10.dp)) {
                                    Icon(
                                        if (generating) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward,
                                        if (generating) "Pause" else "Send",
                                        tint = if (generating) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
                                    )
                                }
                            }
                        }
                        if (showTools) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Surface(onClick = { showContextInspector = true }, enabled = contextSnapshot != null, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("Context", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                                }
                                Surface(onClick = { showModelMenu = true }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("Model", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                                }
                                Text("@file · /commands", Modifier.padding(horizontal = 5.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(
                    "Nexus can make mistakes. Check important code changes.",
                    Modifier.fillMaxWidth().widthIn(max = 820.dp).align(Alignment.CenterHorizontally).padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChatWorkspaceChanges(
    changes: com.mrredhood.nexus.core.workspace.WorkspaceChangeSummary,
    workspaceId: String
) {
    Surface(
        Modifier.fillMaxWidth().widthIn(max = 820.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Workspace changes", style = MaterialTheme.typography.titleSmall)
                Text("${changes.created} created · ${changes.modified} modified · ${changes.deleted} deleted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("+${changes.additions} lines · -${changes.deletions} lines", style = MaterialTheme.typography.labelMedium)
            changes.changes.forEach { change ->
                Surface(
                    onClick = { if (change.status != WorkspaceChangeStatus.DELETED) NexusEditorActionBus.request(workspaceId, change.relativePath, true) },
                    enabled = change.status != WorkspaceChangeStatus.DELETED,
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (change.status) { WorkspaceChangeStatus.CREATED -> "C"; WorkspaceChangeStatus.MODIFIED -> "M"; WorkspaceChangeStatus.DELETED -> "D" },
                            style = MaterialTheme.typography.labelLarge,
                            color = when (change.status) { WorkspaceChangeStatus.CREATED -> MaterialTheme.colorScheme.primary; WorkspaceChangeStatus.MODIFIED -> MaterialTheme.colorScheme.tertiary; WorkspaceChangeStatus.DELETED -> MaterialTheme.colorScheme.error }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(change.relativePath, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("+${change.additions} / -${change.deletions} lines", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (change.status != WorkspaceChangeStatus.DELETED) Icon(Icons.Outlined.OpenInNew, "Open in editor")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatActionCard(
    proposal: com.mrredhood.nexus.core.ai.NexusActionProposal,
    review: com.mrredhood.nexus.core.ai.NexusActionReview?,
    execution: com.mrredhood.nexus.core.ai.NexusActionExecutionSummary?,
    workspaceId: String,
    onToggleDiff: () -> Unit,
    onOpenEditor: (String) -> Unit
) {
    var expanded by remember(proposal.id, review?.proposed) { mutableStateOf(false) }
    val action = proposal.action
    val isMutating = action.type in MUTATING_ACTION_TYPES
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(action.type.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                    action.path?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Icon(Icons.Outlined.MoreHoriz, "AI action details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            execution?.let { result ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (result.success) "Applied" else "Failed", style = MaterialTheme.typography.labelMedium, color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    if (result.additions > 0 || result.deletions > 0) Text("+${result.additions} / -${result.deletions} lines", style = MaterialTheme.typography.labelMedium)
                }
                Text(result.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            review?.let { diff ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("+${diff.additions}", style = MaterialTheme.typography.labelMedium)
                    Text("-${diff.deletions}", style = MaterialTheme.typography.labelMedium)
                    if (diff.changed) {
                        Surface(onClick = { expanded = !expanded; onToggleDiff() }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(if (expanded) "Hide diff" else "Review diff", Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (expanded) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            diff.diff.forEach { line ->
                                val prefix = when (line.kind) { NexusDiffKind.ADD -> "+"; NexusDiffKind.REMOVE -> "-"; NexusDiffKind.CONTEXT -> " " }
                                Text("$prefix${line.text}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (action.path != null && action.type != "delete_file" && (execution?.success == true || action.type == "open_file" || action.type == "focus_file")) {
                Surface(onClick = { onOpenEditor(action.path) }, shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.OpenInNew, null)
                        Text("Open in editor", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            when {
                execution?.success == true -> Text("Completed", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                execution != null && !execution.success -> Text("Failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                proposal.status == NexusActionStatus.EXECUTING -> Text("Applying…", style = MaterialTheme.typography.labelSmall)
                isMutating -> Text("Nexus applied this change automatically.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                proposal.status == NexusActionStatus.REJECTED -> Text("Rejected", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
