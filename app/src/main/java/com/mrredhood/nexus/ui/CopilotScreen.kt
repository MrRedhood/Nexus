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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.settings.ApiKeyStore
import com.mrredhood.nexus.core.workspace.CopilotAgentTask
import com.mrredhood.nexus.core.workspace.GitHubCopilotAgentService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopilotScreen(project: NexusProject, onBack: () -> Unit) {
    val context = LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val service = remember { GitHubCopilotAgentService() }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty()

    var prompt by remember { mutableStateOf("") }
    var baseBranch by remember { mutableStateOf(project.branch.ifBlank { "main" }) }
    var model by remember { mutableStateOf(GitHubCopilotAgentService.AUTO_MODEL) }
    var createPullRequest by remember { mutableStateOf(false) }
    var tasks by remember { mutableStateOf<List<CopilotAgentTask>>(emptyList()) }
    var selectedTask by remember { mutableStateOf<CopilotAgentTask?>(null) }
    var continueTask by remember { mutableStateOf<CopilotAgentTask?>(null) }
    var modelMenu by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun token(): String? = tokenStore.get("github")

    fun loadTasks() {
        val accessToken = token()
        if (accessToken.isNullOrBlank()) {
            error = "Add a GitHub token in Settings > GitHub first."
            return
        }
        if (repository.isBlank()) {
            error = "This project has no GitHub repository configured."
            return
        }
        scope.launch {
            loading = true
            error = null
            runCatching { service.listTasks(repository, accessToken) }
                .onSuccess { tasks = it }
                .onFailure { error = it.message ?: "Could not load Copilot tasks." }
            loading = false
        }
    }

    fun sendTask(headRef: String? = null, message: String = prompt) {
        val accessToken = token()
        if (accessToken.isNullOrBlank()) {
            error = "Add a GitHub token in Settings > GitHub first."
            return
        }
        if (repository.isBlank()) {
            error = "Connect this project to a GitHub repository first."
            return
        }
        if (message.isBlank()) return
        scope.launch {
            loading = true
            error = null
            info = null
            runCatching {
                service.startTask(
                    repository = repository,
                    token = accessToken,
                    prompt = message,
                    baseRef = baseBranch,
                    headRef = headRef,
                    model = model,
                    createPullRequest = createPullRequest
                )
            }.onSuccess { task ->
                tasks = listOf(task) + tasks.filterNot { it.id == task.id }
                selectedTask = task
                prompt = ""
                continueTask = null
                info = "Copilot accepted the task. It is now working on GitHub-hosted compute."
            }.onFailure { error = it.message ?: "Could not start Copilot." }
            loading = false
        }
    }

    LaunchedEffect(repository) { if (repository.isNotBlank()) loadTasks() }

    LaunchedEffect(tasks.map { it.id to it.state }) {
        while (tasks.any { it.state == "queued" || it.state == "in_progress" || it.state == "waiting_for_user" }) {
            delay(5000)
            val accessToken = token() ?: break
            runCatching { service.listTasks(repository, accessToken) }.onSuccess { tasks = it }
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub Copilot") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(enabled = !loading, onClick = ::loadTasks) {
                        Icon(Icons.Outlined.Refresh, "Refresh tasks")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Copilot agent", style = MaterialTheme.typography.titleLarge)
                    Text(repository.ifBlank { "No GitHub repository connected" })
                    Text(
                        "Real cloud-agent tasks can research, plan, edit code, run checks, push branches and create pull requests.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            if (repository.isBlank()) {
                Text("Connect a GitHub repository before starting a Copilot task.", color = MaterialTheme.colorScheme.error)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = baseBranch,
                    onValueChange = { baseBranch = it },
                    label = { Text("Base branch") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { modelMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(model) }
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        GitHubCopilotAgentService.SUPPORTED_MODELS.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { model = option; modelMenu = false })
                        }
                    }
                }
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = createPullRequest, onCheckedChange = { createPullRequest = it })
                Text("Create a pull request when the task finishes")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Research the repository and explain the architecture",
                    "Create an implementation plan for the requested feature",
                    "Review the repository for bugs and security problems",
                    "Run the tests, diagnose failures, and fix them"
                ).forEach { preset ->
                    FilledTonalButton(onClick = { prompt = preset }, enabled = !loading) {
                        Text(preset.substringBefore(" "))
                    }
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Ask Copilot to work on this repository") },
                placeholder = { Text("Implement, debug, refactor, test, review, document...") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { sendTask() },
                enabled = !loading && prompt.isNotBlank() && repository.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Send, null)
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text("Send to Copilot")
            }

            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Text("Recent Copilot tasks", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { task ->
                    Card(onClick = { selectedTask = task }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(task.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Text(task.state, style = MaterialTheme.typography.labelMedium)
                            }
                            task.sessions.lastOrNull()?.let { session ->
                                Text(session.prompt, maxLines = 3, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                Text("${session.headRef ?: "new branch"} → ${session.baseRef ?: baseBranch} · ${session.model ?: "auto"}", style = MaterialTheme.typography.labelSmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                task.htmlUrl?.let {
                                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }) { Icon(Icons.Outlined.OpenInNew, null); Text("Open") }
                                }
                                if (task.pullRequestNumber != null) {
                                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$repository/pull/${task.pullRequestNumber}"))) }) { Text("PR #${task.pullRequestNumber}") }
                                }
                                if (task.sessions.lastOrNull()?.headRef != null && task.state != "queued" && task.state != "in_progress") {
                                    TextButton(onClick = { continueTask = task }) { Text("Continue") }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    selectedTask?.let { task ->
        AlertDialog(
            onDismissRequest = { selectedTask = null },
            title = { Text(task.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("State: ${task.state}")
                    Text("Task ID: ${task.id}", style = MaterialTheme.typography.bodySmall)
                    task.sessions.forEachIndexed { index, session ->
                        Text("Session ${index + 1}: ${session.state}", style = MaterialTheme.typography.titleSmall)
                        Text(session.prompt)
                        Text("${session.headRef ?: "new branch"} → ${session.baseRef ?: "base"} · ${session.model ?: "auto"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTask = null }) { Text("Close") }
            }
        )
    }

    continueTask?.let { task ->
        val session = task.sessions.lastOrNull()
        AlertDialog(
            onDismissRequest = { continueTask = null },
            title = { Text("Continue Copilot work") },
            text = {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Follow-up instruction") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = prompt.isNotBlank() && !loading && session?.headRef != null,
                    onClick = { sendTask(headRef = session?.headRef, message = prompt) }
                ) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { continueTask = null }) { Text("Cancel") } }
        )
    }
}
