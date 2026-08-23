package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.core.settings.NexusSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: NexusSettings, onUpdate: ((NexusSettings) -> NexusSettings) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsSection("Appearance", Icons.Outlined.Tune) {
                ChoiceRow("Theme", settings.theme, listOf("system", "light", "dark", "amoled")) { onUpdate { it.copy(theme = it) } }
                ChoiceRow("Accent color", settings.accent, listOf("blue", "purple", "cyan", "green", "orange", "red")) { onUpdate { it.copy(accent = it) } }
                ChoiceRow("UI scale", "${(settings.uiScale * 100).toInt()}%", listOf("80%", "90%", "100%", "110%", "120%", "130%")) { value -> onUpdate { it.copy(uiScale = value.removeSuffix("%").toFloat() / 100f) } }
                ToggleRow("Compact mode", settings.compactMode) { onUpdate { it.copy(compactMode = it) } }
                ToggleRow("Fullscreen", settings.fullscreen) { onUpdate { it.copy(fullscreen = it) } }
                ToggleRow("Immersive coding mode", settings.immersiveCoding) { onUpdate { it.copy(immersiveCoding = it) } }
            }
            SettingsSection("Editor", Icons.Outlined.Code) {
                ChoiceRow("Font", settings.editorFont, listOf("JetBrains Mono", "Roboto Mono", "Fira Code", "Source Code Pro", "Ubuntu Mono", "System Mono")) { onUpdate { it.copy(editorFont = it) } }
                ChoiceRow("Font size", settings.editorFontSize.toString(), (10..24 step 2).map(Int::toString)) { onUpdate { it.copy(editorFontSize = value.toInt()) } }
                ChoiceRow("Word wrap", settings.wordWrap, listOf("off", "on", "editor_width", "custom")) { onUpdate { it.copy(wordWrap = it) } }
                ChoiceRow("Tab size", settings.tabSize.toString(), listOf("2", "4", "8")) { onUpdate { it.copy(tabSize = it.toInt()) } }
                ToggleRow("Use spaces", settings.useSpaces) { onUpdate { it.copy(useSpaces = it) } }
                ToggleRow("Auto-indent", settings.autoIndent) { onUpdate { it.copy(autoIndent = it) } }
                ToggleRow("Auto-close brackets", settings.autoCloseBrackets) { onUpdate { it.copy(autoCloseBrackets = it) } }
                ToggleRow("Auto-close tags", settings.autoCloseTags) { onUpdate { it.copy(autoCloseTags = it) } }
                ToggleRow("Auto-rename tags", settings.autoRenameTags) { onUpdate { it.copy(autoRenameTags = it) } }
                ToggleRow("Syntax highlighting", settings.syntaxHighlighting) { onUpdate { it.copy(syntaxHighlighting = it) } }
                ToggleRow("Rainbow brackets", settings.rainbowBrackets) { onUpdate { it.copy(rainbowBrackets = it) } }
                ChoiceRow("Line numbers", settings.lineNumbers, listOf("off", "absolute", "relative")) { onUpdate { it.copy(lineNumbers = it) } }
                ToggleRow("Current line highlight", settings.currentLineHighlight) { onUpdate { it.copy(currentLineHighlight = it) } }
                ToggleRow("Indent guides", settings.indentGuides) { onUpdate { it.copy(indentGuides = it) } }
                ToggleRow("Show whitespace", settings.showWhitespace) { onUpdate { it.copy(showWhitespace = it) } }
                ToggleRow("Code folding", settings.codeFolding) { onUpdate { it.copy(codeFolding = it) } }
                ToggleRow("Minimap", settings.minimap) { onUpdate { it.copy(minimap = it) } }
            }
            SettingsSection("Files & Explorer", Icons.Outlined.Folder) {
                ChoiceRow("Sort by", settings.explorerSort, listOf("name", "type", "size", "modified", "created")) { onUpdate { it.copy(explorerSort = it) } }
                ToggleRow("Descending", settings.explorerDescending) { onUpdate { it.copy(explorerDescending = it) } }
                ToggleRow("Folders first", settings.foldersFirst) { onUpdate { it.copy(foldersFirst = it) } }
                ChoiceRow("View", settings.explorerView, listOf("list", "compact", "grid")) { onUpdate { it.copy(explorerView = it) } }
                ToggleRow("Show hidden files", settings.showHiddenFiles) { onUpdate { it.copy(showHiddenFiles = it) } }
                ToggleRow("Show full path", settings.showFullPath) { onUpdate { it.copy(showFullPath = it) } }
            }
            SettingsSection("Workspace", Icons.Outlined.Memory) {
                ChoiceRow("AI permission mode", settings.workspacePermission, listOf("restricted", "standard", "full")) { onUpdate { it.copy(workspacePermission = it) } }
                ChoiceRow("Indexing", settings.indexing, listOf("automatic", "on_open", "manual", "disabled")) { onUpdate { it.copy(indexing = it) } }
                ChoiceRow("Automatic workspace context", settings.workspaceContext, listOf("never", "smart", "always")) { onUpdate { it.copy(workspaceContext = it) } }
            }
            SettingsSection("AI Context", Icons.Outlined.SmartToy) {
                ToggleRow("Streaming", settings.aiStreaming) { onUpdate { it.copy(aiStreaming = it) } }
                ChoiceRow("Maximum context files", settings.maxContextFiles.toString(), listOf("3", "5", "10", "20")) { onUpdate { it.copy(maxContextFiles = it.toInt()) } }
                ToggleRow("Current file", settings.includeCurrentFile) { onUpdate { it.copy(includeCurrentFile = it) } }
                ToggleRow("Selection", settings.includeSelection) { onUpdate { it.copy(includeSelection = it) } }
                ToggleRow("Git diff", settings.includeGitDiff) { onUpdate { it.copy(includeGitDiff = it) } }
                ToggleRow("Terminal output", settings.includeTerminalContext) { onUpdate { it.copy(includeTerminalContext = it) } }
                ToggleRow("Workspace summary", settings.includeWorkspaceSummary) { onUpdate { it.copy(includeWorkspaceSummary = it) } }
            }
            SettingsSection("Agents & Safety", Icons.Outlined.Security) {
                ChoiceRow("Agent autonomy", settings.agentAutonomy, listOf("ask_every_time", "approve_risky", "autonomous")) { onUpdate { it.copy(agentAutonomy = it) } }
                ChoiceRow("Maximum parallel agents", settings.maxParallelAgents.toString(), listOf("1", "2", "3", "4", "8")) { onUpdate { it.copy(maxParallelAgents = it.toInt()) } }
                ToggleRow("Snapshot before AI edit", settings.snapshotBeforeAiEdit) { onUpdate { it.copy(snapshotBeforeAiEdit = it) } }
                ChoiceRow("Show diff before applying", settings.showDiffBeforeApply, listOf("always", "risky", "never")) { onUpdate { it.copy(showDiffBeforeApply = it) } }
                ChoiceRow("Auto-apply patches", settings.autoApplyPatches, listOf("never", "approved", "trusted")) { onUpdate { it.copy(autoApplyPatches = it) } }
            }
            SettingsSection("Terminal", Icons.Outlined.Terminal) {
                ChoiceRow("Font size", settings.terminalFontSize.toString(), listOf("11", "13", "15", "17")) { onUpdate { it.copy(terminalFontSize = it.toInt()) } }
                ToggleRow("Confirm terminal close", settings.confirmTerminalClose) { onUpdate { it.copy(confirmTerminalClose = it) } }
            }
            SettingsSection("GitHub Actions / CI") {
                ToggleRow("Automatically analyze CI failures", settings.autoAnalyzeCiFailures) { onUpdate { it.copy(autoAnalyzeCiFailures = it) } }
                ChoiceRow("Refresh interval", "${settings.ciRefreshSeconds}s", listOf("5s", "10s", "30s", "60s")) { onUpdate { it.copy(ciRefreshSeconds = it.removeSuffix("s").toInt()) } }
            }
            SettingsSection("Projects") {
                ToggleRow("Remember open files", settings.rememberOpenFiles) { onUpdate { it.copy(rememberOpenFiles = it) } }
                ToggleRow("Remember cursor position", settings.rememberCursorPosition) { onUpdate { it.copy(rememberCursorPosition = it) } }
                ToggleRow("Remember Explorer state", settings.rememberExplorerState) { onUpdate { it.copy(rememberExplorerState = it) } }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Build architecture", style = MaterialTheme.typography.titleMedium)
                    Text("Nexus does not manage Android SDK, NDK, JDK, CMake, Flutter SDK, Dart SDK, Gradle installations, or local APK/AAB toolchains. Builds are delegated to GitHub Actions.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { if (icon != null) Icon(icon, null); Text(title, style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.padding(2.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(title: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember(title) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, modifier = Modifier.weight(1f).padding(top = 14.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.menuAnchor()) { Text(selected.replace('_', ' ')) }
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option.replace('_', ' ')) }, onClick = { expanded = false; onSelect(option) }) }
            }
        }
    }
}
