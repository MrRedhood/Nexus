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
import androidx.compose.material.icons.outlined.Memory
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.core.settings.NexusSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: NexusSettings, onUpdate: ((NexusSettings) -> NexusSettings) -> Unit, onBack: () -> Unit) {
    val permissionMode = when (settings.workspacePermission.lowercase()) {
        "restricted", "never" -> "never"
        "some", "standard" -> "some"
        else -> "autonomous"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection("Appearance", Icons.Outlined.Tune) {
                ChoiceRow("Theme", settings.theme, listOf("system", "light", "dark", "amoled")) { v -> onUpdate { it.copy(theme = v) } }
                ChoiceRow("Accent", settings.accent, listOf("blue", "purple", "cyan", "green", "orange", "red")) { v -> onUpdate { it.copy(accent = v) } }
                ToggleRow("Fullscreen", settings.fullscreen) { v -> onUpdate { it.copy(fullscreen = v) } }
            }

            SettingsSection("Editor", Icons.Outlined.Code) {
                ChoiceRow("Font", settings.editorFont, listOf("JetBrains Mono", "Roboto Mono", "Fira Code", "Source Code Pro", "Ubuntu Mono", "System Mono")) { v -> onUpdate { it.copy(editorFont = v) } }
                ChoiceRow("Font size", settings.editorFontSize.toString(), (10..24 step 2).map(Int::toString)) { v -> onUpdate { it.copy(editorFontSize = v.toInt()) } }
                ChoiceRow("Tab size", settings.tabSize.toString(), listOf("2", "4", "8")) { v -> onUpdate { it.copy(tabSize = v.toInt()) } }
                ChoiceRow("Word wrap", settings.wordWrap, listOf("off", "on", "editor_width")) { v -> onUpdate { it.copy(wordWrap = v) } }
                ToggleRow("Syntax highlighting", settings.syntaxHighlighting) { v -> onUpdate { it.copy(syntaxHighlighting = v) } }
                ToggleRow("Auto-indent", settings.autoIndent) { v -> onUpdate { it.copy(autoIndent = v) } }
                ToggleRow("Auto-close brackets", settings.autoCloseBrackets) { v -> onUpdate { it.copy(autoCloseBrackets = v) } }
                ToggleRow("Auto-close tags", settings.autoCloseTags) { v -> onUpdate { it.copy(autoCloseTags = v) } }
            }

            SettingsSection("Workspace", Icons.Outlined.Memory) {
                ChoiceRow("Indexing", settings.indexing, listOf("automatic", "on_open", "manual", "disabled")) { v -> onUpdate { it.copy(indexing = v) } }
                ChoiceRow("Workspace context", settings.workspaceContext, listOf("never", "smart", "always")) { v -> onUpdate { it.copy(workspaceContext = v) } }
                ToggleRow("Diagnostics", settings.diagnostics) { v -> onUpdate { it.copy(diagnostics = v) } }
                ToggleRow("Format on save", settings.formatOnSave) { v -> onUpdate { it.copy(formatOnSave = v) } }
            }

            SettingsSection("AI", Icons.Outlined.SmartToy) {
                ChoiceRow("AI permission", permissionMode, listOf("never", "some", "autonomous")) { v -> onUpdate { it.copy(workspacePermission = v) } }
                ToggleRow("Streaming", settings.aiStreaming) { v -> onUpdate { it.copy(aiStreaming = v) } }
                ChoiceRow("Context files", settings.maxContextFiles.toString(), listOf("5", "10", "20")) { v -> onUpdate { it.copy(maxContextFiles = v.toInt()) } }
                ToggleRow("Include current file", settings.includeCurrentFile) { v -> onUpdate { it.copy(includeCurrentFile = v) } }
                ToggleRow("Include Git diff", settings.includeGitDiff) { v -> onUpdate { it.copy(includeGitDiff = v) } }
                ToggleRow("Include workspace summary", settings.includeWorkspaceSummary) { v -> onUpdate { it.copy(includeWorkspaceSummary = v) } }
            }

            SettingsSection("Terminal", Icons.Outlined.Terminal) {
                ChoiceRow("Font size", settings.terminalFontSize.toString(), listOf("11", "13", "15", "17")) { v -> onUpdate { it.copy(terminalFontSize = v.toInt()) } }
                ChoiceRow("Scrollback", settings.terminalScrollback.toString(), listOf("1000", "5000", "10000", "20000")) { v -> onUpdate { it.copy(terminalScrollback = v.toInt()) } }
            }

            SettingsSection("GitHub Actions / CI") {
                ToggleRow("Analyze CI failures automatically", settings.autoAnalyzeCiFailures) { v -> onUpdate { it.copy(autoAnalyzeCiFailures = v) } }
                ChoiceRow("Refresh interval", "${settings.ciRefreshSeconds}s", listOf("5s", "10s", "30s", "60s")) { v -> onUpdate { it.copy(ciRefreshSeconds = v.removeSuffix("s").toInt()) } }
            }

            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
fun SettingsSection(title: String, icon: ImageVector? = null, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
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
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ChoiceRow(title: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember(title) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, modifier = Modifier.weight(1f).padding(top = 14.dp))
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected.replace('_', ' '))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.replace('_', ' ')) },
                        onClick = { expanded = false; onSelect(option) }
                    )
                }
            }
        }
    }
}
