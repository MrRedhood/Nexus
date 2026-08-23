package com.mrredhood.nexus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.nexus.data.ProjectRepository
import com.mrredhood.nexus.ui.NexusViewModel
import com.mrredhood.nexus.ui.NexusViewModelFactory
import com.mrredhood.nexus.ui.theme.NexusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = ProjectRepository(applicationContext)
        setContent {
            NexusTheme {
                val vm: NexusViewModel = viewModel(factory = NexusViewModelFactory(repository))
                NexusApp(vm)
            }
        }
    }
}

@Composable
private fun NexusApp(vm: NexusViewModel) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = projects.firstOrNull { it.id == selectedId }
    Surface(modifier = Modifier.fillMaxSize()) {
        if (selected == null) HomeScreen(projects, vm, onOpen = { selectedId = it })
        else ProjectScreen(selected.name, selected.repository, onBack = { selectedId = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(projects: List<com.mrredhood.nexus.core.model.NexusProject>, vm: NexusViewModel, onOpen: (String) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nexus") },
                actions = { IconButton(onClick = { showCreate = true }) { Icon(Icons.Outlined.Add, "Create project") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Mobile-first development", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Nexus keeps the phone as the development interface and uses cloud CI as the build machine.", style = MaterialTheme.typography.bodyMedium)
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
                            Text("Create a project to establish its workspace identity.")
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
                Spacer(Modifier.height(8.dp))
                Card(shape = MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Outlined.Settings, null)
                        Column {
                            Text("Foundation", style = MaterialTheme.typography.titleMedium)
                            Text("Compose UI · API 36 · persistent project metadata · cloud-first CI", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showCreate) CreateProjectDialog(onDismiss = { showCreate = false }, onCreate = { name, repo -> vm.createProject(name, repo); showCreate = false })
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create project") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Project name") }, singleLine = true)
            OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("GitHub repository (optional)") }, singleLine = true)
        }
    }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onCreate(name, repo.trim().ifBlank { null }) }) { Text("Create") } }, dismissButton = { FilledTonalButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectScreen(name: String, repository: String?, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ProjectCard(Icons.Outlined.FolderOpen, "Workspace", repository ?: "Local workspace", "Workspace identity is ready for the file-system layer.") }
            item { ProjectCard(Icons.Outlined.Code, "Engineering foundation", "Kotlin + Compose + API 36", "The next layers build on this project primitive: files, editor, Git, GitHub and cloud CI.") }
            item { ProjectCard(Icons.Outlined.Settings, "Build strategy", "Cloud-first", "No Android SDK, NDK, JDK or Gradle build infrastructure is bundled into the app.") }
        }
    }
}

@Composable
private fun ProjectCard(icon: ImageVector, title: String, value: String, detail: String) {
    Card(shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(icon, null); Text(title, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
