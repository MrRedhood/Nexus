package com.mrredhood.nexus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.mrredhood.nexus.core.workspace.GitHubPullRequest
import com.mrredhood.nexus.core.workspace.GitHubPullRequestFile
import com.mrredhood.nexus.core.workspace.GitHubPullRequestService
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
    val pullRequestService = remember { GitHubPullRequestService() }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty()
    var repositories by remember { mutableStateOf<List<GitHubRepository>>(emptyList()) }
    var workflows by remember { mutableStateOf<List<GitHubWorkflow>>(emptyList()) }
    var runs by remember { mutableStateOf<List<GitHubWorkflowRun>>(emptyList()) }
    var pullRequests by remember { mutableStateOf<List<GitHubPullRequest>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedWorkflow by remember { mutableStateOf<GitHubWorkflow?>(null) }
    var selectedPr by remember { mutableStateOf<GitHubPullRequest?>(null) }
    var reviewPr by remember { mutableStateOf<GitHubPullRequest?>(null) }
    var selectedPrFiles by remember { mutableStateOf<List<GitHubPullRequestFile>>(emptyList()) }
    var showCreatePr by remember { mutableStateOf(false) }
    var prState by remember { mutableStateOf("open") }
    var showIssues by remember { mutableStateOf(false) }
    var showChecks by remember { mutableStateOf(false) }

    fun token() = tokenStore.get("github")

    fun loadRepositories() {
        val accessToken = token() ?: run { error = "Add a GitHub token in Settings > GitHub first."; return }
        scope.launch {
            loading = true; error = null
            runCatching { service.repositories(accessToken, query.ifBlank { null }) }
                .onSuccess { repositories = it }.onFailure { error = it.message }
            loading = false
        }
    }

    fun loadActions() {
        val accessToken = token() ?: run { error = "Add a GitHub token in Settings > GitHub first."; return }
        if (repository.isBlank()) { error = "Connect a GitHub repository to this project first."; return }
        scope.launch {
            loading = true; error = null
            runCatching {
                workflows = service.workflows(repository, accessToken)
                runs = service.workflowRuns(repository, accessToken)
            }.onFailure { error = it.message }
            loading = false
        }
    }

    fun loadPullRequests() {
        val accessToken = token() ?: run { error = "Add a GitHub token in Settings > GitHub first."; return }
        if (repository.isBlank()) { error = "Connect a GitHub repository to this project first."; return }
        scope.launch {
            loading = true; error = null
            runCatching { pullRequestService.list(repository, accessToken, prState) }
                .onSuccess { pullRequests = it }.onFailure { error = it.message }
            loading = false
        }
    }

    fun openPullRequest(pr: GitHubPullRequest) {
        val accessToken = token() ?: return
        scope.launch {
            loading = true; error = null
            runCatching {
                selectedPr = pullRequestService.get(repository, pr.number, accessToken)
                selectedPrFiles = pullRequestService.files(repository, pr.number, accessToken)
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadRepositories()
        if (repository.isNotBlank()) { loadActions(); loadPullRequests() }
    }

    BackHandler {
        when {
            showChecks -> showChecks = false
            showIssues -> showIssues = false
            selectedPr != null -> selectedPr = null
            reviewPr != null -> reviewPr = null
            else -> onBack()
        }
    }

    if (showChecks) {
        GitHubChecksScreen(project = project, onBack = { showChecks = false })
        return
    }
    if (showIssues) {
        GitHubIssuesScreen(project = project, onBack = { showIssues = false })
        return
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("GitHub") },
            navigationIcon = { IconButton(onClick = onBack) { Text("‹") } },
            actions = {
                TextButton(onClick = { showChecks = true }) { Text("Checks") }
                TextButton(onClick = { showIssues = true }) { Text("Issues") }
            }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search repositories") }, singleLine = true) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::loadRepositories, enabled = !loading) { Text("Search") }
                    Button(onClick = ::loadActions, enabled = !loading && repository.isNotBlank()) { Text("Refresh Actions") }
                }
            }
            if (loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item { Text("Repositories", style = MaterialTheme.typography.titleMedium) }
            items(repositories, key = { it.fullName }) { repo ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(repo.fullName); Text(repo.description ?: "No description"); Text("Default branch · ${repo.defaultBranch}")
                    }
                }
            }

            if (repository.isNotBlank()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pull Requests", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { prState = if (prState == "open") "closed" else "open"; loadPullRequests() }, enabled = !loading) { Text(if (prState == "open") "Closed" else "Open") }
                            Button(onClick = { showCreatePr = true }, enabled = !loading) { Text("New PR") }
                        }
                    }
                }
                items(pullRequests, key = { it.number }) { pr ->
                    Card(onClick = { openPullRequest(pr) }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("#${pr.number} ${pr.title}")
                            Text("${pr.head} → ${pr.base} · ${pr.state}${if (pr.draft) " · draft" else ""}")
                            Text("${pr.author} · +${pr.additions} -${pr.deletions} · ${pr.changedFiles} files")
                        }
                    }
                }
                item { Text("Actions", style = MaterialTheme.typography.titleMedium) }
                items(workflows, key = { it.id }) { workflow ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(workflow.name); Text(workflow.path)
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
                item { Text("Recent runs", style = MaterialTheme.typography.titleMedium) }
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

    if (showCreatePr) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var head by remember { mutableStateOf(project.branch.ifBlank { "main" }) }
        var base by remember { mutableStateOf("main") }
        AlertDialog(
            onDismissRequest = { showCreatePr = false }, title = { Text("Create pull request") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(head, { head = it }, label = { Text("Head branch") }, singleLine = true)
                OutlinedTextField(base, { base = it }, label = { Text("Base branch") }, singleLine = true)
                OutlinedTextField(body, { body = it }, label = { Text("Description") }, minLines = 3)
            } },
            confirmButton = { TextButton(enabled = title.isNotBlank() && head.isNotBlank() && base.isNotBlank() && !loading, onClick = {
                val accessToken = token() ?: return@TextButton
                scope.launch {
                    loading = true
                    runCatching { pullRequestService.create(repository, head, base, title, body, accessToken) }
                        .onSuccess { created -> showCreatePr = false; selectedPr = created; selectedPrFiles = emptyList(); loadPullRequests() }
                        .onFailure { error = it.message }
                    loading = false
                }
            }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreatePr = false }) { Text("Cancel") } }
        )
    }

    selectedPr?.let { pr ->
        AlertDialog(
            onDismissRequest = { selectedPr = null }, title = { Text("#${pr.number} ${pr.title}") },
            text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("${pr.head} → ${pr.base}") }
                item { Text("State: ${pr.state}${if (pr.merged) " · merged" else ""}") }
                item { Text("Author: ${pr.author}") }
                item { Text("Changes: +${pr.additions} -${pr.deletions} · ${pr.changedFiles} files") }
                if (pr.body.isNotBlank()) item { Text(pr.body) }
                item { Text("Changed files") }
                items(selectedPrFiles, key = { it.path }) { file ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(file.path); Text("${file.status} · +${file.additions} -${file.deletions}")
                        if (!file.patch.isNullOrBlank()) { Text(file.patch.take(1200), style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(4.dp)) }
                    }
                }
            } },
            confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!pr.merged && pr.state == "open") {
                    TextButton(enabled = !loading, onClick = {
                        val accessToken = token() ?: return@TextButton
                        scope.launch {
                            loading = true
                            runCatching { pullRequestService.merge(repository, pr.number, accessToken) }.onSuccess { merged -> if (!merged) error = "GitHub did not merge the pull request." else { selectedPr = null; loadPullRequests() } }.onFailure { error = it.message }
                            loading = false
                        }
                    }) { Text("Merge") }
                    TextButton(enabled = !loading, onClick = {
                        val accessToken = token() ?: return@TextButton
                        scope.launch {
                            loading = true
                            runCatching { pullRequestService.update(repository, pr.number, accessToken, state = "closed") }.onSuccess { selectedPr = null; loadPullRequests() }.onFailure { error = it.message }
                            loading = false
                        }
                    }) { Text("Close") }
                }
                TextButton(enabled = !loading, onClick = { reviewPr = pr; selectedPr = null }) { Text("Review") }
                TextButton(onClick = { selectedPr = null }) { Text("Done") }
            } }
        )
    }

    reviewPr?.let { pr ->
        val accessToken = token()
        if (accessToken.isNullOrBlank()) {
            reviewPr = null
            error = "Add a GitHub token in Settings > GitHub first."
        } else {
            PullRequestReviewDialog(repository = repository, pullRequest = pr, token = accessToken, service = pullRequestService, onDismiss = { reviewPr = null }, onError = { error = it })
        }
    }
}
