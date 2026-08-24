package com.mrredhood.nexus.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.core.build.GitHubArtifactService
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.settings.ApiKeyStore
import com.mrredhood.nexus.core.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactCenterScreen(project: NexusProject, workspace: Workspace, onBack: () -> Unit) {
    val context = LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val service = remember { GitHubArtifactService(context) }
    val scope = rememberCoroutineScope()
    var artifacts by remember { mutableStateOf<List<GitHubArtifactService.Artifact>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var downloadingId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        val repository = project.repository.orEmpty()
        val token = tokenStore.get("github")
        if (repository.isBlank()) { error = "This project has no GitHub repository configured."; return }
        if (token.isNullOrBlank()) { error = "Add a GitHub token in Settings > GitHub first."; return }
        scope.launch {
            loading = true; error = null
            runCatching { withContext(Dispatchers.IO) { service.listApkArtifacts(repository, token) } }
                .onSuccess { artifacts = it }
                .onFailure { error = it.message ?: "Unable to load GitHub artifacts" }
            loading = false
        }
    }

    fun download(artifact: GitHubArtifactService.Artifact) {
        val repository = project.repository.orEmpty()
        val token = tokenStore.get("github") ?: return
        scope.launch {
            downloadingId = artifact.id; error = null; info = null
            runCatching { withContext(Dispatchers.IO) { service.downloadAndExtract(repository, token, artifact) } }
                .onSuccess { apk ->
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apk.uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure { info = "APK downloaded, but Android could not open the installer: ${it.message}" }
                }
                .onFailure { error = it.message ?: "Artifact download failed" }
            downloadingId = null
        }
    }

    LaunchedEffect(project.repository) { refresh() }
    BackHandler(onBack = onBack)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Build Artifacts") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            actions = { IconButton(enabled = !loading, onClick = ::refresh) { Icon(Icons.Outlined.Refresh, "Refresh") } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Android, null, modifier = Modifier.size(28.dp))
                        Column { Text(project.name, style = MaterialTheme.typography.titleMedium); Text("GitHub Actions APKs", style = MaterialTheme.typography.bodySmall) }
                    }
                    Text("${artifacts.size} available build artifact${if (artifacts.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium)
                }
            }
            error?.let { message -> Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Warning, null); Text(message, color = MaterialTheme.colorScheme.error) } } }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (loading && artifacts.isEmpty()) CircularProgressIndicator()
            if (!loading && artifacts.isEmpty() && error == null) {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("No APK builds yet", style = MaterialTheme.typography.titleMedium); Text("Run the Nexus Android CI workflow from GitHub to create a debug or release APK.", style = MaterialTheme.typography.bodyMedium); TextButton(onClick = ::refresh) { Text("Check again") } } }
            }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(artifacts, key = { it.id }) { artifact ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(artifact.variant.replaceFirstChar { it.uppercase() } + " APK", style = MaterialTheme.typography.titleMedium); Text(formatBytes(artifact.sizeBytes), style = MaterialTheme.typography.bodySmall) }
                                if (downloadingId == artifact.id) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Button(enabled = !artifact.expired, onClick = { download(artifact) }) { Icon(Icons.Outlined.CloudDownload, null); Text("Get APK") }
                            }
                            Text("${artifact.branch.ifBlank { "main" }} · ${artifact.commitSha.take(7)}", style = MaterialTheme.typography.labelMedium)
                            Text("Run #${artifact.runId} · ${artifact.createdAt.replace('T', ' ').removeSuffix("Z")}", style = MaterialTheme.typography.bodySmall)
                            if (artifact.expired) Text("Expired", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                item { Spacer(Modifier.padding(bottom = 20.dp)) }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${DecimalFormat("0.0").format(bytes / 1024.0)} KB"
    return "${DecimalFormat("0.0").format(bytes / (1024.0 * 1024.0))} MB"
}
