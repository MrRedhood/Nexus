@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mrredhood.nexus

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var pickerCallback by remember { mutableStateOf<((Uri?) -> Unit)?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> pickerCallback?.invoke(uri); pickerCallback = null }
    fun chooseFolder(callback: (Uri?) -> Unit) { pickerCallback = callback; picker.launch(null) }
    val project = projects.firstOrNull { it.id == selectedProjectId }
    val workspace = project?.let { p -> workspaces.firstOrNull { it.projectId == p.id } }
    Surface(Modifier.fillMaxSize()) {
        when {
            project == null -> HomeScreen(projects, vm, ::chooseFolder) { selectedProjectId = it }
            workspace == null -> MissingWorkspaceScreen(project, vm, ::chooseFolder) { selectedProjectId = null }
            else -> WorkspaceScreen(project, workspace) { selectedProjectId = null }
        }
    }
}

@Composable
private fun HomeScreen(projects: List<NexusProject>, vm: NexusViewModel, chooseFolder: ((Uri?) -> Unit) -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var create by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Nexus") }, actions = { IconButton(onClick = { create = true }) { Icon(Icons.Outlined.Add, "Create project") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) { Column(Modifier.padding(20.dp)) { Text("Workspace", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); Text("Select a folder and Nexus will persist access through Android's Storage Access Framework. No broad storage permission is required.") } } }
            item { Text("Projects", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            if (projects.isEmpty()) item { Card(shape = MaterialTheme.shapes.large) { Column(Modifier.padding(20.dp)) { Text("No projects yet", style = MaterialTheme.typography.titleMedium); Text("Create a project and choose its workspace folder.", modifier = Modifier.padding(top = 6.dp)); FilledTonalButton(onClick = { create = true }, modifier = Modifier.padding(top = 14.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.padding(3.dp)); Text("Create project") } } } }
            items(projects, key = { it.id }) { project -> Card(onClick = { onOpen(project.id) }, shape = MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(project.name, style = MaterialTheme.typography.titleMedium); Text(project.repository ?: "Local workspace", style = MaterialTheme.typography.bodyMedium); Text("Branch · ${project.branch}", style = MaterialTheme.typography.labelMedium) }; IconButton(onClick = { vm.deleteProject(project.id) }) { Icon(Icons.Outlined.Delete, "Delete project") } } } }
            item { Card(shape = MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { Icon(Icons.Outlined.Settings, null); Column { Text("Foundation", style = MaterialTheme.typography.titleMedium); Text("Material 3 · API 36 · SAF · persistent workspace metadata", style = MaterialTheme.typography.bodySmall) } } }; Spacer(Modifier.height(20.dp)) }
        }
    }
    if (create) CreateProjectDialog(chooseFolder, { create = false }) { name, repository, uri -> vm.createProject(name, repository, uri, DocumentFile.fromTreeUri(context, uri)?.name ?: name) { create = false } }
}

@Composable
private fun CreateProjectDialog(chooseFolder: ((Uri?) -> Unit) -> Unit, onDismiss: () -> Unit, onCreate: (String, String?, Uri) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }; var repository by remember { mutableStateOf("") }; var uri by remember { mutableStateOf<Uri?>(null) }; var folderName by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create project") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(repository, { repository = it }, label = { Text("GitHub repository (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Card(shape = MaterialTheme.shapes.large) { Column(Modifier.fillMaxWidth().padding(14.dp)) { Text("Workspace folder", style = MaterialTheme.typography.titleSmall); Text(folderName.ifBlank { "No folder selected" }, style = MaterialTheme.typography.bodyMedium); FilledTonalButton(onClick = { chooseFolder { selected -> uri = selected; folderName = selected?.let { DocumentFile.fromTreeUri(context, it)?.name ?: "Selected folder" } ?: "" } }, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.padding(3.dp)); Text("Choose folder") } } } } }, confirmButton = { FilledTonalButton(enabled = name.isNotBlank() && uri != null, onClick = { uri?.let { onCreate(name.trim(), repository.trim().ifBlank { null }, it) } }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
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
    val entries by vm.entries.collectAsStateWithLifecycle(); val currentPath by vm.currentPath.collectAsStateWithLifecycle(); val loading by vm.loading.collectAsStateWithLifecycle(); val error by vm.error.collectAsStateWithLifecycle(); val editorContent by vm.editorContent.collectAsStateWithLifecycle(); val editorPath by vm.editorPath.collectAsStateWithLifecycle(); val editorDirty by vm.editorDirty.collectAsStateWithLifecycle(); val saving by vm.saving.collectAsStateWithLifecycle()
    var createDialog by remember { mutableStateOf<String?>(null) }; var discardDialog by remember { mutableStateOf(false) }
    LaunchedEffect(workspace.id) { vm.open(workspace) }
    if (editorContent != null && editorPath != null) {
        EditorScreen(editorPath!!, editorContent!!, editorDirty, saving, vm::updateEditorContent, { vm.saveEditor(workspace) }, { if (editorDirty) discardDialog = true else vm.clearEditor() })
        if (discardDialog) AlertDialog(onDismissRequest = { discardDialog = false }, title = { Text("Unsaved changes") }, text = { Text("This file has unsaved changes. Discard them and close the editor?") }, confirmButton = { FilledTonalButton(onClick = { discardDialog = false; vm.clearEditor() }) { Text("Discard") } }, dismissButton = { TextButton(onClick = { discardDialog = false }) { Text("Keep editing") } })
        return
    }
    Scaffold(topBar = { TopAppBar(title = { Column { Text(project.name); Text(if (currentPath.isBlank()) workspace.displayName else currentPath, style = MaterialTheme.typography.labelSmall) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }, actions = { IconButton(onClick = { vm.refresh(workspace) }) { Icon(Icons.Outlined.Refresh, "Refresh") }; IconButton(onClick = { createDialog = "file" }) { Icon(Icons.Outlined.Add, "Create file") }; IconButton(onClick = { createDialog = "folder" }) { Icon(Icons.Outlined.CreateNewFolder, "Create folder") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (currentPath.isNotBlank()) FilledTonalButton(onClick = { vm.up(workspace) }, modifier = Modifier.padding(vertical = 8.dp)) { Icon(Icons.Outlined.ArrowBack, null); Spacer(Modifier.padding(3.dp)); Text("Parent") }
            if (loading) CircularProgressIndicator(Modifier.padding(16.dp))
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(entries, key = { it.relativePath }) { entry -> EntryCard(entry, { if (entry.type == EntryType.DIRECTORY) vm.enter(workspace, entry.relativePath) else vm.read(workspace, entry.relativePath) }, { vm.delete(workspace, entry.relativePath) }) }; item { Spacer(Modifier.height(20.dp)) } }
        }
    }
    createDialog?.let { type -> NameDialog(if (type == "folder") "New folder" else "New file", { createDialog = null }) { name -> if (type == "folder") vm.createDirectory(workspace, name) else vm.createFile(workspace, name); createDialog = null } }
    error?.let { message -> AlertDialog(onDismissRequest = vm::clearError, title = { Text("Workspace error") }, text = { Text(message) }, confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } }) }
}

@Composable
private fun EditorScreen(path: String, content: String, dirty: Boolean, saving: Boolean, onChange: (String) -> Unit, onSave: () -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Column { Text(path.substringAfterLast('/')); Text(if (dirty) "Unsaved changes" else "Saved", style = MaterialTheme.typography.labelSmall) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Close editor") } }, actions = { IconButton(enabled = dirty && !saving, onClick = onSave) { if (saving) CircularProgressIndicator(modifier = Modifier.padding(4.dp)) else Icon(Icons.Outlined.Save, "Save") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(10.dp)) { Card(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large) { OutlinedTextField(value = content, onValueChange = onChange, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), singleLine = false, label = { Text(path) }) } }
    }
}

@Composable
private fun EntryCard(entry: WorkspaceEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onOpen, shape = MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { Icon(if (entry.type == EntryType.DIRECTORY) Icons.Outlined.Folder else Icons.Outlined.Description, null); Column(Modifier.weight(1f)) { Text(entry.name, style = MaterialTheme.typography.titleMedium); Text(if (entry.type == EntryType.DIRECTORY) "Folder" else formatBytes(entry.sizeBytes), style = MaterialTheme.typography.bodySmall) }; IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") } } }
}

@Composable
private fun NameDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) { var value by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text("Name") }, singleLine = true) }, confirmButton = { FilledTonalButton(enabled = value.trim().isNotEmpty(), onClick = { onConfirm(value.trim()) }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }

private fun formatBytes(value: Long): String = when { value < 1024 -> "$value B"; value < 1024 * 1024 -> "${value / 1024} KB"; value < 1024 * 1024 * 1024 -> "${value / (1024 * 1024)} MB"; else -> "${value / (1024 * 1024 * 1024)} GB" }
