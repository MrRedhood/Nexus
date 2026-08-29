package com.mrredhood.nexus.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.core.settings.GitCredentialStore
import com.mrredhood.nexus.core.settings.NexusSettings
import com.mrredhood.nexus.core.workspace.BuildArtifact
import com.mrredhood.nexus.core.workspace.BuildRun
import com.mrredhood.nexus.core.workspace.GitHubActionsBuildService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: NexusSettings, onUpdate: ((NexusSettings) -> NexusSettings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val gitCredentials = remember { GitCredentialStore(context) }
    val buildService = remember { GitHubActionsBuildService() }
    val scope = rememberCoroutineScope()
    var githubTokenConfigured by remember { mutableStateOf(gitCredentials.hasGithubToken()) }
    var buildRepository by remember { mutableStateOf("") }
    var buildVariant by remember { mutableStateOf("debug") }
    var buildRuns by remember { mutableStateOf<List<BuildRun>>(emptyList()) }
    var selectedArtifacts by remember { mutableStateOf<List<BuildArtifact>>(emptyList()) }
    var building by remember { mutableStateOf(false) }
    var buildError by remember { mutableStateOf<String?>(null) }
    var expandedBuildVariant by remember { mutableStateOf(false) }
    val permissionMode = when (settings.workspacePermission.lowercase()) {
        "restricted", "never" -> "never"
        "some", "standard" -> "some"
        else -> "autonomous"
    }

    fun refreshBuilds() {
        val repository = buildRepository.trim()
        val token = gitCredentials.githubToken()
        if (repository.isBlank() || token.isNullOrBlank()) return
        scope.launch {
            buildError = null
            runCatching { buildService.latestRuns(repository, token, "main") }
                .onSuccess { buildRuns = it }
                .onFailure { buildError = it.message ?: "Unable to load GitHub Actions runs." }
        }
    }

    fun startBuild() {
        val repository = buildRepository.trim()
        val token = gitCredentials.githubToken()
        if (repository.isBlank()) { buildError = "Enter the GitHub repository as owner/name."; return }
        if (token.isNullOrBlank()) { buildError = "Configure a GitHub token in Git credentials first."; return }
        scope.launch {
            building = true
            buildError = null
            selectedArtifacts = emptyList()
            runCatching { buildService.dispatch(repository, token, "main", buildVariant) }
                .onFailure { buildError = it.message ?: "Unable to start the build." }
            if (buildError == null) {
                repeat(60) {
                    delay(settings.ciRefreshSeconds.coerceIn(5, 60) * 1000L)
                    val runs = runCatching { buildService.latestRuns(repository, token, "main") }.getOrElse { emptyList() }
                    buildRuns = runs
                    val run = runs.firstOrNull()
                    if (run != null && run.isFinished) {
                        selectedArtifacts = runCatching { buildService.artifacts(repository, token, run.id) }.getOrElse { emptyList() }
                        return@repeat
                    }
                }
            }
            building = false
        }
    }

    LaunchedEffect(buildRepository, githubTokenConfigured) {
        if (githubTokenConfigured && buildRepository.isNotBlank()) refreshBuilds()
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
                Text("GitHub authentication is managed in the separate Git credentials screen.", style = MaterialTheme.typography.bodySmall)
            }
            SettingsSection("Cloud Build", Icons.Outlined.CloudUpload) {
                Text("Build Android APKs on GitHub Actions without installing SDKs in Nexus.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = buildRepository, onValueChange = { buildRepository = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Repository") }, placeholder = { Text("owner/name") }, singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expandedBuildVariant = true }, modifier = Modifier.fillMaxWidth()) { Text("${buildVariant.replaceFirstChar { it.uppercase() }} APK") }
                        DropdownMenu(expanded = expandedBuildVariant, onDismissRequest = { expandedBuildVariant = false }) {
                            listOf("debug", "release").forEach { variant -> DropdownMenuItem(text = { Text("${variant.replaceFirstChar { it.uppercase() }} APK") }, onClick = { buildVariant = variant; expandedBuildVariant = false }) }
                        }
                    }
                    FilledTonalButton(enabled = !building && githubTokenConfigured && buildRepository.isNotBlank(), onClick = ::startBuild) {
                        Icon(Icons.Outlined.CloudUpload, null); Spacer(Modifier.padding(2.dp)); Text(if (building) "Building…" else "Build")
                    }
                }
                if (building) Text("GitHub Actions is running. Nexus will keep polling the run and will not cancel it.", style = MaterialTheme.typography.labelMedium)
                buildError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                buildRuns.take(5).forEach { run ->
                    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(run.statusLabel(), style = MaterialTheme.typography.titleSmall); Text(run.commitSha.take(7), style = MaterialTheme.typography.labelMedium) }
                            Text("${run.branch} · ${run.createdAt.replace('T', ' ').substringBefore('.')}", style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(run.url))) }) { Icon(Icons.Outlined.OpenInNew, null); Spacer(Modifier.padding(2.dp)); Text("Open run") } }
                        }
                    }
                }
                selectedArtifacts.forEach { artifact ->
                    Card(shape = MaterialTheme.shapes.large) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text(artifact.name, style = MaterialTheme.typography.titleSmall); Text(formatBytes(artifact.sizeBytes), style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buildRuns.firstOrNull()?.url ?: "https://github.com"))) }) { Text("Open") }
                        }
                    }
                }
                if (buildRuns.isEmpty() && !building) Text("Enter a repository and configure a GitHub token in Git credentials to see build history.", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}

private fun BuildRun.statusLabel(): String = when {
    status == "completed" && conclusion == "success" -> "Passed"
    status == "completed" && conclusion == "failure" -> "Failed"
    status == "completed" -> "Completed · ${conclusion ?: "unknown"}"
    status == "in_progress" -> "Running"
    status == "queued" -> "Queued"
    else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

@Composable
fun SettingsSection(title: String, icon: ImageVector? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { if (icon != null) Icon(icon, null); Text(title, style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.padding(2.dp)); content()
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, modifier = Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange) }
}

@Composable
fun ChoiceRow(title: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember(title) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, modifier = Modifier.weight(1f).padding(top = 14.dp))
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected.replace('_', ' ')) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(option.replace('_', ' ')) }, onClick = { expanded = false; onSelect(option) }) } }
        }
    }
}
