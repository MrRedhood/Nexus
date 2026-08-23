package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun ChatScreen(project: NexusProject, workspace: Workspace, context: ChatContext = ChatContext(), onClose: (() -> Unit)? = null) {
    val vm: ChatViewModel = viewModel()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val usage by vm.tokenUsage.collectAsStateWithLifecycle()
    val contextSnapshot by vm.contextSnapshot.collectAsStateWithLifecycle()
    val proposals by vm.actionProposals.collectAsStateWithLifecycle()
    val reviews by vm.actionReviews.collectAsStateWithLifecycle()
    val actionMessage by vm.actionMessage.collectAsStateWithLifecycle()
    val liveContexts by ChatContextStore.contexts.collectAsStateWithLifecycle()
    val androidContext = LocalContext.current
    val contextService = remember(androidContext) { WorkspaceContextService(WorkspaceFileSystem(androidContext.applicationContext)) }
    var input by remember { mutableStateOf("") }
    var showContextInspector by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val workspaceContext = liveContexts[workspace.id] ?: context

    LaunchedEffect(workspace.id) { vm.open(workspace); runCatching { contextService.refresh(workspace) } }
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    if (showContextInspector && contextSnapshot != null) {
        AIContextInspector(snapshot = contextSnapshot!!, onDismiss = { showContextInspector = false })
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Nexus AI", style = MaterialTheme.typography.titleLarge)
                Text(project.name, style = MaterialTheme.typography.bodySmall)
                if (usage.total > 0) Text("Tokens: ${usage.total} (${usage.input} in / ${usage.output} out)", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = { showContextInspector = true }, enabled = contextSnapshot != null) {
                Text("Context")
            }
            IconButton(onClick = vm::clear, enabled = !generating) { Icon(Icons.Outlined.DeleteSweep, "Clear chat") }
            onClose?.let { IconButton(onClick = it) { Text("×", style = MaterialTheme.typography.titleLarge) } }
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message ->
                val assistant = message.role == "assistant"
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (assistant) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer)) {
                    Text(message.content.ifBlank { "…" }, Modifier.padding(14.dp))
                }
            }

            if (proposals.isNotEmpty()) {
                item {
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
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("+${review.additions}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                            Text("-${review.deletions}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                            Text(if (review.changed) "Changes ready for review" else "No changes", style = MaterialTheme.typography.labelMedium)
                                        }
                                        OutlinedButton(onClick = { expanded = !expanded }, enabled = review.changed) {
                                            Text(if (expanded) "Hide diff" else "Review diff")
                                        }
                                        if (expanded) {
                                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    review.diff.forEach { line ->
                                                        val prefix = when (line.kind) { NexusDiffKind.ADD -> "+"; NexusDiffKind.REMOVE -> "-"; NexusDiffKind.CONTEXT -> " " }
                                                        Text("$prefix${line.text}", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                }
                                            }
                                        }
                                    } else if (NexusActionPolicy.requiresApproval(action)) {
                                        Text("Preparing a safe change preview…", style = MaterialTheme.typography.bodySmall)
                                    }

                                    when (proposal.status) {
                                        NexusActionStatus.PROPOSED, NexusActionStatus.APPROVED -> {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (action.type == "open_file" || action.type == "focus_file") {
                                                    OutlinedButton(onClick = { vm.openAction(proposal.id) }) { Icon(Icons.Outlined.OpenInNew, null); Text("Open") }
                                                }
                                                if (NexusActionPolicy.requiresApproval(action)) {
                                                    Button(onClick = { vm.approveAction(proposal.id) }, enabled = review?.changed != false) { Text("Apply") }
                                                } else if (action.type != "open_file" && action.type != "focus_file") {
                                                    Button(onClick = { vm.approveAction(proposal.id) }) { Text("Approve") }
                                                }
                                                OutlinedButton(onClick = { vm.rejectAction(proposal.id) }) { Text("Reject") }
                                            }
                                        }
                                        NexusActionStatus.EXECUTING -> Text("Applying…", style = MaterialTheme.typography.bodySmall)
                                        NexusActionStatus.COMPLETED -> Text("Applied", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                        NexusActionStatus.REJECTED -> Text("Rejected", style = MaterialTheme.typography.bodySmall)
                                        NexusActionStatus.FAILED -> Text("Failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        actionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) } }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ask Nexus about your code…") }, maxLines = 5, enabled = !generating)
            IconButton(onClick = { if (generating) vm.stop() else { vm.send(input, workspaceContext); input = "" } }) {
                Icon(if (generating) Icons.Outlined.Stop else Icons.Outlined.Send, if (generating) "Stop" else "Send")
            }
        }
    }
}
