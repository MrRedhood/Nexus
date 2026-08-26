package com.mrredhood.nexus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.settings.ApiKeyStore
import com.mrredhood.nexus.core.workspace.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(project: NexusProject, workspace: Workspace, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tokenStore = remember { ApiKeyStore(context) }
    val fs = remember { WorkspaceFileSystem(context) }
    val service = remember { GitHubRepositoryService(fs) }
    val scope = rememberCoroutineScope()
    val repository = project.repository.orEmpty(); var branch by remember { mutableStateOf(project.branch.ifBlank { "main" }) }
    var status by remember { mutableStateOf<GitHubSyncStatus?>(null) }; var diffs by remember { mutableStateOf<List<GitDiff>>(emptyList()) }
    var branches by remember { mutableStateOf<List<GitBranch>>(emptyList()) }; var commits by remember { mutableStateOf<List<GitCommit>>(emptyList()) }
    var staged by remember { mutableStateOf(setOf<String>()) }; var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var info by remember { mutableStateOf<String?>(null) }
    var showCommit by remember { mutableStateOf(false) }; var selectedDiff by remember { mutableStateOf<GitDiff?>(null) }; var showBranches by remember { mutableStateOf(false) }; var showLog by remember { mutableStateOf(false) }; var showTerminal by remember { mutableStateOf(false) }; var showArtifacts by remember { mutableStateOf(false) }
    fun token(): String? = tokenStore.get("github")
    fun run(block: suspend (String) -> Unit) { val t=token(); if(t.isNullOrBlank()){error="Add a GitHub token in Settings > GitHub first.";return}; if(repository.isBlank()){error="This project has no GitHub repository configured.";return}; scope.launch{loading=true;error=null;runCatching{block(t)}.onFailure{error=it.message?:"Git operation failed"};loading=false} }
    fun refresh(){run{t->status=service.status(repository,branch,t,workspace);diffs=service.diff(repository,branch,t,workspace);staged=staged.filter{p->diffs.any{it.path==p}}.toSet()}}
    LaunchedEffect(repository,branch){if(repository.isNotBlank())refresh()}
    BackHandler{if(showTerminal)showTerminal=false else if(showArtifacts)showArtifacts=false else onBack()}
    if(showTerminal){TerminalScreen(project,workspace){showTerminal=false};return}; if(showArtifacts){ArtifactCenterScreen(project,workspace){showArtifacts=false};return}
    Scaffold(topBar={TopAppBar(title={Text("Source Control")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Back")}},actions={IconButton(onClick=::refresh){Icon(Icons.Outlined.Refresh,"Refresh")};IconButton(onClick={showBranches=true}){Icon(Icons.Outlined.AccountTree,"Branches")};IconButton(onClick={showLog=true}){Icon(Icons.Outlined.History,"History")};IconButton(onClick={showTerminal=true}){Icon(Icons.Outlined.Terminal,"Terminal")};IconButton(onClick={showArtifacts=true}){Icon(Icons.Outlined.Download,"Artifacts")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(repository.ifBlank{"No repository"},style=MaterialTheme.typography.titleMedium);Text("Branch · $branch");status?.let{Text("Remote ${it.remoteCommit.take(7)}")}}}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilledTonalButton(onClick=::refresh,enabled=!loading,modifier=Modifier.weight(1f)){Text("Refresh")};FilledTonalButton(onClick={run{t->service.fetch(repository,branch,t,workspace);info="Fetched $branch";refresh()}},enabled=!loading,modifier=Modifier.weight(1f)){Text("Fetch")}}
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth());error?.let{Text(it,color=MaterialTheme.colorScheme.error)};info?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
            status?.let{s->Text("Changes · ${s.changed.size} modified · ${s.added.size} added · ${s.deleted.size} deleted",style=MaterialTheme.typography.titleMedium)}
            if(diffs.isEmpty())Text("Working tree clean",color=MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){items(diffs,key={it.path}){d->
                Card(onClick={selectedDiff=d},Modifier.fillMaxWidth()){Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Checkbox(checked=d.path in staged,onCheckedChange={checked->staged=if(checked)staged+d.path else staged-d.path});Column(Modifier.weight(1f)){Text(d.path);Text("+${d.addedLines}  -${d.removedLines}",style=MaterialTheme.typography.labelMedium)}}}}
            }}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={staged=diffs.map{it.path}.toSet()},enabled=diffs.isNotEmpty(),modifier=Modifier.weight(1f)){Text("Stage All")};OutlinedButton(onClick={staged=emptySet()},enabled=staged.isNotEmpty(),modifier=Modifier.weight(1f)){Text("Unstage All")};Button(onClick={showCommit=true},enabled=staged.isNotEmpty()&&!loading,modifier=Modifier.weight(1f)){Text("Commit")}}
            Spacer(Modifier.height(4.dp))
        }
    }
    if(showCommit){var message by remember{mutableStateOf("Update from Nexus")};AlertDialog(onDismissRequest={showCommit=false},title={Text("Commit staged changes")},text={OutlinedTextField(message,{message=it},label={Text("Commit message")},singleLine=true)},confirmButton={TextButton(enabled=message.isNotBlank(),onClick={run{t->val r=service.commitAndPush(repository,branch,t,workspace,message,staged);staged=emptySet();info="Committed ${r.commitSha.take(7)}";showCommit=false;refresh()}}){Text("Commit & Push")}},dismissButton={TextButton(onClick={showCommit=false}){Text("Cancel")}})}
    selectedDiff?.let{d->AlertDialog(onDismissRequest={selectedDiff=null},title={Text(d.path)},text={LazyColumn{item{Text("+${d.addedLines}  -${d.removedLines}")};item{Spacer(Modifier.height(8.dp));Text(d.after.ifBlank{"File deleted"},style=MaterialTheme.typography.bodySmall)}}},confirmButton={TextButton(onClick={selectedDiff=null}){Text("Close")}})}
    if(showBranches){LaunchedEffect(Unit){run{t->branches=service.branches(repository,branch,t)}};AlertDialog(onDismissRequest={showBranches=false},title={Text("Branches")},text={LazyColumn{items(branches){b->TextButton(onClick={branch=b.name;showBranches=false}){Text(if(b.current)"✓ ${b.name}" else b.name)}}}},confirmButton={TextButton(onClick={showBranches=false}){Text("Close")}})}
    if(showLog){LaunchedEffect(Unit){run{t->commits=service.log(repository,branch,t)}};AlertDialog(onDismissRequest={showLog=false},title={Text("Commit history")},text={LazyColumn{items(commits){c->Column(Modifier.padding(vertical=6.dp)){Text("${c.sha.take(7)} · ${c.message}");Text("${c.author} · ${c.timestamp}",style=MaterialTheme.typography.labelSmall)}}}},confirmButton={TextButton(onClick={showLog=false}){Text("Close")}})}
}
