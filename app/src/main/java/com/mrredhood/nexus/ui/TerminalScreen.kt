@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mrredhood.nexus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.workspace.EntryType
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import kotlinx.coroutines.launch

private data class TerminalLine(val text: String, val error: Boolean = false)
private data class TerminalSession(val id: Int, val name: String, var cwd: String = "", val lines: MutableList<TerminalLine> = mutableListOf())

@Composable
fun TerminalScreen(project: NexusProject, workspace: Workspace, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fileSystem = remember { WorkspaceFileSystem(context) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val sessions = remember { mutableStateListOf(TerminalSession(1, "Terminal 1")) }
    var selectedId by remember { mutableStateOf(1) }
    var nextId by remember { mutableStateOf(2) }
    var command by remember { mutableStateOf("") }

    val selected = sessions.firstOrNull { it.id == selectedId } ?: sessions.first()

    fun print(text: String, error: Boolean = false) {
        selected.lines.add(TerminalLine(text, error))
    }

    fun normalize(path: String, cwd: String): String {
        val raw = path.trim().replace('\\', '/')
        if (raw.isBlank()) return cwd
        val combined = if (raw.startsWith('/')) raw.removePrefix("/") else if (cwd.isBlank()) raw else "$cwd/$raw"
        val parts = mutableListOf<String>()
        combined.split('/').forEach { part ->
            when {
                part.isBlank() || part == "." -> Unit
                part == ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(part)
            }
        }
        return parts.joinToString("/")
    }

    fun run(raw: String) {
        val input = raw.trim()
        if (input.isBlank()) return
        print("$ $input")
        command = ""
        scope.launch {
            try {
                val parts = input.split(Regex("\\s+"))
                when (parts.first().lowercase()) {
                    "help" -> print("Commands: pwd, ls, cd, cat, touch, mkdir, rm, cp, mv, find, grep, echo, clear, history, exit")
                    "pwd" -> print("/${selected.cwd}")
                    "clear" -> selected.lines.clear()
                    "echo" -> print(input.substringAfter(parts.first()).trim().trim('"', '\''))
                    "history" -> print(selected.lines.filter { it.text.startsWith("$ ") }.joinToString("\n") { it.text })
                    "cd" -> {
                        val target = normalize(parts.getOrNull(1) ?: "", selected.cwd)
                        if (fileSystem.isDirectory(workspace, target)) selected.cwd = target else print("cd: no such directory: ${parts.getOrNull(1) ?: ""}", true)
                    }
                    "ls" -> {
                        val target = normalize(parts.getOrNull(1) ?: "", selected.cwd)
                        val entries = fileSystem.list(workspace, target)
                        if (entries.isEmpty()) print("(empty)") else entries.forEach { entry -> print(if (entry.type == EntryType.DIRECTORY) "${entry.name}/" else entry.name) }
                    }
                    "cat" -> {
                        val target = normalize(parts.getOrNull(1) ?: "", selected.cwd)
                        if (target.isBlank()) print("cat: missing file operand", true) else print(fileSystem.read(workspace, target).content)
                    }
                    "touch" -> {
                        val name = parts.getOrNull(1) ?: throw IllegalArgumentException("touch: missing file operand")
                        fileSystem.createFile(workspace, normalize(name, selected.cwd))
                    }
                    "mkdir" -> {
                        val name = parts.getOrNull(1) ?: throw IllegalArgumentException("mkdir: missing operand")
                        fileSystem.createDirectory(workspace, normalize(name, selected.cwd))
                    }
                    "rm" -> {
                        val name = parts.getOrNull(1) ?: throw IllegalArgumentException("rm: missing operand")
                        fileSystem.delete(workspace, normalize(name, selected.cwd))
                    }
                    "cp", "mv" -> {
                        val source = parts.getOrNull(1) ?: throw IllegalArgumentException("${parts.first()}: missing source")
                        val destination = parts.getOrNull(2) ?: throw IllegalArgumentException("${parts.first()}: missing destination")
                        val src = normalize(source, selected.cwd)
                        val dst = normalize(destination, selected.cwd)
                        if (parts.first().equals("cp", true)) fileSystem.copy(workspace, src, dst) else fileSystem.move(workspace, src, dst)
                    }
                    "find" -> {
                        val root = normalize(parts.getOrNull(1) ?: "", selected.cwd)
                        val needle = parts.drop(2).firstOrNull()?.removePrefix("-name=")?.trim('"', '\'')
                        val results = mutableListOf<String>()
                        suspend fun walk(path: String) {
                            fileSystem.list(workspace, path).forEach { entry ->
                                if (needle == null || entry.name == needle || entry.name.contains(needle, true)) results += entry.relativePath
                                if (entry.type == EntryType.DIRECTORY) walk(entry.relativePath)
                            }
                        }
                        walk(root)
                        results.forEach(::print)
                        if (results.isEmpty()) print("No matches")
                    }
                    "grep" -> {
                        val needle = parts.getOrNull(1) ?: throw IllegalArgumentException("grep: missing pattern")
                        val target = normalize(parts.getOrNull(2) ?: "", selected.cwd)
                        if (target.isBlank()) throw IllegalArgumentException("grep: missing file")
                        fileSystem.read(workspace, target).content.lineSequence().filter { it.contains(needle) }.forEach(::print)
                    }
                    "exit" -> if (sessions.size > 1) { sessions.removeIf { it.id == selected.id }; selectedId = sessions.first().id }
                    "git" -> print("Git commands are handled by Nexus Source Control. Open Source Control for fetch, diff, commit and push.")
                    else -> print("$input: command not found. Type 'help' for available commands.", true)
                }
            } catch (e: Exception) {
                print(e.message ?: "Command failed", true)
            }
        }
    }

    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Terminal") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { selected.lines.clear() }) { Icon(Icons.Outlined.ClearAll, "Clear") }
                IconButton(onClick = { clipboard.setText(AnnotatedString(selected.lines.joinToString("\n") { it.text })) }) { Icon(Icons.Outlined.ContentCopy, "Copy output") }
                IconButton(onClick = {
                    val id = nextId++
                    sessions.add(TerminalSession(id, "Terminal $id"))
                    selectedId = id
                }) { Icon(Icons.Outlined.Add, "New terminal") }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sessions.forEach { session ->
                            Card(onClick = { selectedId = session.id }, colors = CardDefaults.cardColors(containerColor = if (session.id == selected.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Icon(Icons.Outlined.Terminal, null, modifier = Modifier.width(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(session.name, style = MaterialTheme.typography.labelMedium)
                                    if (sessions.size > 1) IconButton(onClick = { sessions.removeIf { it.id == session.id }; if (selectedId == session.id) selectedId = sessions.first().id }, modifier = Modifier.width(22.dp).height(22.dp)) { Icon(Icons.Outlined.Close, "Close", modifier = Modifier.width(16.dp)) }
                                }
                            }
                        }
                    }
                }
                items(selected.lines) { line -> Text(line.text, color = if (line.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            }
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("${if (selected.cwd.isBlank()) "/" else "/${selected.cwd}"} $ ", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    BasicTextField(value = command, onValueChange = { command = it }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f).padding(start = 4.dp), decorationBox = { inner -> if (command.isBlank()) Text("Run a command…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace); inner() })
                }
            }
            Text("Workspace · ${project.name}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}
