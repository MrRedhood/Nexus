package com.mrredhood.nexus.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.nexus.core.ai.AiModel

private val AI_PROVIDERS = listOf("Gemini", "OpenRouter", "DeepInfra", "LiteLLM")

@Composable
fun AdvancedSettingsSection() {
    val vm: AdvancedSettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState(); val models by vm.models.collectAsState(); val loadingModels by vm.loadingModels.collectAsState(); val modelError by vm.modelError.collectAsState()
    var apiKey by remember { mutableStateOf("") }; var showApiKey by remember { mutableStateOf(false) }
    var testRunning by remember { mutableStateOf(false) }; var testMessage by remember { mutableStateOf<String?>(null) }; var testSuccess by remember { mutableStateOf(false) }
    var endpoint by remember(settings.provider) { mutableStateOf(settings.endpoint) }; var filter by remember { mutableStateOf("all") }; var modelMenu by remember { mutableStateOf(false) }

    LaunchedEffect(settings.provider) { apiKey = ""; showApiKey = false; testMessage = null; endpoint = settings.endpoint; filter = "all"; vm.loadModels(settings.provider) }
    val filteredModels = models.filter { when (filter) { "free" -> it.pricing.free; "premium" -> it.premium; "image" -> it.image; "video" -> it.video; "audio" -> it.audio; "text" -> it.text; else -> true } }
    val keyConfigured = vm.hasApiKey(settings.provider)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSection("AI Providers & Models") {
            ChoiceRow("Provider", settings.provider, AI_PROVIDERS) { v -> vm.update { it.copy(provider = v, apiKeyConfigured = vm.hasApiKey(v), model = "default") }; vm.loadModels(v) }
            Text("Model", style = MaterialTheme.typography.labelLarge)
            Box(Modifier.fillMaxWidth()) {
                Button(onClick = { modelMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = filteredModels.isNotEmpty()) { Text(settings.model.takeUnless { it == "default" } ?: if (loadingModels) "Loading models…" else "Select a model") }
                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) { filteredModels.forEach { model -> DropdownMenuItem(text = { ModelMenuText(model) }, onClick = { vm.update { it.copy(model = model.id) }; modelMenu = false }) } }
            }
            Text("${filteredModels.size} models", style = MaterialTheme.typography.bodySmall)
            FilterRow(filter) { filter = it }
            if (loadingModels) Text("Loading live models from ${settings.provider}…", style = MaterialTheme.typography.bodySmall)
            modelError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { vm.loadModels(settings.provider) }) { Text("Refresh model list") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.VpnKey, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text("API key storage", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (keyConfigured) "A key is securely stored for ${settings.provider}."
                        else "No key is stored for ${settings.provider}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (keyConfigured) Icon(Icons.Outlined.CheckCircle, contentDescription = "Configured", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Keys are encrypted at rest with Android Keystore. Nexus stores only encrypted key material locally and never displays the saved secret.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (keyConfigured) "Replace ${settings.provider} API key" else "${settings.provider} API key") },
                placeholder = { Text("Paste your API key") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (showApiKey) "Hide API key" else "Show API key")
                    }
                },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = apiKey.isNotBlank(), onClick = { vm.saveApiKey(settings.provider, apiKey); apiKey = ""; showApiKey = false; testMessage = null }, modifier = Modifier.weight(1f)) {
                    Text(if (keyConfigured) "Replace key" else "Save key")
                }
                if (keyConfigured) OutlinedButton(onClick = { vm.clearApiKey(settings.provider); apiKey = ""; showApiKey = false; testMessage = null }) { Text("Remove") }
            }
            if (keyConfigured) Text("Saved securely on this device · secret value hidden", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Custom endpoint (optional)") }, placeholder = { Text(defaultEndpointHint(settings.provider)) }, singleLine = true)
            Button(onClick = { vm.update { it.copy(endpoint = endpoint.trim()) }; vm.loadModels(settings.provider) }, modifier = Modifier.fillMaxWidth()) { Text("Save endpoint") }
            Button(enabled = keyConfigured && !testRunning, onClick = { testRunning = true; testMessage = null; vm.testConnection { success, message -> testRunning = false; testSuccess = success; testMessage = message } }, modifier = Modifier.fillMaxWidth()) { Text(if (testRunning) "Testing connection…" else "Test connection") }
            testMessage?.let { Text(it, color = if (testSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text("Each provider has its own encrypted key. Changing providers never replaces another provider's saved key.", style = MaterialTheme.typography.bodySmall)
        }
        SettingsSection("GitHub") { ToggleRow("Automatic repository sync", settings.githubAutoSync) { v -> vm.update { it.copy(githubAutoSync = v) } }; ToggleRow("Fetch remote branches", settings.githubFetchBranches) { v -> vm.update { it.copy(githubFetchBranches = v) } }; ChoiceRow("Default branch", settings.githubDefaultBranch, listOf("main", "master", "develop")) { v -> vm.update { it.copy(githubDefaultBranch = v) } } }
        SettingsSection("Memory") { ToggleRow("Enable persistent memory", settings.memoryEnabled) { v -> vm.update { it.copy(memoryEnabled = v) } }; ChoiceRow("Retention", "${settings.memoryRetentionDays} days", listOf("7 days", "30 days", "90 days", "365 days")) { v -> vm.update { it.copy(memoryRetentionDays = v.removeSuffix(" days").toInt()) } } }
        SettingsSection("Notifications") { ToggleRow("Notifications", settings.notificationsEnabled) { v -> vm.update { it.copy(notificationsEnabled = v) } }; ToggleRow("Notification sound", settings.notificationSound) { v -> vm.update { it.copy(notificationSound = v) } }; ToggleRow("CI notifications", settings.ciNotifications) { v -> vm.update { it.copy(ciNotifications = v) } } }
        SettingsSection("Storage & Cache") { ChoiceRow("Cache limit", "${settings.cacheLimitMb} MB", listOf("64 MB", "128 MB", "256 MB", "512 MB")) { v -> vm.update { it.copy(cacheLimitMb = v.removeSuffix(" MB").toInt()) } }; ToggleRow("Clear cache on exit", settings.clearCacheOnExit) { v -> vm.update { it.copy(clearCacheOnExit = v) } } }
        SettingsSection("Plugins") { ToggleRow("Enable plugins", settings.pluginsEnabled) { v -> vm.update { it.copy(pluginsEnabled = v) } } }
        SettingsSection("Privacy & Updates") { ToggleRow("Send crash reports", settings.crashReports) { v -> vm.update { it.copy(crashReports = v) } }; ToggleRow("Anonymous analytics", settings.analytics) { v -> vm.update { it.copy(analytics = v) } }; ToggleRow("Automatically check for updates", settings.autoCheckUpdates) { v -> vm.update { it.copy(autoCheckUpdates = v) } } }
        Spacer(Modifier.padding(bottom = 4.dp))
    }
}

@Composable private fun ModelMenuText(model: AiModel) { Column { Text(model.name.ifBlank { model.id }, maxLines = 1); Text(buildString { append(model.id); if (model.premium) append(" · Premium"); else if (model.pricing.free) append(" · Free"); model.modalities.forEach { append(" · $it") } }, style = MaterialTheme.typography.labelSmall, maxLines = 1) } }
@Composable private fun FilterRow(selected: String, onSelect: (String) -> Unit) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) { listOf("all", "free", "premium", "text", "image", "video", "audio").forEach { value -> TextButton(onClick = { onSelect(value) }) { Text(if (selected == value) "✓ ${value.replaceFirstChar { it.uppercase() }}" else value.replaceFirstChar { it.uppercase() }) } } }
}
private fun defaultEndpointHint(provider: String): String = when {
    provider.equals("OpenRouter", true) -> "https://openrouter.ai/api/v1/chat/completions"
    provider.equals("DeepInfra", true) -> "https://api.deepinfra.com/v1/openai/chat/completions"
    provider.equals("LiteLLM", true) -> "http://localhost:4000/v1/chat/completions"
    else -> "Gemini endpoint is built in"
}
