package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.mrredhood.nexus.core.settings.GitCredentialStore

@Composable
fun GitCredentialsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { GitCredentialStore(context) }
    var httpsUsername by remember { mutableStateOf(store.httpsUsername().orEmpty()) }
    var httpsPassword by remember { mutableStateOf(store.httpsPassword().orEmpty()) }
    var sshKey by remember { mutableStateOf(store.sshPrivateKey().orEmpty()) }
    var sshPassphrase by remember { mutableStateOf(store.sshPassphrase().orEmpty()) }
    var knownHosts by remember { mutableStateOf(store.knownHosts().orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Git credentials") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Outlined.Security, null); Text("Native Git authentication", style = MaterialTheme.typography.titleLarge) }
                        Text("HTTPS and SSH credentials are stored separately from GitHub API tokens and AI-provider keys. They are encrypted at rest.", style = MaterialTheme.typography.bodyMedium)
                        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Outlined.Key, null); Text("HTTPS", style = MaterialTheme.typography.titleMedium) }
                        Text("Use a Git username and password/PAT for HTTPS remotes.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(httpsUsername, { httpsUsername = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(httpsPassword, { httpsPassword = it }, label = { Text("Password / PAT") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { store.setHttpsUsername(httpsUsername.trim()); store.setHttpsPassword(httpsPassword); message = "HTTPS credentials saved." }) { Text("Save HTTPS") }
                            TextButton(onClick = { store.clearHttps(); httpsUsername = ""; httpsPassword = ""; message = "HTTPS credentials removed." }) { Text("Remove") }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Outlined.Key, null); Text("SSH", style = MaterialTheme.typography.titleMedium) }
                        Text("Paste an OpenSSH private key. The passphrase is optional. known_hosts can be supplied for strict host verification.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(sshKey, { sshKey = it }, label = { Text("Private key") }, minLines = 5, maxLines = 9, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(sshPassphrase, { sshPassphrase = it }, label = { Text("Passphrase (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                        OutlinedTextField(knownHosts, { knownHosts = it }, label = { Text("known_hosts (optional)") }, minLines = 3, maxLines = 7, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { store.setSshPrivateKey(sshKey); store.setSshPassphrase(sshPassphrase); store.setKnownHosts(knownHosts); message = "SSH credentials saved." }) { Text("Save SSH") }
                            TextButton(onClick = { store.clearSsh(); sshKey = ""; sshPassphrase = ""; knownHosts = ""; message = "SSH credentials removed." }) { Text("Remove") }
                        }
                    }
                }
            }
            item { Text("Nexus never includes these credentials in AI prompts, project files, normal logs, or GitHub API payloads.", style = MaterialTheme.typography.labelSmall) }
        }
    }
}
