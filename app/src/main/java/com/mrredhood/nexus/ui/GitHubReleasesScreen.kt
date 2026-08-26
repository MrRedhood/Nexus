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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import com.mrredhood.nexus.core.workspace.GitHubRelease
import com.mrredhood.nexus.core.workspace.GitHubReleaseService
import kotlinx.coroutines.launch

@Composable
fun GitHubReleasesScreen(project: NexusProject, onBack: () -> Unit) {
    val context = LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val service = remember { GitHubReleaseService() }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty()
    var releases by remember { mutableStateOf<List<GitHubRelease>>(emptyList()) }
    var selected by remember { mutableStateOf<GitHubRelease?>(null) }
    var editing by remember { mutableStateOf<GitHubRelease?>(null) }
    var creating by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var includeDrafts by remember { mutableStateOf(true) }

    fun token() = tokenStore.get("github")
    fun refresh() {
        val accessToken = token() ?: run { error = "Add a GitHub token in Settings > GitHub first."; return }
        if (repository.isBlank()) { error = "Connect a GitHub repository to this project first."; return }
        scope.launch {
            loading = true; error = null
            runCatching { service.list(repository, accessToken, includeDrafts) }.onSuccess { releases = it }.onFailure { error = it.message }
            loading = false
        }
    }
    fun open(release: GitHubRelease) {
        val accessToken = token() ?: return
        scope.launch { loading = true; runCatching { service.get(repository, release.id, accessToken) }.onSuccess { selected = it }.onFailure { error = it.message }; loading = false }
    }

    LaunchedEffect(includeDrafts, repository) { if (repository.isNotBlank()) refresh() }
    BackHandler { when { selected != null -> selected = null; editing != null -> editing = null; creating -> creating = false; else -> onBack() } }

    Scaffold(topBar = { TopAppBar(title = { Text("Releases") }, navigationIcon = { TextButton(onClick = onBack) { Text("‹") } }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("GitHub releases", style = MaterialTheme.typography.titleLarge); Text(repository) }; Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Button(onClick = { creating = true }) { Text("New") }; OutlinedButton(onClick = ::refresh, enabled = !loading) { Text("Refresh") } } } }
            item { Row { Checkbox(includeDrafts, { includeDrafts = it }); Text("Include drafts", modifier = Modifier.padding(top = 12.dp)) } }
            if (loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (releases.isEmpty() && !loading) item { Text("No releases returned by GitHub.") }
            items(releases, key = { it.id }) { release -> Card(onClick = { open(release) }, modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(14.dp)) { Text(release.name, style = MaterialTheme.typography.titleMedium); Text("${release.tagName} · ${if (release.draft) "draft" else if (release.prerelease) "pre-release" else "published"}"); Text("${release.assets.size} assets"); if (release.body.isNotBlank()) Text(release.body.take(180), style = MaterialTheme.typography.bodySmall) } } }
        }
    }

    selected?.let { release ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(release.name) }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Tag: ${release.tagName}") }; item { Text("Target: ${release.targetCommitish}") }; item { Text(if (release.draft) "Draft" else if (release.prerelease) "Pre-release" else "Published") }
            if (release.body.isNotBlank()) item { Text(release.body) }
            item { Text("Assets", style = MaterialTheme.typography.titleMedium) }
            items(release.assets, key = { it.id }) { asset -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(modifier = Modifier.weight(1f)) { Text(asset.name); Text("${asset.sizeBytes / 1024} KB · ${asset.contentType}", style = MaterialTheme.typography.bodySmall) }; TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(asset.downloadUrl))) }) { Text("Open") } } }
        } }, confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { editing = release; selected = null }) { Text("Edit") }
            if (release.draft) TextButton(enabled = !loading, onClick = { val accessToken = token() ?: return@TextButton; scope.launch { loading = true; runCatching { service.publish(repository, release.id, accessToken) }.onSuccess { updated -> selected = updated; refresh() }.onFailure { error = it.message }; loading = false } }) { Text("Publish") }
            TextButton(enabled = !loading, onClick = { val accessToken = token() ?: return@TextButton; scope.launch { loading = true; runCatching { service.delete(repository, release.id, accessToken) }.onSuccess { selected = null; refresh() }.onFailure { error = it.message }; loading = false } }) { Text("Delete") }
            TextButton(onClick = { selected = null }) { Text("Done") }
        } })
    }

    if (creating) ReleaseEditorDialog(repository, null, service, token(), project.branch.ifBlank { "main" }, onDismiss = { creating = false }, onSaved = { creating = false; refresh() }, onError = { error = it })
    editing?.let { release -> ReleaseEditorDialog(repository, release, service, token(), project.branch.ifBlank { release.targetCommitish }, onDismiss = { editing = null }, onSaved = { editing = null; refresh() }, onError = { error = it }) }
}

@Composable
private fun ReleaseEditorDialog(repository: String, existing: GitHubRelease?, service: GitHubReleaseService, token: String?, defaultTarget: String, onDismiss: () -> Unit, onSaved: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var tag by remember(existing) { mutableStateOf(existing?.tagName.orEmpty()) }
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var body by remember(existing) { mutableStateOf(existing?.body.orEmpty()) }
    var target by remember(existing) { mutableStateOf(existing?.targetCommitish ?: defaultTarget) }
    var draft by remember(existing) { mutableStateOf(existing?.draft ?: true) }
    var prerelease by remember(existing) { mutableStateOf(existing?.prerelease ?: false) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "Create release" else "Edit release") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(tag, { tag = it }, label = { Text("Tag") }, singleLine = true); OutlinedTextField(name, { name = it }, label = { Text("Title") }, singleLine = true); OutlinedTextField(target, { target = it }, label = { Text("Target branch or commit") }, singleLine = true); OutlinedTextField(body, { body = it }, label = { Text("Release notes") }, minLines = 4)
        Row { Checkbox(draft, { draft = it }); Text("Draft", modifier = Modifier.padding(top = 12.dp)) }; Row { Checkbox(prerelease, { prerelease = it }); Text("Pre-release", modifier = Modifier.padding(top = 12.dp)) }; Spacer(Modifier.height(2.dp))
    } }, confirmButton = { TextButton(enabled = !saving && token?.isNotBlank() == true && tag.isNotBlank() && target.isNotBlank(), onClick = { scope.launch { saving = true; runCatching { if (existing == null) service.create(repository, token!!, tag, name.ifBlank { tag }, body, target, draft, prerelease) else service.update(repository, existing.id, token!!, tag, name.ifBlank { tag }, body, target, draft, prerelease) }.onSuccess { onSaved() }.onFailure { onError(it.message ?: "Release operation failed.") }; saving = false } }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
