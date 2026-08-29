package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.nexus.core.ai.AiProviderRegistry
import com.mrredhood.nexus.core.settings.AdvancedSettingsRepository
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun AiProvidersScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val registry = remember { AiProviderRegistry(context) }
    val settings = remember { AdvancedSettingsRepository(context) }
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf(registry.list()) }
    var editing by remember { mutableStateOf<AiProviderRegistry.Provider?>(null) }
    var adding by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("AI model providers") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, "Add provider") } }
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Outlined.AutoAwesome, null); Text("API model providers", style = MaterialTheme.typography.titleLarge) }
                        Text("Manage API keys, models and endpoints independently from Settings, GitHub and Copilot.", style = MaterialTheme.typography.bodyMedium)
                        status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            items(providers, key = { it.name.lowercase() }) { provider ->
                val hasKey = registry.hasKey(provider.name)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.Key, null)
                            Column(Modifier.weight(1f)) {
                                Text(provider.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (hasKey) "API key configured" else "API key missing", style = MaterialTheme.typography.labelMedium, color = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                Text("Model · ${provider.model}", style = MaterialTheme.typography.bodySmall)
                                if (provider.endpoint.isNotBlank()) Text(provider.endpoint, style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { editing = provider }) { Icon(Icons.Outlined.Visibility, "Manage provider") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(enabled = hasKey && provider.model.isNotBlank() && provider.model != "default", onClick = {
                                scope.launch {
                                    settings.update { it.copy(provider = provider.name, model = provider.model, endpoint = provider.endpoint, apiKeyConfigured = true) }
                                    status = "${provider.name} / ${provider.model} selected for Nexus AI."
                                }
                            }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PlayArrow, null); Text("Use provider", Modifier.padding(start = 6.dp)) }
                            TextButton(onClick = { registry.remove(provider.name); providers = registry.list(); status = "Removed ${provider.name}." }) { Icon(Icons.Outlined.Delete, null); Text("Remove") }
                        }
                    }
                }
            }
            item { FilledTonalButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("Add API model provider", Modifier.padding(start = 6.dp)) } }
        }
    }

    if (adding || editing != null) {
        ProviderEditorDialog(
            initial = editing,
            existingKey = editing?.let(registry::getKey).orEmpty(),
            onDismiss = { adding = false; editing = null },
            onSave = { name, model, endpoint, key ->
                registry.save(AiProviderRegistry.Provider(name, model, endpoint), key)
                providers = registry.list()
                adding = false
                editing = null
                status = "Saved $name securely."
            }
        )
    }
}

@Composable
private fun ProviderEditorDialog(initial: AiProviderRegistry.Provider?, existingKey: String, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember(initial?.name) { mutableStateOf(initial?.name.orEmpty()) }
    var model by remember(initial?.model) { mutableStateOf(initial?.model.orEmpty()) }
    var endpoint by remember(initial?.endpoint) { mutableStateOf(initial?.endpoint.orEmpty()) }
    var key by remember(initial?.name) { mutableStateOf(existingKey) }
    var showKey by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add API provider" else "Edit API provider") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Provider name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, placeholder = { Text("gpt-4.1-mini") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Endpoint (optional)") }, placeholder = { Text("OpenAI-compatible / custom endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(Icons.Outlined.Visibility, "Show or hide API key") } })
            Text("API keys are encrypted with Android Keystore and are never stored in project files or sent to AI context.", style = MaterialTheme.typography.labelSmall)
        } },
        confirmButton = { FilledTonalButton(enabled = name.isNotBlank() && model.isNotBlank() && key.isNotBlank(), onClick = { onSave(name.trim(), model.trim(), endpoint.trim(), key) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
