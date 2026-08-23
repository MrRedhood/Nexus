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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
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

@Composable
fun SettingsScreen(
    settings: NexusSettings,
    onUpdate: ((NexusSettings) -> NexusSettings) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection("Appearance", Icons.Outlined.Tune) {
                ChoiceRow("Theme", settings.theme, listOf("system", "light", "dark", "amoled")) { value ->
                    onUpdate { current -> current.copy(theme = value) }
                }
                ChoiceRow("Accent color", settings.accent, listOf("blue", "purple", "cyan", "green", "orange", "red")) { value ->
                    onUpdate { current -> current.copy(accent = value) }
                }
                ChoiceRow("UI scale", "${(settings.uiScale * 100).toInt()}%", listOf("80%", "90%", "100%", "110%", "120%", "130%")) { value ->
                    onUpdate { current -> current.copy(uiScale = value.removeSuffix("%").toFloat() / 100f) }
                }
                ToggleRow("Compact mode", settings.compactMode) { value ->
                    onUpdate { current -> current.copy(compactMode = value) }
                }
                ToggleRow("Fullscreen", settings.fullscreen) { value ->
                    onUpdate { current -> current.copy(fullscreen = value) }
                }
                ToggleRow("Immersive coding mode", settings.immersiveCoding) { value ->
                    onUpdate { current -> current.copy(immersiveCoding = value) }
                }
            }

            SettingsSection("Editor", Icons.Outlined.Code) {
                ChoiceRow("Font", settings.editorFont, listOf("JetBrains Mono", "Roboto Mono", "Fira Code", "Source Code Pro", "Ubuntu Mono", "System Mono")) { value ->
                    onUpdate { current -> current.copy(editorFont = value) }
                }
                ChoiceRow("Font size", settings.editorFontSize.toString(), (10..24 step 2).map(Int::toString)) { value ->
                    onUpdate { current -> current.copy(editorFontSize = value.toInt()) }
                }
                ChoiceRow("Word wrap", settings.wordWrap, listOf("off", "on", "editor_width", "custom")) { value ->
                    onUpdate { current -> current.copy(wordWrap = value) }
                }
                ChoiceRow("Tab size", settings.tabSize.toString(), listOf("2", "4", "8")) { value ->
                    onUpdate { current -> current.copy(tabSize = value.toInt()) }
                }
                ToggleRow("Use spaces", settings.useSpaces) { value ->
                    onUpdate { current -> current.copy(useSpaces = value) }
                }
                ToggleRow("Auto-indent", settings.autoIndent) { value ->
                    onUpdate { current -> current.copy(autoIndent = value) }
                }
                ToggleRow("Auto-close brackets", settings.autoCloseBrackets) { value ->
                    onUpdate { current -> current.copy(autoCloseBrackets = value) }
                }
                ToggleRow("Auto-close tags", settings.autoCloseTags) { value ->
                    onUpdate { current -> current.copy(autoCloseTags = value) }
                }
                ToggleRow("Auto-rename tags", settings.autoRenameTags) { value ->
                    onUpdate { current -> current.copy(autoRenameTags = value) }
                }
                ToggleRow("Syntax highlighting", settings.syntaxHighlighting) { value ->
                    onUpdate { current -> current.copy(syntaxHighlighting = value) }
                }
                ToggleRow("Rainbow brackets", settings.rainbowBrackets) { value ->
                    onUpdate { current -> current.copy(rainbowBrackets = value) }
                }
                ChoiceRow("Line numbers", settings.lineNumbers, listOf("off", "absolute", "relative")) { value ->
                    onUpdate { current -> current.copy(lineNumbers = value) }
                }
                ToggleRow("Current line highlight", settings.currentLineHighlight) { value ->
                    onUpdate { current -> current.copy(currentLineHighlight = value) }
                }
                ToggleRow("Indent guides", settings.indentGuides) { value ->
                    onUpdate { current -> current.copy(indentGuides = value) }
                }
                ToggleRow("Show whitespace", settings.showWhitespace) { value ->
                    onUpdate { current -> current.copy(showWhitespace = value) }
                }
                ToggleRow("Code folding", settings.codeFolding) { value ->
                    onUpdate { current -> current.copy(codeFolding = value) }
                }
                ToggleRow("Minimap", settings.minimap) { value ->
                    onUpdate { current -> current.copy(minimap = value) }
                }
            }

            SettingsSection("Files & Explorer", Icons.Outlined.Folder) {
                ChoiceRow("Sort by", settings.explorerSort, listOf("name", "type", "size", "modified", "created")) { value ->
                    onUpdate { current -> current.copy(explorerSort = value) }
                }
                ToggleRow("Descending", settings.explorerDescending) { value ->
                    onUpdate { current -> current.copy(explorerDescending = value) }
                }
                ToggleRow("Folders first", settings.foldersFirst) { value ->
                    onUpdate { current -> current.copy(foldersFirst = value) }
                }
                ChoiceRow("View", settings.explorerView, listOf("list", "compact", "grid")) { value ->
                    onUpdate { current -> current.copy(explorerView = value) }
                }
                ToggleRow("Show hidden files", settings.showHiddenFiles) { value ->
                    onUpdate { current -> current.copy(showHiddenFiles = value) }
                }
                ToggleRow("Show full path", settings.showFullPath) { value ->
                    onUpdate { current -> current.copy(showFullPath = value) }
                }
            }

            SettingsSection("Workspace", Icons.Outlined.Memory) {
                ChoiceRow("AI permission mode", settings.workspacePermission, listOf("restricted", "standard", "full")) { value ->
                    onUpdate { current -> current.copy(workspacePermission = value) }
                }
                ChoiceRow("Indexing", settings.indexing, listOf("automatic", "on_open", "manual", "disabled")) { value ->
                    onUpdate { current -> current.copy(indexing = value) }
                }
                ChoiceRow("Automatic workspace context", settings.workspaceContext, listOf("never", "smart", "always")) { value ->
                    onUpdate { current -> current.copy(workspaceContext = value) }
                }
            }

            SettingsSection("AI Context", Icons.Outlined.SmartToy) {
                ToggleRow("Streaming", settings.aiStreaming) { value ->
                    onUpdate { current -> current.copy(aiStreaming = value) }
                }
                ChoiceRow("Maximum context files", settings.maxContextFiles.toString(), listOf("3", "5", "10", "20")) { value ->
                    onUpdate { current -> current.copy(maxContextFiles = value.toInt()) }
                }
                ToggleRow("Current file", settings.includeCurrentFile) { value ->
                    onUpdate { current -> current.copy(includeCurrentFile = value) }
                }
                ToggleRow("Selection", settings.includeSelection) { value ->
                    onUpdate { current -> current.copy(includeSelection = value) }
                }
                ToggleRow("Git diff", settings.includeGitDiff) { value ->
                    onUpdate { current -> current.copy(includeGitDiff = value) }
                }
                ToggleRow("Terminal output", settings.includeTerminalContext) { value ->
                    onUpdate { current -> current.copy(includeTerminalContext = value) }
                }
                ToggleRow("Workspace summary", settings.includeWorkspaceSummary) { value ->
                    onUpdate { current -> current.copy(includeWorkspaceSummary = value) }
                }
            }

            SettingsSection("Agents & Safety", Icons.Outlined.Security) {
                ChoiceRow("Agent autonomy", settings.agentAutonomy, listOf("ask_every_time", "approve_risky", "autonomous")) { value ->
                    onUpdate { current -> current.copy(agentAutonomy = value) }
                }
                ChoiceRow("Maximum parallel agents", settings.maxParallelAgents.toString(), listOf("1", "2", "3", "4", "8")) { value ->
                    onUpdate { current -> current.copy(maxParallelAgents = value.toInt()) }
                }
                ToggleRow("Snapshot before AI edit", settings.snapshotBeforeAiEdit) { value ->
                    onUpdate { current -> current.copy(snapshotBeforeAiEdit = value) }
                }
                ChoiceRow("Show diff before applying", settings.showDiffBeforeApply, listOf("always", "risky", "never")) { value ->
                    onUpdate { current -> current.copy(showDiffBeforeApply = value) }
                }
                ChoiceRow("Auto-apply patches", settings.autoApplyPatches, listOf("never", "approved", "trusted")) { value ->
                    onUpdate { current -> current.copy(autoApplyPatches = value) }
                }
            }

            SettingsSection("Terminal", Icons.Outlined.Terminal) {
                ChoiceRow("Font size", settings.terminalFontSize.toString(), listOf("11", "13", "15", "17")) { value ->
                    onUpdate { current -> current.copy(terminalFontSize = value.toInt()) }
                }
                ToggleRow("Confirm terminal close", settings.confirmTerminalClose) { value ->
                    onUpdate { current -> current.copy(confirmTerminalClose = value) }
                }
            }

            SettingsSection("GitHub Actions / CI") {
                ToggleRow("Automatically analyze CI failures", settings.autoAnalyzeCiFailures) { value ->
                    onUpdate { current -> current.copy(autoAnalyzeCiFailures = value) }
                }
                ChoiceRow("Refresh interval", "${settings.ciRefreshSeconds}s", listOf("5s", "10s", "30s", "60s")) { value ->
                    onUpdate { current -> current.copy(ciRefreshSeconds = value.removeSuffix("s").toInt()) }
                }
            }

            SettingsSection("Projects") {
                ToggleRow("Remember open files", settings.rememberOpenFiles) { value ->
                    onUpdate { current -> current.copy(rememberOpenFiles = value) }
                }
                ToggleRow("Remember cursor position", settings.rememberCursorPosition) { value ->
                    onUpdate { current -> current.copy(rememberCursorPosition = value) }
                }
                ToggleRow("Remember Explorer state", settings.rememberExplorerState) { value ->
                    onUpdate { current -> current.copy(rememberExplorerState = value) }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Build architecture", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Nexus does not manage Android SDK, NDK, JDK, CMake, Flutter SDK, Dart SDK, Gradle installations, or local APK/AAB toolchains. Builds are delegated to GitHub Actions.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) Icon(icon, null)
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.padding(2.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(
    title: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember(title) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, modifier = Modifier.weight(1f).padding(top = 14.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.menuAnchor()
            ) {
                Text(selected.replace('_', ' '))
            }
            androidx.compose.material3.ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.replace('_', ' ')) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}
