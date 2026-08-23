package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.core.ai.AIContextItem
import com.mrredhood.nexus.core.ai.AIContextSnapshot
import com.mrredhood.nexus.core.ai.AIContextSource

@Composable
fun AIContextInspector(
    snapshot: AIContextSnapshot,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("AI Context", style = MaterialTheme.typography.headlineSmall)
                        Text("What Nexus prepared for this request", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                }

                val usage = if (snapshot.tokenLimit == 0) 0f else
                    (snapshot.estimatedTokens.toFloat() / snapshot.tokenLimit).coerceIn(0f, 1f)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${snapshot.estimatedTokens} / ${snapshot.tokenLimit} tokens", style = MaterialTheme.typography.titleMedium)
                        Text("${(usage * 100).toInt()}% context usage · ${snapshot.includedItems.size} included · ${snapshot.droppedItems.size} dropped", style = MaterialTheme.typography.bodySmall)
                        if (snapshot.truncated) Text("Context was truncated to stay within limits.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                snapshot.items.forEach { item -> ContextItemCard(item, included = true) }

                if (snapshot.droppedItems.isNotEmpty()) {
                    Text("Excluded", style = MaterialTheme.typography.titleMedium)
                    snapshot.droppedItems.forEach { item -> ContextItemCard(item, included = false) }
                }

                var showPrompt by remember { mutableStateOf(false) }
                Button(onClick = { showPrompt = !showPrompt }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showPrompt) "Hide context preview" else "Show assembled context")
                }
                if (showPrompt) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Text(
                            snapshot.asPromptContext(),
                            Modifier
                                .padding(12.dp)
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextItemCard(item: AIContextItem, included: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (included) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sourceTitle(item.source), style = MaterialTheme.typography.titleSmall)
                Text(if (included) "Included" else "Dropped", style = MaterialTheme.typography.labelMedium)
            }
            Text(item.label, style = MaterialTheme.typography.bodyMedium)
            item.path?.takeIf { it.isNotBlank() && it != item.label }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("~${item.estimatedTokens} tokens", style = MaterialTheme.typography.labelSmall)
            item.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun sourceTitle(source: AIContextSource): String = when (source) {
    AIContextSource.USER_MESSAGE -> "User request"
    AIContextSource.SELECTION -> "Selection"
    AIContextSource.CURRENT_FILE -> "Current file"
    AIContextSource.REFERENCED_FILE -> "Referenced file"
    AIContextSource.RELATED_FILE -> "Related file"
    AIContextSource.GIT_DIFF -> "Git diff"
    AIContextSource.TERMINAL_OUTPUT -> "Terminal output"
    AIContextSource.WORKSPACE_SUMMARY -> "Workspace summary"
    AIContextSource.MEMORY -> "Memory"
}
