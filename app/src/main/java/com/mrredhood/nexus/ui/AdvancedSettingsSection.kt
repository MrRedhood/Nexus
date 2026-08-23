package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AdvancedSettingsSection() {
    val vm: AdvancedSettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    var apiKey by remember { mutableStateOf("") }
    var testRunning by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }
    var model by remember(settings.provider) { mutableStateOf(settings.model.takeUnless { it == "default" }.orEmpty()) }
    var endpoint by remember(settings.provider) { mutableStateOf(settings.endpoint) }

    LaunchedEffect(settings.provider) {
        apiKey = ""
        testMessage = null
        model = settings.model.takeUnless { it == "default" }.orEmpty()
        endpoint = settings.endpoint
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSection("AI Providers & Models") {
            ChoiceRow("Provider", settings.provider, listOf("Gemini", "OpenRouter", "DeepInfra", "LiteLLM")) { v ->
                vm.update { it.copy(provider = v, apiKeyConfigured = vm.hasApiKey(v)) }
            }

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model ID") },
                placeholder = { Text(defaultModelHint(settings.provider)) },
                singleLine = true
            )
            Button(
                onClick = { vm.update { it.copy(model = model.trim().ifBlank { "default" }) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save model") }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("${settings.provider} API key") },
                placeholder = { Text("Enter key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Text(
                if (vm.hasApiKey(settings.provider)) "API key is securely stored on this device."
                else "No API key stored for this provider.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                enabled = apiKey.isNotBlank(),
                onClick = {
                    vm.saveApiKey(settings.provider, apiKey.trim())
                    apiKey = ""
                    testMessage = null
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save API key") }
            if (vm.hasApiKey(settings.provider)) {
                TextButton(onClick = { vm.clearApiKey(settings.provider); apiKey = ""; testMessage = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove stored key")
                }
            }

            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom endpoint (optional)") },
                placeholder = { Text(defaultEndpointHint(settings.provider)) },
                singleLine = true
            )
            Button(
                onClick = { vm.update { it.copy(endpoint = endpoint.trim()) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save endpoint") }

            Button(
                enabled = vm.hasApiKey(settings.provider) && !testRunning,
                onClick = {
                    testRunning = true
                    testMessage = null
                    vm.testConnection { success, message ->
                        testRunning = false
                        testSuccess = success
                        testMessage = message
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (testRunning) "Testing connection…" else "Test connection") }

            testMessage?.let {
                Text(
                    it,
                    color = if (testSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "Keys are encrypted with Android Keystore. Nexus never stores the raw key in DataStore or project files. Provider execution uses a common HTTP interface; no provider SDK is bundled.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        SettingsSection("GitHub") {
            ToggleRow("Automatic repository sync", settings.githubAutoSync) { v -> vm.update { it.copy(githubAutoSync = v) } }
            ToggleRow("Fetch remote branches", settings.githubFetchBranches) { v -> vm.update { it.copy(githubFetchBranches = v) } }
            ChoiceRow("Default branch", settings.githubDefaultBranch, listOf("main", "master", "develop")) { v -> vm.update { it.copy(githubDefaultBranch = v) } }
        }

        SettingsSection("Memory") {
            ToggleRow("Enable persistent memory", settings.memoryEnabled) { v -> vm.update { it.copy(memoryEnabled = v) } }
            ChoiceRow("Retention", "${settings.memoryRetentionDays} days", listOf("7 days", "30 days", "90 days", "365 days")) { v -> vm.update { it.copy(memoryRetentionDays = v.removeSuffix(" days").toInt()) } }
        }

        SettingsSection("Notifications") {
            ToggleRow("Notifications", settings.notificationsEnabled) { v -> vm.update { it.copy(notificationsEnabled = v) } }
            ToggleRow("Notification sound", settings.notificationSound) { v -> vm.update { it.copy(notificationSound = v) } }
            ToggleRow("CI notifications", settings.ciNotifications) { v -> vm.update { it.copy(ciNotifications = v) } }
        }

        SettingsSection("Storage & Cache") {
            ChoiceRow("Cache limit", "${settings.cacheLimitMb} MB", listOf("64 MB", "128 MB", "256 MB", "512 MB")) { v -> vm.update { it.copy(cacheLimitMb = v.removeSuffix(" MB").toInt()) } }
            ToggleRow("Clear cache on exit", settings.clearCacheOnExit) { v -> vm.update { it.copy(clearCacheOnExit = v) } }
        }

        SettingsSection("Plugins") {
            ToggleRow("Enable plugins", settings.pluginsEnabled) { v -> vm.update { it.copy(pluginsEnabled = v) } }
        }

        SettingsSection("Privacy & Updates") {
            ToggleRow("Send crash reports", settings.crashReports) { v -> vm.update { it.copy(crashReports = v) } }
            ToggleRow("Anonymous analytics", settings.analytics) { v -> vm.update { it.copy(analytics = v) } }
            ToggleRow("Automatically check for updates", settings.autoCheckUpdates) { v -> vm.update { it.copy(autoCheckUpdates = v) } }
        }

        Spacer(Modifier.padding(bottom = 4.dp))
    }
}

private fun defaultModelHint(provider: String): String = when {
    provider.equals("Gemini", true) -> "gemini-2.5-flash"
    provider.equals("OpenRouter", true) -> "google/gemini-2.5-flash"
    provider.equals("DeepInfra", true) -> "meta-llama/Llama-3.3-70B-Instruct"
    else -> "Model ID exposed by your LiteLLM server"
}

private fun defaultEndpointHint(provider: String): String = when {
    provider.equals("OpenRouter", true) -> "https://openrouter.ai/api/v1/chat/completions"
    provider.equals("DeepInfra", true) -> "https://api.deepinfra.com/v1/openai/chat/completions"
    provider.equals("LiteLLM", true) -> "http://localhost:4000/v1/chat/completions"
    else -> "Gemini endpoint is built in"
}
