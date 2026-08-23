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
    var keyProvider by remember { mutableStateOf(settings.provider) }

    LaunchedEffect(settings.provider) {
        if (keyProvider != settings.provider) {
            keyProvider = settings.provider
            apiKey = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSection("AI Providers & Models") {
            ChoiceRow("Provider", settings.provider, listOf("Gemini", "OpenRouter", "DeepInfra", "LiteLLM")) { v ->
                apiKey = ""
                keyProvider = v
                vm.update { it.copy(provider = v, apiKeyConfigured = vm.hasApiKey(v)) }
            }
            ChoiceRow("Model", settings.model, listOf("default", "fast", "balanced", "reasoning")) { v -> vm.update { it.copy(model = v) } }

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
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save API key") }
            if (vm.hasApiKey(settings.provider)) {
                TextButton(onClick = { vm.clearApiKey(settings.provider); apiKey = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove stored key")
                }
            }

            OutlinedTextField(
                value = settings.endpoint,
                onValueChange = { vm.update { current -> current.copy(endpoint = it) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom endpoint (optional)") },
                placeholder = { Text("Provider-specific API URL") },
                singleLine = true
            )
            Text(
                "Keys are encrypted with Android Keystore. Nexus never stores the raw key in DataStore or project files.",
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
