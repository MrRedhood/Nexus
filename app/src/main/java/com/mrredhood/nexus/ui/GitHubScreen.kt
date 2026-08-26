package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.mrredhood.nexus.core.workspace.GitHubRepository
import com.mrredhood.nexus.core.workspace.GitHubRepositoryService
import com.mrredhood.nexus.core.workspace.GitHubWorkflow
import com.mrredhood.nexus.core.workspace.GitHubWorkflowRun
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubScreen(project: NexusProject, onBack: () -> Unit) {
    val context = LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val service = remember { GitHubRepositoryService(WorkspaceFileSystem(context)) }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty()
    var repositories by remember { mutableStateOf<List<GitHubRepository>>(emptyList()) }
    var workflows by remember { mutableStateOf<List<GitHubWorkflow>>(emptyList()) }
    var runs by remember { mutableStateOf<List<GitHubWorkflowRun>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedWorkflow by remember { mutableStateOf<GitHubWorkflow?>(null) }

    fun token() = tokenStore.get("github")
    fun loadRepositories() {
        val accessToken = token()
        if (accessToken.isNullOrBlank()) { error = "Add a GitHub token in Settings > GitHub first."; return }
        scope.launch {
            loading = true
            error = null
            runCatching { service.repositories(accessToken, query.ifBlank { null }) }
                .onSuccess { repositories = it }
                .onFailure { error = it.message }
            loading = false
        }
    }
    fun loadActions() {
        val accessToken = token() ?: run { error = "Add a GitHub token in Settings > GitHub first."; return }
        if (repository.isBlank()) { error = "Connect a GitHub repository to this project first."; return }
        scope.launch {
            loading = true
            error = null
            runCatching {
                workflows = service.workflows(repository, accessToken)
                runs = service.workflowRuns(repository, accessToken)
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadRepositories(); if (repository.isNotBlank()) loadActions() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("GitHub") },
            navigationIcon = { IconButton(onClick = onBack) { Text("‹") } }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search repositories") }, singleLine = true)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::loadRepositories, enabled = !loading) { Text("Search") }
                    Button(onClick = ::loadActions, enabled = !loading && repository.isNotBlank()) { Text("Refresh Actions") }
                }
            }
            if (loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message) } }
            item { Text("Repositories") }
            items(repositories, key = { it.fullName }) { repo ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(repo.fullName)
                        Text(repo.description ?: "No description")
                        Text("Default branch · ${repo.defaultBranch}")
                    }
                }
            }
            if (repository.isNotBlank()) {
                item { Text("Actions") }
                items(workflows, key = { it.id }) { workflow ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(workflow.name)
                            Text(workflow.path)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val accessToken = token() ?: return@Button
                                    scope.launch {
                                        loading = true
                                        runCatching { service.dispatchWorkflow(repository, workflow.id, accessToken, project.branch.ifBlank { "main" }) }.onFailure { error = it.message }
                                        runs = runCatching { service.workflowRuns(repository, accessToken) }.getOrDefault(runs)
                                        loading = false
                                    }
                                }, enabled = !loading) { Text("Run") }
                                TextButton(onClick = { selectedWorkflow = workflow }) { Text("Details") }
                            }
                        }
                    }
                }
                item { Text("Recent runs") }
                items(runs, key = { it.id }) { run ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${run.name} · ${run.status} ${run.conclusion ?: ""}")
                        Text("${run.branch} · ${run.sha.take(7)}")
                    }
                }
            }
        }
    }
    selectedWorkflow?.let { workflow ->
        AlertDialog(onDismissRequest = { selectedWorkflow = null }, title = { Text(workflow.name) }, text = { Text("Workflow: ${workflow.path}\nState: ${workflow.state}") }, confirmButton = { TextButton(onClick = { selectedWorkflow = null }) { Text("Close") } })
    }
}
