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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.workspace.EntryType
import com.mrredhood.nexus.core.workspace.Workspace
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
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = projects.firstOrNull { it.id == selectedId }
    val selectedWorkspace = selected?.let { project -> workspaces.firstOrNull { it.projectId == project.id } }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        pendingWorkspaceSelection?.invoke(uri)
        pendingWorkspaceSelection = null
    }

    Surface(Modifier.fillMaxSize()) {
        if (selected == null) HomeScreen(projects, vm, onOpen = { selectedId = it }, onPickWorkspace = { callback -> pendingWorkspaceSelection = callback; folderPicker.launch(null) })
        else if (selectedWorkspace != null) ProjectScreen(selected, selectedWorkspace, onBack = { selectedId = null })
        else WorkspaceMissingScreen(selected, vm, onBack = { selectedId = null }, onPickWorkspace = { callback -> pendingWorkspaceSelection = callback; folderPicker.launch(null) })
    }
}

private var pendingWorkspaceSelection: ((Uri?) -> Unit)? = null

@Composable
private fun HomeScreen(
    projects: List<NexusProject>,
    vm: NexusViewModel,
    onOpen: (String) -> Unit,
    onPickWorkspace: (((Uri?) -> Unit)) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Nexus") }, actions = {
            IconButton(onClick = { showCreate = true }) { Icon(Icons.Outlined.Add, "Create project") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Workspace", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Nexus uses Android's document provider for durable, user-selected workspaces. Files stay under the location you explicitly grant.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Text("Projects", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            if (projects.isEmpty()) {
                item {
                    Card(shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(20.dp)) {
                            Text("No projects yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text("Create a project and select its workspace folder.")
                            Spacer(Modifier.height(14.dp))
                            FilledTonalButton(onClick = { showCreate = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.padding(3.dp)); Text("Create project") }
                        }
                    }
                }
            }
            items(projects, key = { it.id }) { project ->
                Card(onClick = { onOpen(project.id) }, shape = MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            Text(project.repository ?: "Local workspace", style = MaterialTheme.typography.bodyMedium)
                            Text("Branch · ${project.branch}", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = { vm.deleteProject(project.id) }) { Icon(Icons.Outlined.Delete, "Delete project") }
                    }
                }
            }
            item {
                Card(shape = MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Outlined.Settings, null)
                        Column {
                            Text("Foundation", style = MaterialTheme.typography.titleMedium)
                            Text("Material 3 · API 36 · SAF workspace · persistent metadata", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showCreate) CreateProjectDialog(
        onDismiss = { showCreate = false },
        onPickWorkspace = { callback -> onPickWorkspace(callback) },
        onCreate = { name, repo, uri ->
            if (uri != null) {
                val displayName = DocumentFile.fromTreeUri(androidx.compose.ui.platform.LocalContext.current, uri)?.name ?: name
                vm.createProject(name, repo, uri, displayName) { showCreate = false }
            }
        }
    )
}

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onPickWorkspace: (((Uri?) -> Unit)) -> Unit,
    onCreate: (String, String?, Uri?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var workspaceUri by remember { mutableStateOf<Uri?>(null) }
    var workspaceName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("GitHub repository (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Card(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Workspace", style = MaterialTheme.typography.titleSmall)
                        Text(if (workspaceName.isBlank()) "Select a folder" else workspaceName, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = {
                            onPickWorkspace { uri ->
                                workspaceUri = uri
                                workspaceName = uri?.let { DocumentFile.fromTreeUri(context, it)?.name ?: "Selected folder" } ?: ""
                            }
                        }) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.padding(3.dp)); Text("Choose folder") }
                    }
                }
            }
        },
        confirmButton = { FilledTonalButton(enabled = name.isNotBlank() && workspaceUri != null, onClick = { onCreate(name, repo.trim().ifBlank { null }, workspaceUri) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WorkspaceMissingScreen(project: NexusProject, vm: NexusViewModel, onBack: () -> Unit, onPickWorkspace: (((Uri?) -> Unit)) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(project.name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }) }) { padding ->
        Card(Modifier.fillMaxWidth().padding(padding).padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp)) {
                Text("Workspace unavailable", style = MaterialTheme.typography.titleLarge)
                Text("The previously selected folder is no longer accessible. Select a new workspace folder.", modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(onClick = {
                    onPickWorkspace { uri ->
                        if (uri != null) vm.createProject(project.name, project.repository, uri, project.name) { }
                    }
                }) { Text("Select workspace") }
            }
        }
    }
}

@Composable
private fun ProjectScreen(project: NexusProject, workspace: Workspace, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: WorkspaceViewModel = viewModel(factory = WorkspaceViewModelFactory(context))
    val entries by vm.entries.collectAsStateWithLifecycle()
    val currentPath by vm.currentPath.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val openedFile by vm.openedFile.collectAsStateWithLifecycle()
    var showCreateMenu by remember { mutableStateOf(false) }
    var createFolder by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(workspace.id) { vm.open(workspace) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text(project.name); Text(if (currentPath.isBlank()) workspace.displayName else currentPath, style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { vm.refresh(workspace) }) { Icon(Icons.Outlined.Refresh, "Refresh workspace") }
                IconButton(onClick = { showCreateMenu = true }) { Icon(Icons.Outlined.Add, "Create") }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (currentPath.isNotBlank()) {
                FilledTonalButton(onClick = { vm.up(workspace) }, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Outlined.ArrowBack, null); Spacer(Modifier.padding(3.dp)); Text("Parent") }
            }
            if (loading) CircularProgressIndicator(Modifier.padding(20.dp))
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries, key = { it.relativePath }) { entry ->
                    WorkspaceEntryCard(entry, onOpen = {
                        if (entry.type == EntryType.DIRECTORY) vm.enter(workspace, entry.relativePath) else vm.read(workspace, entry.relativePath)
                    }, onDelete = { vm.delete(workspace, entry.relativePath) })
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    if (showCreateMenu) {
        AlertDialog(onDismissRequest = { showCreateMenu = false }, title = { Text("Create in ${if (currentPath.isBlank()) workspace.displayName else currentPath}") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { showCreateMenu = false; createFolder = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CreateNewFolder, null); Spacer(Modifier.padding(3.dp)); Text("New folder") }
                FilledTonalButton(onClick = { showCreateMenu = false; createFolder = false; createName = "" }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Description, null); Spacer(Modifier.padding(3.dp)); Text("New file") }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { showCreateMenu = false }) { Text("Cancel") } })
    }

    if (!showCreateMenu && createName.isEmpty() && false) Unit
    if (createFolder || (!showCreateMenu && createName.isNotEmpty())) {
        NameDialog(title = if (createFolder) "New folder" else "New file", value = createName, onValueChange = { createName = it }, onDismiss = { createFolder = false; createName = "" }, onConfirm = {
            if (createFolder) vm.createDirectory(workspace, it) else vm.createFile(workspace, it)
            createFolder = false; createName = ""
        })
    }
    if (error != null) AlertDialog(onDismissRequest = { vm.clearError() }, title = { Text("Workspace error") }, text = { Text(error ?: "Unknown error") }, confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("OK") } })
    if (openedFile != null) AlertDialog(onDismissRequest = { vm.clearOpenedFile() }, title = { Text(openedFile?.name ?: "File") }, text = { Text(openedFile?.content ?: "", style = MaterialTheme.typography.bodySmall) }, confirmButton = { TextButton(onClick = { vm.clearOpenedFile() }) { Text("Close") } })
}

@Composable
private fun WorkspaceEntryCard(entry: com.mrredhood.nexus.core.workspace.WorkspaceEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onOpen, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(if (entry.type == EntryType.DIRECTORY) Icons.Outlined.Folder else Icons.Outlined.Description, null)
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                Text(if (entry.type == EntryType.DIRECTORY) "Folder" else formatBytes(entry.sizeBytes), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete ${entry.name}") }
        }
    }
}

@Composable
private fun NameDialog(title: String, value: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text("Name") }, singleLine = true) }, confirmButton = { FilledTonalButton(enabled = value.trim().isNotEmpty(), onClick = { onConfirm(value.trim()) }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun formatBytes(value: Long): String = when {
    value < 1024 -> "$value B"
    value < 1024 * 1024 -> "${value / 1024} KB"
    value < 1024 * 1024 * 1024 -> "${value / (1024 * 1024)} MB"
    else -> "${value / (1024 * 1024 * 1024)} GB"
}
