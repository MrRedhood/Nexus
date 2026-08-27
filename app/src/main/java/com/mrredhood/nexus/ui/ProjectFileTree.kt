package com.mrredhood.nexus.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.mrredhood.nexus.core.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ProjectTreeNode(val path: String, val name: String, val directory: Boolean, val children: List<ProjectTreeNode> = emptyList())

@Composable
fun ProjectFileTreeDrawer(workspace: Workspace, currentPath: String, onOpenFile: (String) -> Unit, onOpenDirectory: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var nodes by remember(workspace.id) { mutableStateOf<List<ProjectTreeNode>>(emptyList()) }
    var loading by remember(workspace.id) { mutableStateOf(true) }
    LaunchedEffect(workspace.id) {
        loading = true
        nodes = withContext(Dispatchers.IO) { loadProjectTree(context, workspace) }
        loading = false
    }
    ModalDrawerSheet {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("Project", style = MaterialTheme.typography.labelLarge)
                Text(workspace.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
            HorizontalDivider()
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LazyColumn(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                item { TreeRow(workspace.displayName, "", true, 0, true, currentPath.isBlank()) { onOpenDirectory(""); onClose() } }
                items(nodes, key = { it.path }) { node -> TreeNodeRow(node, currentPath, 1, onOpenFile, onOpenDirectory, onClose) }
            }
        }
    }
}

@Composable
private fun TreeNodeRow(node: ProjectTreeNode, currentPath: String, depth: Int, onOpenFile: (String) -> Unit, onOpenDirectory: (String) -> Unit, onClose: () -> Unit) {
    var expanded by remember(node.path) { mutableStateOf(node.path == currentPath || currentPath.startsWith("${node.path}/")) }
    TreeRow(node.name, node.path, node.directory, depth, expanded, node.path == currentPath) {
        if (node.directory) {
            expanded = !expanded
            onOpenDirectory(node.path)
        } else {
            onOpenFile(node.path)
            onClose()
        }
    }
    if (node.directory && expanded) node.children.forEach { child -> TreeNodeRow(child, currentPath, depth + 1, onOpenFile, onOpenDirectory, onClose) }
}

@Composable
private fun TreeRow(name: String, path: String, directory: Boolean, depth: Int, expanded: Boolean, selected: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(start = (12 + depth * 16).dp, end = 12.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (directory) {
                Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Outlined.Folder, null, Modifier.size(19.dp))
            } else {
                Spacer(Modifier.width(20.dp))
                Icon(Icons.Outlined.InsertDriveFile, null, Modifier.size(19.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(name, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private suspend fun loadProjectTree(context: Context, workspace: Workspace): List<ProjectTreeNode> = withContext(Dispatchers.IO) {
    fun build(document: DocumentFile, parent: String): List<ProjectTreeNode> = document.listFiles().mapNotNull { child ->
        val name = child.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val path = if (parent.isBlank()) name else "$parent/$name"
        ProjectTreeNode(path, name, child.isDirectory, if (child.isDirectory) build(child, path) else emptyList())
    }.sortedWith(compareBy<ProjectTreeNode> { !it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    val root = DocumentFile.fromTreeUri(context, workspace.uri()) ?: return@withContext emptyList()
    build(root, "")
}
