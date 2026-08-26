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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Stop
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.workspace.EntryType
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class TerminalLine(val text: String, val error: Boolean = false)

private class TerminalSession(
    val id: Int,
    val name: String,
    initialCwd: String = ""
) {
    var cwd by mutableStateOf(initialCwd)
    val lines = mutableStateListOf<TerminalLine>()
    val history = mutableStateListOf<String>()
    var historyIndex by mutableStateOf(-1)
    var running by mutableStateOf(false)
    var job: Job? = null
}

private interface TerminalProvider {
    suspend fun execute(
        command: String,
        session: TerminalSession,
        workspace: Workspace,
        fileSystem: WorkspaceFileSystem,
        print: (String, Boolean) -> Unit
    )
}

private class WorkspaceTerminalProvider : TerminalProvider {
    override suspend fun execute(
        command: String,
        session: TerminalSession,
        workspace: Workspace,
        fileSystem: WorkspaceFileSystem,
        print: (String, Boolean) -> Unit
    ) {
        val parts = command.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        val name = parts.first().lowercase()

        fun normalize(path: String): String {
            val raw = path.trim().replace('\\', '/')
            if (raw.isBlank()) return session.cwd
            val combined = if (raw.startsWith('/')) raw.removePrefix("/") else if (session.cwd.isBlank()) raw else "${session.cwd}/$raw"
            val normalized = mutableListOf<String>()
            combined.split('/').forEach { part ->
                when {
                    part.isBlank() || part == "." -> Unit
                    part == ".." -> if (normalized.isNotEmpty()) normalized.removeAt(normalized.lastIndex)
                    else -> normalized.add(part)
                }
            }
            return normalized.joinToString("/")
        }

        when (name) {
            "help" -> print("Commands: pwd, ls, cd, cat, touch, mkdir, rm, cp, mv, find, grep, echo, clear, history, exit, git", false)
            "pwd" -> print("/${session.cwd}", false)
            "clear" -> session.lines.clear()
            "echo" -> print(command.substringAfter(parts.first()).trim().trim('"', '\''), false)
            "history" -> session.history.forEachIndexed { index, value -> print("${index + 1}  $value", false) }
            "cd" -> {
                val target = normalize(parts.getOrNull(1) ?: "")
                if (fileSystem.isDirectory(workspace, target)) session.cwd = target
                else print("cd: no such directory: ${parts.getOrNull(1) ?: ""}", true)
            }
            "ls" -> {
                val target = normalize(parts.getOrNull(1) ?: "")
                val entries = fileSystem.list(workspace, target)
                if (entries.isEmpty()) print("(empty)", false)
                else entries.forEach { entry -> print(if (entry.type == EntryType.DIRECTORY) "${entry.name}/" else entry.name, false) }
            }
            "cat" -> {
                val target = normalize(parts.getOrNull(1) ?: "")
                if (target.isBlank()) print("cat: missing file operand", true)
                else print(fileSystem.read(workspace, target).content, false)
            }
            "touch" -> {
                val target = parts.getOrNull(1) ?: throw IllegalArgumentException("touch: missing file operand")
                fileSystem.createFile(workspace, normalize(target))
            }
            "mkdir" -> {
                val target = parts.getOrNull(1) ?: throw IllegalArgumentException("mkdir: missing operand")
                fileSystem.createDirectory(workspace, normalize(target))
            }
            "rm" -> {
                val target = parts.getOrNull(1) ?: throw IllegalArgumentException("rm: missing operand")
                fileSystem.delete(workspace, normalize(target))
            }
            "cp", "mv" -> {
                val source = parts.getOrNull(1) ?: throw IllegalArgumentException("$name: missing source")
                val destination = parts.getOrNull(2) ?: throw IllegalArgumentException("$name: missing destination")
                val src = normalize(source)
                val dst = normalize(destination)
                if (name == "cp") fileSystem.copy(workspace, src, dst) else fileSystem.move(workspace, src, dst)
            }
            "find" -> {
                val root = normalize(parts.getOrNull(1) ?: "")
                val needle = parts.drop(2).firstOrNull()?.removePrefix("-name=")?.trim('"', '\'')
                val results = mutableListOf<String>()
                suspend fun walk(path: String) {
                    fileSystem.list(workspace, path).forEach { entry ->
                        if (needle == null || entry.name == needle || entry.name.contains(needle, true)) results += entry.relativePath
                        if (entry.type == EntryType.DIRECTORY) walk(entry.relativePath)
                    }
                }
                walk(root)
                results.forEach { print(it, false) }
                if (results.isEmpty()) print("No matches", false)
            }
            "grep" -> {
                val needle = parts.getOrNull(1) ?: throw IllegalArgumentException("grep: missing pattern")
                val target = normalize(parts.getOrNull(2) ?: "")
                if (target.isBlank()) throw IllegalArgumentException("grep: missing file")
                fileSystem.read(workspace, target).content.lineSequence().filter { it.contains(needle) }.forEach { print(it, false) }
            }
            "git" -> print("Git commands are handled by Nexus Source Control. Open Source Control for fetch, diff, commit and push.", false)
            "exit" -> Unit
            else -> print("$command: command not found. Type 'help' for available commands.", true)
        }
    }
}

@Composable
fun TerminalScreen(project: NexusProject, workspace: Workspace, onBack: () -> Unit) {
    val context = LocalContext.current
    val fileSystem = remember { WorkspaceFileSystem(context) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val provider = remember { WorkspaceTerminalProvider() }
    val sessions = remember { mutableStateListOf(TerminalSession(1, "Terminal 1")) }
    var selectedId by remember { mutableStateOf(1) }
    var nextId by remember { mutableStateOf(2) }
    var command by remember { mutableStateOf("") }

    val selected = sessions.firstOrNull { it.id == selectedId } ?: sessions.first()

    fun print(session: TerminalSession, text: String, error: Boolean = false) {
        session.lines.add(TerminalLine(text, error))
    }

    fun submit(raw: String) {
        val input = raw.trim()
        if (input.isBlank() || selected.running) return
        selected.history.add(input)
        selected.historyIndex = -1
        print(selected, "$ $input")
        command = ""
        selected.running = true
        selected.job = scope.launch {
            try {
                provider.execute(input, selected, workspace, fileSystem) { text, error -> print(selected, text, error) }
                if (input.equals("exit", true) && sessions.size > 1) {
                    sessions.removeIf { it.id == selected.id }
                    selectedId = sessions.first().id
                }
            } catch (_: CancellationException) {
                print(selected, "^C", true)
            } catch (e: Exception) {
                print(selected, e.message ?: "Command failed", true)
            } finally {
                selected.running = false
                selected.job = null
            }
        }
    }

    BackHandler {
        selected.job?.cancel()
        onBack()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Terminal") },
            navigationIcon = { IconButton(onClick = { selected.job?.cancel(); onBack() }) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { selected.lines.clear() }) { Icon(Icons.Outlined.ClearAll, "Clear") }
                IconButton(onClick = { clipboard.setText(AnnotatedString(selected.lines.joinToString("\n") { it.text })) }) { Icon(Icons.Outlined.ContentCopy, "Copy output") }
                if (selected.running) {
                    IconButton(onClick = { selected.job?.cancel() }) { Icon(Icons.Outlined.Stop, "Stop command") }
                } else {
                    IconButton(onClick = {
                        val id = nextId++
                        sessions.add(TerminalSession(id, "Terminal $id", selected.cwd))
                        selectedId = id
                        command = ""
                    }) { Icon(Icons.Outlined.Add, "New terminal") }
                }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sessions.forEach { session ->
                            Card(onClick = { if (!session.running) selectedId = session.id }, colors = CardDefaults.cardColors(containerColor = if (session.id == selected.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
                                    Icon(Icons.Outlined.Terminal, null, modifier = Modifier.width(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(session.name, style = MaterialTheme.typography.labelMedium)
                                    if (sessions.size > 1) IconButton(onClick = {
                                        session.job?.cancel()
                                        sessions.removeIf { it.id == session.id }
                                        if (selectedId == session.id) selectedId = sessions.first().id
                                    }, modifier = Modifier.width(24.dp).height(24.dp)) { Icon(Icons.Outlined.Close, "Close", modifier = Modifier.width(16.dp)) }
                                }
                            }
                        }
                    }
                }
                items(selected.lines) { line ->
                    Text(line.text, color = if (line.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("${if (selected.cwd.isBlank()) "/" else "/${selected.cwd}"} $ ", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    BasicTextField(
                        value = command,
                        onValueChange = { command = it },
                        singleLine = true,
                        enabled = !selected.running,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit(command) }),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        decorationBox = { inner ->
                            if (command.isBlank()) Text(if (selected.running) "Command running…" else "Run a command…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                            inner()
                        }
                    )
                }
            }
            Text("${if (selected.running) "Running · " else ""}Workspace · ${project.name}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}
