@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mrredhood.nexus

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.workspace.EntryType
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceEntry
import com.mrredhood.nexus.ui.NexusViewModel
import com.mrredhood.nexus.ui.NexusViewModelFactory
import com.mrredhood.nexus.ui.SettingsScreen
import com.mrredhood.nexus.ui.SettingsViewModel
import com.mrredhood.nexus.ui.WorkspaceViewModel
import com.mrredhood.nexus.ui.WorkspaceViewModelFactory
import com.mrredhood.nexus.ui.theme.NexusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NexusTheme {
                val vm: NexusViewModel = viewModel(factory = NexusViewModelFactory(applicationContext))
                NexusApp(vm)
            }
        }
    }
}

@Composable
private fun NexusApp(vm: NexusViewModel) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val settingsVm: SettingsViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var pickerCallback by remember { mutableStateOf<((Uri?) -> Unit)?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> pickerCallback?.invoke(uri); pickerCallback = null }
    fun chooseFolder(callback: (Uri?) -> Unit) { pickerCallback = callback; picker.launch(null) }
    val project = projects.firstOrNull { it.id == selectedProjectId }
    val workspace = project?.let { p -> workspaces.firstOrNull { it.projectId == p.id } }

    if (showSettings) {
        SettingsScreen(settings, settingsVm::update) { showSettings = false }
        return
    }

    Surface(Modifier.fillMaxSize()) {
        when {
            project == null -> HomeScreen(projects, vm, ::chooseFolder, { selectedProjectId = it }, { showSettings = true })
            workspace == null -> MissingWorkspaceScreen(project, vm, ::chooseFolder) { selectedProjectId = null }
            else -> WorkspaceScreen(project, workspace) { selectedProjectId = null }
        }
    }
}

@Composable
private fun HomeScreen(projects: List<NexusProject>, vm: NexusViewModel, chooseFolder: ((Uri?) -> Unit) -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    var create by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Nexus") }, actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }; IconButton(onClick = { create = true }) { Icon(Icons.Outlined.Add, "Create project") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) { Column(Modifier.padding(20.dp)) { Text("Workspace", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); Text("Select a folder and Nexus will persist access through Android's Storage Access Framework. No broad storage permission is required.") } } }
            item { Text("Projects", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            if (projects.isEmpty()) item { Card(shape = MaterialTheme.shapes.large) { Column(Modifier.padding(20.dp)) { Text("No projects yet", style = MaterialTheme.typography.titleMedium); Text("Create a project and choose its workspace folder.", modifier = Modifier.padding(top = 6.dp)); FilledTonalButton(onClick = { create = true }, modifier = Modifier.padding(top = 14.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Create project") } } } }
            items(projects, key = { it.id }) { project -> Card(onClick = { onOpen(project.id) }, shape = MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(project.name, style = MaterialTheme.typography.titleMedium); Text(project.repository ?: "Local workspace", style = MaterialTheme.typography.bodyMedium); Text("Branch · ${project.branch}", style = MaterialTheme.typography.labelMedium) }; IconButton(onClick = { vm.deleteProject(project.id) }) { Icon(Icons.Outlined.Delete, "Delete project") } } } }
            item { Card(shape = MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { Icon(Icons.Outlined.Settings, null); Column { Text("Nexus settings", style = MaterialTheme.typography.titleMedium); Text("Appearance, editor, workspace, AI, agents, safety, terminal and CI preferences", style = MaterialTheme.typography.bodySmall) } } }; Spacer(Modifier.height(20.dp)) }
        }
    }
    if (create) CreateProjectDialog(chooseFolder, { create = false }) { name, repository, uri -> vm.createProject(name, repository, uri, DocumentFile.fromTreeUri(context, uri)?.name ?: name) { create = false } }
}

@Composable
private fun CreateProjectDialog(chooseFolder: ((Uri?) -> Unit) -> Unit, onDismiss: () -> Unit, onCreate: (String, String?, Uri) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var repository by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var folderName by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create project") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(repository, { repository = it }, label = { Text("GitHub repository (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Card(shape = MaterialTheme.shapes.large) { Column(Modifier.fillMaxWidth().padding(14.dp)) { Text("Workspace folder", style = MaterialTheme.typography.titleSmall); Text(folderName.ifBlank { "No folder selected" }, style = MaterialTheme.typography.bodyMedium); FilledTonalButton(onClick = { chooseFolder { selected -> uri = selected; folderName = selected?.let { DocumentFile.fromTreeUri(context, it)?.name ?: "Selected folder" } ?: "" } }, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Choose folder") } } } } }, confirmButton = { FilledTonalButton(enabled = name.isNotBlank() && uri != null, onClick = { uri?.let { onCreate(name.trim(), repository.trim().ifBlank { null }, it) } }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun MissingWorkspaceScreen(project: NexusProject, vm: NexusViewModel, chooseFolder: ((Uri?) -> Unit) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text(project.name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }) }) { padding -> Card(Modifier.fillMaxWidth().padding(padding).padding(16.dp), shape = MaterialTheme.shapes.large) { Column(Modifier.padding(20.dp)) { Text("Workspace unavailable", style = MaterialTheme.typography.titleLarge); Text("Nexus no longer has access to the selected folder. Reconnect the project to a folder.", modifier = Modifier.padding(top = 8.dp)); FilledTonalButton(onClick = { chooseFolder { uri -> if (uri != null) vm.attachWorkspace(project, uri, DocumentFile.fromTreeUri(context, uri)?.name ?: project.name) } }, modifier = Modifier.padding(top = 14.dp)) { Text("Reconnect workspace") } } } }
}

@Composable
private fun WorkspaceScreen(project: NexusProject, workspace: Workspace, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: WorkspaceViewModel = viewModel(factory = WorkspaceViewModelFactory(context))
    val entries by vm.entries.collectAsStateWithLifecycle()
    val currentPath by vm.currentPath.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val editorContent by vm.editorContent.collectAsStateWithLifecycle()
    val editorPath by vm.editorPath.collectAsStateWithLifecycle()
    val editorDirty by vm.editorDirty.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val openDocuments by vm.openDocuments.collectAsStateWithLifecycle()
    var createDialog by remember { mutableStateOf<String?>(null) }
    var nameDialog by remember { mutableStateOf<NameAction?>(null) }
    var destinationDialog by remember { mutableStateOf<DestinationAction?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var discardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(workspace.id) { vm.open(workspace) }

    BackHandler {
        if (editorContent != null) {
            if (editorDirty) discardDialog = true else vm.clearEditor()
        } else if (!vm.back(workspace)) onBack()
    }

    if (editorContent != null && editorPath != null) {
        EditorScreen(editorPath!!, editorContent!!, editorDirty, saving, openDocuments, { path -> vm.read(workspace, path) }, { path -> vm.closeEditor(workspace, path) }, vm::updateEditorContent, { vm.saveEditor(workspace) }, { if (editorDirty) discardDialog = true else vm.clearEditor() })
        if (discardDialog) AlertDialog(onDismissRequest = { discardDialog = false }, title = { Text("Unsaved changes") }, text = { Text("This file has unsaved changes. Discard them and close the editor?") }, confirmButton = { FilledTonalButton(onClick = { discardDialog = false; vm.clearEditor() }) { Text("Discard") } }, dismissButton = { TextButton(onClick = { discardDialog = false }) { Text("Keep editing") } })
        return
    }

    val segments = currentPath.split('/').filter { it.isNotBlank() }
    val breadcrumbPaths = buildList { add(""); var path = ""; segments.forEach { segment -> path = if (path.isBlank()) segment else "$path/$segment"; add(path) } }

    Scaffold(topBar = { TopAppBar(title = { Text(project.name) }, navigationIcon = { IconButton(onClick = { if (!vm.back(workspace)) onBack() }) { Icon(Icons.Outlined.ArrowBack, "Back") } }, actions = { IconButton(onClick = { vm.refresh(workspace) }) { Icon(Icons.Outlined.Refresh, "Refresh") }; IconButton(onClick = { createDialog = "file" }) { Icon(Icons.Outlined.Add, "Create file") }; IconButton(onClick = { createDialog = "folder" }) { Icon(Icons.Outlined.CreateNewFolder, "Create folder") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { breadcrumbPaths.forEachIndexed { index, path -> if (index > 0) Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(18.dp)); TextButton(onClick = { vm.navigateTo(workspace, path) }) { Text(if (index == 0) workspace.displayName else segments[index - 1], maxLines = 1) } } } }
            if (currentPath.isNotBlank()) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilledTonalButton(onClick = { vm.up(workspace) }) { Icon(Icons.Outlined.ArrowUpward, null); Spacer(Modifier.width(6.dp)); Text("Parent") } }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries, key = { it.relativePath }) { entry -> EntryCard(entry, { if (entry.type == EntryType.DIRECTORY) vm.enter(workspace, entry.relativePath) else vm.read(workspace, entry.relativePath) }, { action -> when (action) { FileAction.RENAME -> nameDialog = NameAction("Rename", entry); FileAction.COPY -> destinationDialog = DestinationAction("Copy", entry, false); FileAction.MOVE -> destinationDialog = DestinationAction("Move", entry, true); FileAction.DELETE -> deleteTarget = entry } }) }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
    createDialog?.let { type -> NameDialog(if (type == "folder") "New folder" else "New file", "Create", { createDialog = null }) { name -> if (type == "folder") vm.createDirectory(workspace, name) else vm.createFile(workspace, name); createDialog = null } }
    nameDialog?.let { action -> NameDialog(action.title, "Rename", { nameDialog = null }, action.entry.name) { name -> vm.rename(workspace, action.entry.relativePath, name); nameDialog = null } }
    destinationDialog?.let { action -> DestinationDialog(action.title, action.entry, { destinationDialog = null }) { destination -> if (action.move) vm.move(workspace, action.entry.relativePath, destination) else vm.copy(workspace, action.entry.relativePath, destination); destinationDialog = null } }
    deleteTarget?.let { entry -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Delete ${if (entry.type == EntryType.DIRECTORY) "folder" else "file"}?") }, text = { Text("This permanently deletes ${entry.relativePath}. This action cannot be undone.") }, confirmButton = { FilledTonalButton(onClick = { vm.delete(workspace, entry.relativePath); deleteTarget = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }) }
    error?.let { message -> AlertDialog(onDismissRequest = vm::clearError, title = { Text("Workspace error") }, text = { Text(message) }, confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } }) }
}

enum class FileAction { RENAME, COPY, MOVE, DELETE }
data class NameAction(val title: String, val entry: WorkspaceEntry)
data class DestinationAction(val title: String, val entry: WorkspaceEntry, val move: Boolean)

@Composable
private fun EntryCard(entry: WorkspaceEntry, onOpen: () -> Unit, onAction: (FileAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onOpen, shape = MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { Icon(if (entry.type == EntryType.DIRECTORY) Icons.Outlined.Folder else Icons.Outlined.Description, null); Column(Modifier.weight(1f)) { Text(entry.name, style = MaterialTheme.typography.titleMedium); Text(if (entry.type == EntryType.DIRECTORY) "Folder" else formatBytes(entry.sizeBytes), style = MaterialTheme.typography.bodySmall) }; Box { IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "File actions") }; DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onAction(FileAction.RENAME) }); DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menu = false; onAction(FileAction.COPY) }); DropdownMenuItem(text = { Text("Move") }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { menu = false; onAction(FileAction.MOVE) }); HorizontalDivider(); DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onAction(FileAction.DELETE) }) } } } }
}

@Composable
private fun NameDialog(title: String, confirm: String, onDismiss: () -> Unit, initial: String = "", onConfirm: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }, confirmButton = { FilledTonalButton(enabled = value.trim().isNotEmpty(), onClick = { onConfirm(value.trim()) }) { Text(confirm) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun DestinationDialog(title: String, entry: WorkspaceEntry, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(entry.relativePath) { mutableStateOf(entry.relativePath) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Enter the destination path relative to the workspace root.", style = MaterialTheme.typography.bodySmall); OutlinedTextField(value, { value = it }, label = { Text("Destination path") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Text("Example: src/${entry.name}", style = MaterialTheme.typography.labelSmall) } }, confirmButton = { FilledTonalButton(enabled = value.trim().isNotEmpty(), onClick = { onConfirm(value.trim()) }) { Text(title) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun EditorScreen(path: String, content: String, dirty: Boolean, saving: Boolean, documents: List<com.mrredhood.nexus.core.editor.EditorDocument>, onActivate: (String) -> Unit, onClose: (String) -> Unit, onChange: (String) -> Unit, onSave: () -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Column { Text(path.substringAfterLast('/')); Text(if (dirty) "Unsaved changes" else "Saved", style = MaterialTheme.typography.labelSmall) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Close editor") } }, actions = { IconButton(enabled = dirty && !saving, onClick = onSave) { if (saving) CircularProgressIndicator(modifier = Modifier.padding(4.dp)) else Icon(Icons.Outlined.Save, "Save") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (documents.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    documents.forEach { document ->
                        val active = document.relativePath == path
                        Surface(shape = MaterialTheme.shapes.large, color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(onClick = { onActivate(document.relativePath) }) { Text((if (document.isDirty) "• " else "") + document.name, maxLines = 1) }
                                IconButton(onClick = { onClose(document.relativePath) }) { Icon(Icons.Outlined.Close, "Close ${document.name}", modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxSize().padding(10.dp), shape = MaterialTheme.shapes.large) { OutlinedTextField(value = content, onValueChange = onChange, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), singleLine = false, label = { Text(path) }) }
        }
    }
}

private fun formatBytes(value: Long): String = when { value < 1024 -> "$value B"; value < 1024 * 1024 -> "${value / 1024} KB"; value < 1024 * 1024 * 1024 -> "${value / (1024 * 1024)} MB"; else -> "${value / (1024 * 1024 * 1024)} GB" }
