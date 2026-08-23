package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.nexus.core.settings.NexusFeatureSettings

@Composable
fun AdvancedSettingsSection() {
    val vm: AdvancedSettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSection("AI Providers & Models") {
            ChoiceRow("Provider", settings.provider, listOf("Gemini", "OpenRouter", "DeepInfra", "LiteLLM")) { v -> vm.update { it.copy(provider = v) } }
            ChoiceRow("Model", settings.model, listOf("default", "fast", "balanced", "reasoning")) { v -> vm.update { it.copy(model = v) } }
            ToggleRow("API key configured", settings.apiKeyConfigured) { v -> vm.update { it.copy(apiKeyConfigured = v) } }
            Text("API keys are represented as configuration state here; secret storage and provider clients should use Android Keystore when provider execution is wired.", style = MaterialTheme.typography.bodySmall)
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
    }
}
