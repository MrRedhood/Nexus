package com.mrredhood.nexus.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.nexus.core.ai.ChatContext
import com.mrredhood.nexus.core.ai.ChatContextStore
import com.mrredhood.nexus.core.ai.NexusActionExecutionRegistry
import com.mrredhood.nexus.core.ai.NexusActionReview
import com.mrredhood.nexus.core.ai.NexusActionStatus
import com.mrredhood.nexus.core.ai.NexusActionProposal
import com.mrredhood.nexus.core.ai.NexusDiffKind
import com.mrredhood.nexus.core.ai.QueuedChatMessage
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.workspace.Workspace
import kotlinx.coroutines.launch

private val NEXUS_COMMANDS = listOf("/explain", "/fix", "/refactor", "/optimize", "/test", "/build", "/search", "/open")

@Composable
fun ChatScreen(project: NexusProject, workspace: Workspace, context: ChatContext = ChatContext(), onClose: (() -> Unit)? = null) {
    val vm: ChatViewModel = viewModel()
    val settingsVm: AdvancedSettingsViewModel = viewModel()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val usage by vm.tokenUsage.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val proposals by vm.actionProposals.collectAsStateWithLifecycle()
    val reviews by vm.actionReviews.collectAsStateWithLifecycle()
    val executions by NexusActionExecutionRegistry.executions.collectAsStateWithLifecycle()
    val models by settingsVm.models.collectAsStateWithLifecycle()
    val featureSettings by settingsVm.settings.collectAsStateWithLifecycle()
    val loadingModels by settingsVm.loadingModels.collectAsStateWithLifecycle()
    val modelError by settingsVm.modelError.collectAsStateWithLifecycle()
    val androidContext = LocalContext.current
    val listState = rememberLazyListState()
    val liveContext = ChatContextStore.contexts.collectAsStateWithLifecycle().value[workspace.id] ?: context
    var chatContext by remember(workspace.id) { mutableStateOf(liveContext) }
    var input by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var copiedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(workspace.id, featureSettings.provider) { vm.open(workspace); settingsVm.loadModels(featureSettings.provider) }
    LaunchedEffect(liveContext) { chatContext = liveContext }
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    fun copyMessage(index: Int, content: String) {
        if (content.isBlank()) return
        val clipboard = androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nexus message", content))
        copiedIndex = index
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                if (usage.total > 0) Text("${usage.total} tokens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = vm::clear, enabled = !generating) { Icon(Icons.Outlined.DeleteSweep, "Clear chat") }
                onClose?.let { IconButton(onClick = it) { Text("×", style = MaterialTheme.typography.titleLarge) } }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                itemsIndexed(messages) { index, message ->
                    val assistant = message.role == "assistant"
                    val content = message.content
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (assistant) Alignment.CenterStart else Alignment.CenterEnd) {
                        Column(Modifier.fillMaxWidth(if (assistant) 0.94f else 0.84f).widthIn(max = 820.dp), horizontalAlignment = if (assistant) Alignment.Start else Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (assistant) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Text("N", Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                                    Text("Nexus", style = MaterialTheme.typography.labelMedium)
                                }
                                if (generating && index == messages.lastIndex) {
                                    NexusWorkingIndicator()
                                    if (content.isNotBlank()) Text(content, style = MaterialTheme.typography.bodyLarge)
                                } else Text(content.ifBlank { "Thinking…" }, style = MaterialTheme.typography.bodyLarge)
                            } else {
                                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Text(content, Modifier.padding(horizontal = 15.dp, vertical = 11.dp), style = MaterialTheme.typography.bodyLarge) }
                            }
                            if (content.isNotBlank() && !generating) {
                                Row {
                                    IconButton(onClick = { copyMessage(index, content) }) { Icon(Icons.Outlined.ContentCopy, "Copy") }
                                    if (!assistant) IconButton(onClick = { input = content }) { Icon(Icons.Outlined.Edit, "Edit message") }
                                    if (assistant) IconButton(onClick = { vm.regenerate(index, chatContext) }) { Icon(Icons.Outlined.Refresh, "Regenerate response") }
                                }
                                if (copiedIndex == index) Text("Copied", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (proposals.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth().widthIn(max = 820.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nexus actions", style = MaterialTheme.typography.titleMedium)
                        proposals.forEach { proposal -> ActionRow(proposal, reviews[proposal.id], executions[proposal.id], workspace, vm::approveAction, vm::rejectAction) }
                    }
                }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (input.startsWith("/")) {
                    Row(Modifier.fillMaxWidth().widthIn(max = 820.dp).align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NEXUS_COMMANDS.filter { it.startsWith(input.substringBefore(' '), ignoreCase = true) }.take(5).forEach { command -> Surface(onClick = { input = "$command " }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Text(command, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium) } }
                    }
                    Spacer(Modifier.heightIn(min = 5.dp))
                }
                if (queue.isNotEmpty()) QueuePreview(queue, vm::removeQueuedMessage, vm::clearQueue)
                Surface(Modifier.fillMaxWidth().widthIn(max = 820.dp).align(Alignment.CenterHorizontally), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            IconButton(onClick = { showTools = !showTools }) { Icon(Icons.Outlined.Tune, "Tools") }
                            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f).heightIn(min = 46.dp, max = 150.dp), placeholder = { Text("Message Nexus…") }, maxLines = 6, enabled = true, shape = RoundedCornerShape(22.dp), colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.surface, focusedBorderColor = MaterialTheme.colorScheme.surface))
                            Surface(onClick = { if (generating) { if (input.isNotBlank()) { vm.send(input, chatContext); input = "" } else vm.stop() } else if (input.isNotBlank()) { vm.send(input, chatContext); input = "" } }, enabled = generating || input.isNotBlank(), shape = CircleShape, color = if (generating && input.isBlank()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurface) { Box(Modifier.padding(10.dp)) { Icon(if (generating && input.isBlank()) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward, if (generating && input.isBlank()) "Stop" else "Queue message", tint = if (generating && input.isBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface) } }
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(onClick = { showModelMenu = true }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Text(featureSettings.model.ifBlank { "Choose model" }, Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                                if (loadingModels) DropdownMenuItem(text = { Text("Loading models…") }, onClick = {})
                                else if (models.isEmpty()) DropdownMenuItem(text = { Text(modelError ?: "No models available") }, onClick = { settingsVm.loadModels(featureSettings.provider) })
                                else models.take(100).forEach { model -> DropdownMenuItem(text = { Column { Text(model.name); Text(model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, onClick = { settingsVm.update { it.copy(model = model.id, apiKeyConfigured = settingsVm.hasApiKey(model.provider)) }; showModelMenu = false }) }
                            }
                            if (showTools) Text("  ·  @file  ·  /commands", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Nexus can make mistakes. Check important code changes.", Modifier.fillMaxWidth().widthIn(max = 820.dp).padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QueuePreview(items: List<QueuedChatMessage>, onRemove: (String) -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().widthIn(max = 820.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Queued messages · ${items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.DeleteSweep, "Clear queue") }
        }
        items.take(4).forEach { item: QueuedChatMessage ->
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(start = 11.dp, end = 4.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { onRemove(item.id) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Outlined.Close, "Remove queued message") }
                }
            }
        }
        if (items.size > 4) Text("+${items.size - 4} more queued", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NexusWorkingIndicator() {
    val transition = rememberInfiniteTransition(label = "nexus-working")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "rotation")
    val pulse by transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Text("⚒", Modifier.padding(horizontal = 8.dp, vertical = 6.dp).rotate(rotation), style = MaterialTheme.typography.labelLarge) }
        Text("Nexus is working…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = pulse))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(3) { i -> Surface(Modifier.size(5.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = if (i == 0) pulse else 0.35f)) {} } }
    }
}
