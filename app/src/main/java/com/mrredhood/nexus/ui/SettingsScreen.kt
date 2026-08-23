package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
                ChoiceRow("Theme", settings.theme, listOf("system", "light", "dark", "amoled")) { v -> onUpdate { it.copy(theme = v) } }
                ChoiceRow("Accent color", settings.accent, listOf("blue", "purple", "cyan", "green", "orange", "red")) { v -> onUpdate { it.copy(accent = v) } }
                ChoiceRow("Editor theme", settings.editorTheme, listOf("nexus_dark", "nexus_light", "system")) { v -> onUpdate { it.copy(editorTheme = v) } }
                ChoiceRow("UI scale", "${(settings.uiScale * 100).toInt()}%", listOf("80%", "90%", "100%", "110%", "120%", "130%")) { v -> onUpdate { it.copy(uiScale = v.removeSuffix("%").toFloat() / 100f) } }
                ChoiceRow("Animations", settings.animations, listOf("system", "on", "off", "reduced")) { v -> onUpdate { it.copy(animations = v) } }
                ToggleRow("Compact mode", settings.compactMode) { v -> onUpdate { it.copy(compactMode = v) } }
                ToggleRow("Fullscreen", settings.fullscreen) { v -> onUpdate { it.copy(fullscreen = v) } }
                ToggleRow("Immersive coding mode", settings.immersiveCoding) { v -> onUpdate { it.copy(immersiveCoding = v) } }
            }

            SettingsSection("Editor", Icons.Outlined.Code) {
                ChoiceRow("Font", settings.editorFont, listOf("JetBrains Mono", "Roboto Mono", "Fira Code", "Source Code Pro", "Ubuntu Mono", "System Mono")) { v -> onUpdate { it.copy(editorFont = v) } }
                ChoiceRow("Font size", settings.editorFontSize.toString(), (10..24 step 2).map(Int::toString)) { v -> onUpdate { it.copy(editorFontSize = v.toInt()) } }
                ChoiceRow("Line height", settings.lineHeight, listOf("compact", "normal", "comfortable", "spacious")) { v -> onUpdate { it.copy(lineHeight = v) } }
                ChoiceRow("Word wrap", settings.wordWrap, listOf("off", "on", "editor_width", "custom")) { v -> onUpdate { it.copy(wordWrap = v) } }
                ChoiceRow("Tab size", settings.tabSize.toString(), listOf("2", "4", "8")) { v -> onUpdate { it.copy(tabSize = v.toInt()) } }
                ToggleRow("Use spaces", settings.useSpaces) { v -> onUpdate { it.copy(useSpaces = v) } }
                ToggleRow("Auto-indent", settings.autoIndent) { v -> onUpdate { it.copy(autoIndent = v) } }
                ToggleRow("Auto-close brackets", settings.autoCloseBrackets) { v -> onUpdate { it.copy(autoCloseBrackets = v) } }
                ToggleRow("Auto-close tags", settings.autoCloseTags) { v -> onUpdate { it.copy(autoCloseTags = v) } }
                ToggleRow("Auto-rename tags", settings.autoRenameTags) { v -> onUpdate { it.copy(autoRenameTags = v) } }
                ToggleRow("Syntax highlighting", settings.syntaxHighlighting) { v -> onUpdate { it.copy(syntaxHighlighting = v) } }
                ToggleRow("Rainbow brackets", settings.rainbowBrackets) { v -> onUpdate { it.copy(rainbowBrackets = v) } }
                ToggleRow("Matching bracket highlight", settings.matchingBracketHighlight) { v -> onUpdate { it.copy(matchingBracketHighlight = v) } }
                ChoiceRow("Line numbers", settings.lineNumbers, listOf("off", "absolute", "relative")) { v -> onUpdate { it.copy(lineNumbers = v) } }
                ToggleRow("Current line highlight", settings.currentLineHighlight) { v -> onUpdate { it.copy(currentLineHighlight = v) } }
                ToggleRow("Indent guides", settings.indentGuides) { v -> onUpdate { it.copy(indentGuides = v) } }
                ToggleRow("Show whitespace", settings.showWhitespace) { v -> onUpdate { it.copy(showWhitespace = v) } }
                ToggleRow("Code folding", settings.codeFolding) { v -> onUpdate { it.copy(codeFolding = v) } }
                ToggleRow("Minimap", settings.minimap) { v -> onUpdate { it.copy(minimap = v) } }
            }

            SettingsSection("Files & Explorer", Icons.Outlined.Folder) {
                ChoiceRow("Sort by", settings.explorerSort, listOf("name", "type", "size", "modified", "created")) { v -> onUpdate { it.copy(explorerSort = v) } }
                ToggleRow("Descending", settings.explorerDescending) { v -> onUpdate { it.copy(explorerDescending = v) } }
                ToggleRow("Folders first", settings.foldersFirst) { v -> onUpdate { it.copy(foldersFirst = v) } }
                ChoiceRow("View", settings.explorerView, listOf("list", "compact", "grid")) { v -> onUpdate { it.copy(explorerView = v) } }
                ToggleRow("Show hidden files", settings.showHiddenFiles) { v -> onUpdate { it.copy(showHiddenFiles = v) } }
                ToggleRow("Show full path", settings.showFullPath) { v -> onUpdate { it.copy(showFullPath = v) } }
            }

            SettingsSection("Workspace", Icons.Outlined.Memory) {
                ChoiceRow("AI permission mode", settings.workspacePermission, listOf("restricted", "standard", "full")) { v -> onUpdate { it.copy(workspacePermission = v) } }
                ChoiceRow("Indexing", settings.indexing, listOf("automatic", "on_open", "manual", "disabled")) { v -> onUpdate { it.copy(indexing = v) } }
                ChoiceRow("Automatic workspace context", settings.workspaceContext, listOf("never", "smart", "always")) { v -> onUpdate { it.copy(workspaceContext = v) } }
                ChoiceRow("AI completion", settings.aiCompletion, listOf("off", "manual", "automatic")) { v -> onUpdate { it.copy(aiCompletion = v) } }
                ToggleRow("Format on save", settings.formatOnSave) { v -> onUpdate { it.copy(formatOnSave = v) } }
                ToggleRow("Diagnostics", settings.diagnostics) { v -> onUpdate { it.copy(diagnostics = v) } }
            }

            SettingsSection("AI Context", Icons.Outlined.SmartToy) {
                ToggleRow("Streaming", settings.aiStreaming) { v -> onUpdate { it.copy(aiStreaming = v) } }
                ChoiceRow("Maximum context files", settings.maxContextFiles.toString(), listOf("3", "5", "10", "20")) { v -> onUpdate { it.copy(maxContextFiles = v.toInt()) } }
                ToggleRow("Current file", settings.includeCurrentFile) { v -> onUpdate { it.copy(includeCurrentFile = v) } }
                ToggleRow("Selection", settings.includeSelection) { v -> onUpdate { it.copy(includeSelection = v) } }
                ToggleRow("Git diff", settings.includeGitDiff) { v -> onUpdate { it.copy(includeGitDiff = v) } }
                ToggleRow("Terminal output", settings.includeTerminalContext) { v -> onUpdate { it.copy(includeTerminalContext = v) } }
                ToggleRow("Workspace summary", settings.includeWorkspaceSummary) { v -> onUpdate { it.copy(includeWorkspaceSummary = v) } }
                ChoiceRow("Memory approval", settings.memoryApproval, listOf("always", "ask", "never")) { v -> onUpdate { it.copy(memoryApproval = v) } }
            }

            SettingsSection("Agents & Safety", Icons.Outlined.Security) {
                ChoiceRow("Agent autonomy", settings.agentAutonomy, listOf("ask_every_time", "approve_risky", "autonomous")) { v -> onUpdate { it.copy(agentAutonomy = v) } }
                ChoiceRow("Maximum parallel agents", settings.maxParallelAgents.toString(), listOf("1", "2", "3", "4", "8")) { v -> onUpdate { it.copy(maxParallelAgents = v.toInt()) } }
                ToggleRow("Snapshot before AI edit", settings.snapshotBeforeAiEdit) { v -> onUpdate { it.copy(snapshotBeforeAiEdit = v) } }
                ChoiceRow("Show diff before applying", settings.showDiffBeforeApply, listOf("always", "risky", "never")) { v -> onUpdate { it.copy(showDiffBeforeApply = v) } }
                ChoiceRow("Auto-apply patches", settings.autoApplyPatches, listOf("never", "approved", "trusted")) { v -> onUpdate { it.copy(autoApplyPatches = v) } }
            }

            SettingsSection("Terminal", Icons.Outlined.Terminal) {
                ChoiceRow("Font size", settings.terminalFontSize.toString(), listOf("11", "13", "15", "17")) { v -> onUpdate { it.copy(terminalFontSize = v.toInt()) } }
                ChoiceRow("Scrollback lines", settings.terminalScrollback.toString(), listOf("1000", "5000", "10000", "20000")) { v -> onUpdate { it.copy(terminalScrollback = v.toInt()) } }
                ToggleRow("Confirm terminal close", settings.confirmTerminalClose) { v -> onUpdate { it.copy(confirmTerminalClose = v) } }
            }

            SettingsSection("GitHub Actions / CI") {
                ToggleRow("Automatically analyze CI failures", settings.autoAnalyzeCiFailures) { v -> onUpdate { it.copy(autoAnalyzeCiFailures = v) } }
                ChoiceRow("Refresh interval", "${settings.ciRefreshSeconds}s", listOf("5s", "10s", "30s", "60s")) { v -> onUpdate { it.copy(ciRefreshSeconds = v.removeSuffix("s").toInt()) } }
            }

            SettingsSection("Projects") {
                ToggleRow("Remember open files", settings.rememberOpenFiles) { v -> onUpdate { it.copy(rememberOpenFiles = v) } }
                ToggleRow("Remember cursor position", settings.rememberCursorPosition) { v -> onUpdate { it.copy(rememberCursorPosition = v) } }
                ToggleRow("Remember Explorer state", settings.rememberExplorerState) { v -> onUpdate { it.copy(rememberExplorerState = v) } }
            }

            SettingsSection("Security") {
                ToggleRow("App lock", settings.appLock) { v -> onUpdate { it.copy(appLock = v) } }
            }

            AdvancedSettingsSection()

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Build architecture", style = MaterialTheme.typography.titleMedium)
                    Text("Nexus does not manage Android SDK, NDK, JDK, CMake, Flutter SDK, Dart SDK, Gradle installations, or local APK/AAB toolchains. Builds are delegated to GitHub Actions.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.padding(bottom = 12.dp))
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

@Composable
private fun ChoiceRow(title: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember(title) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, modifier = Modifier.weight(1f).padding(top = 14.dp))
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected.replace('_', ' ')) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option.replace('_', ' ')) }, onClick = { expanded = false; onSelect(option) }) }
            }
        }
    }
}
