package com.mrredhood.nexus.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mrredhood.nexus.core.workspace.GitHubIssue
import com.mrredhood.nexus.core.workspace.GitHubIssueComment
import com.mrredhood.nexus.core.workspace.GitHubIssueService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubIssuesScreen(project: NexusProject, onBack: () -> Unit) {
    val context = LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val service = remember { GitHubIssueService() }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty()

    var state by remember { mutableStateOf("open") }
    var issues by remember { mutableStateOf<List<GitHubIssue>>(emptyList()) }
    var selected by remember { mutableStateOf<GitHubIssue?>(null) }
    var comments by remember { mutableStateOf<List<GitHubIssueComment>>(emptyList()) }
    var composer by remember { mutableStateOf("") }
    var createTitle by remember { mutableStateOf("") }
    var createBody by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }
    var editBody by remember { mutableStateOf("") }
    var editLabels by remember { mutableStateOf("") }
    var editAssignees by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    fun token() = tokenStore.get("github")

    fun load() {
        val accessToken = token()
        if (accessToken.isNullOrBlank()) { error = "Add a GitHub token in Settings > GitHub first."; return }
        if (repository.isBlank()) { error = "Connect a GitHub repository to this project first."; return }
        scope.launch {
            loading = true; error = null
            runCatching { service.list(repository, accessToken, state) }
                .onSuccess { issues = it }
                .onFailure { error = it.message ?: "Could not load GitHub issues." }
            loading = false
        }
    }

    fun openIssue(issue: GitHubIssue) {
        val accessToken = token() ?: run { error = "Add a GitHub token in Settings > GitHub first."; return }
        scope.launch {
            loading = true; error = null
            runCatching {
                selected = service.get(repository, issue.number, accessToken)
                comments = service.comments(repository, issue.number, accessToken)
            }.onFailure { error = it.message ?: "Could not load the issue." }
            loading = false
        }
    }

    fun sendComment() {
        val issue = selected ?: return
        val accessToken = token() ?: return
        if (composer.isBlank()) return
        scope.launch {
            sending = true; error = null
            runCatching { service.addComment(repository, issue.number, composer, accessToken) }
                .onSuccess { comment -> comments = comments + comment; composer = "" }
                .onFailure { error = it.message ?: "Could not post the comment." }
            sending = false
        }
    }

    fun beginEdit(issue: GitHubIssue) {
        editTitle = issue.title
        editBody = issue.body
        editLabels = issue.labels.joinToString(", ")
        editAssignees = issue.assignees.joinToString(", ")
        showEdit = true
    }

    fun saveEdit(issue: GitHubIssue) {
        val accessToken = token() ?: return
        scope.launch {
            sending = true; error = null
            runCatching {
                service.update(
                    repository,
                    issue.number,
                    accessToken,
                    title = editTitle,
                    body = editBody,
                    labels = editLabels.split(',').map { it.trim() }.filter { it.isNotBlank() },
                    assignees = editAssignees.split(',').map { it.trim() }.filter { it.isNotBlank() }
                )
            }.onSuccess { updated ->
                selected = updated
                issues = issues.map { if (it.number == updated.number) updated else it }
                showEdit = false
            }.onFailure { error = it.message ?: "Could not update the issue." }
            sending = false
        }
    }

    LaunchedEffect(repository, state) { load() }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Issues") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                actions = { IconButton(onClick = ::load, enabled = !loading) { Icon(Icons.Outlined.Refresh, "Refresh") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("GitHub Issues", style = MaterialTheme.typography.titleLarge)
                    Text(repository.ifBlank { "No repository connected" }, style = MaterialTheme.typography.bodyMedium)
                    Text("Live repository issues with create, edit, close, reopen, labels, assignees, and comments.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { state = "open" }, enabled = state != "open" && !loading) { Text("Open") }
                OutlinedButton(onClick = { state = "closed" }, enabled = state != "closed" && !loading) { Text("Closed") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { showCreate = true }, enabled = !loading && repository.isNotBlank()) { Text("New issue") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (issues.isEmpty() && !loading) item {
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("No $state issues", style = MaterialTheme.typography.titleMedium); Text("GitHub returned no issues for this repository and state.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) } }
                }
                items(issues, key = { it.number }) { issue ->
                    Card(onClick = { openIssue(issue) }, Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("#${issue.number} ${issue.title}", style = MaterialTheme.typography.titleMedium)
                            Text("${issue.author} · ${issue.comments} comments · ${issue.state}", style = MaterialTheme.typography.bodySmall)
                            if (issue.labels.isNotEmpty()) Text(issue.labels.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                            if (issue.assignees.isNotEmpty()) Text("Assigned: ${issue.assignees.joinToString()}", style = MaterialTheme.typography.labelMedium)
                            if (issue.body.isNotBlank()) Text(issue.body.trim(), maxLines = 3, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { if (!sending) showCreate = false },
            title = { Text("Create issue") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(createTitle, { createTitle = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(createBody, { createBody = it }, label = { Text("Description") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                if (sending) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(); Text("Creating on GitHub…") }
            } },
            confirmButton = {
                TextButton(enabled = createTitle.isNotBlank() && !sending, onClick = {
                    val accessToken = token() ?: return@TextButton
                    scope.launch {
                        sending = true; error = null
                        runCatching { service.create(repository, createTitle, createBody, accessToken) }
                            .onSuccess { created -> showCreate = false; createTitle = ""; createBody = ""; state = "open"; issues = listOf(created) + issues }
                            .onFailure { error = it.message ?: "Could not create the issue." }
                        sending = false
                    }
                }) { Icon(Icons.Outlined.Send, null); Text("Create") }
            },
            dismissButton = { TextButton(enabled = !sending, onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    if (showEdit) {
        val issue = selected
        if (issue != null) {
            AlertDialog(
                onDismissRequest = { if (!sending) showEdit = false },
                title = { Text("Edit #${issue.number}") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(editTitle, { editTitle = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editBody, { editBody = it }, label = { Text("Description") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editLabels, { editLabels = it }, label = { Text("Labels (comma separated)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editAssignees, { editAssignees = it }, label = { Text("Assignees (GitHub usernames)") }, modifier = Modifier.fillMaxWidth())
                } },
                confirmButton = { TextButton(enabled = editTitle.isNotBlank() && !sending, onClick = { saveEdit(issue) }) { Text("Save") } },
                dismissButton = { TextButton(enabled = !sending, onClick = { showEdit = false }) { Text("Cancel") } }
            )
        }
    }

    selected?.let { issue ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("#${issue.number} ${issue.title}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${issue.author} · ${issue.state}", style = MaterialTheme.typography.labelMedium)
                if (issue.labels.isNotEmpty()) Text("Labels: ${issue.labels.joinToString()}", style = MaterialTheme.typography.bodySmall)
                if (issue.assignees.isNotEmpty()) Text("Assignees: ${issue.assignees.joinToString()}", style = MaterialTheme.typography.bodySmall)
                if (issue.body.isNotBlank()) Text(issue.body)
                Text("Conversation", style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(comments, key = { it.id }) { comment ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) { Text(comment.author, style = MaterialTheme.typography.labelMedium); Text(comment.body, style = MaterialTheme.typography.bodySmall) } }
                    }
                }
                OutlinedTextField(composer, { composer = it }, label = { Text("Reply to this issue") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = !sending, onClick = { beginEdit(issue) }) { Icon(Icons.Outlined.Edit, null); Text("Edit") }
                    if (issue.state == "open") TextButton(enabled = !sending, onClick = {
                        val accessToken = token() ?: return@TextButton
                        scope.launch {
                            sending = true
                            runCatching { service.update(repository, issue.number, accessToken, state = "closed") }
                                .onSuccess { selected = null; load() }
                                .onFailure { error = it.message }
                            sending = false
                        }
                    }) { Text("Close") } else TextButton(enabled = !sending, onClick = {
                        val accessToken = token() ?: return@TextButton
                        scope.launch {
                            sending = true
                            runCatching { service.update(repository, issue.number, accessToken, state = "open") }
                                .onSuccess { updated -> selected = updated; load() }
                                .onFailure { error = it.message }
                            sending = false
                        }
                    }) { Text("Reopen") }
                    issue.htmlUrl?.let { url -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Icon(Icons.Outlined.OpenInNew, null); Text("Open") } }
                    TextButton(enabled = composer.isNotBlank() && !sending, onClick = ::sendComment) { Text("Send") }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Done") } }
        )
    }
}
