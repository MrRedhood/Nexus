package com.mrredhood.nexus.ui.connectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.domain.connector.ConnectorStateStore
import com.mrredhood.nexus.domain.connector.ConnectorStatus
import com.mrredhood.nexus.domain.permission.PermissionScope
import com.mrredhood.nexus.ui.components.NexusCard

@Composable
fun ConnectorsScreen(modifier: Modifier = Modifier) {
    val store = remember { ConnectorStateStore() }
    var states by remember { mutableStateOf(store.all()) }

    fun refreshState() {
        states = store.all()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Connectors", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Connect only the services you choose. Nexus keeps connector capabilities separate from AI access.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        items(states, key = { it.manifest.id }) { state ->
            ConnectorCard(
                name = state.manifest.name,
                status = state.status,
                account = state.accountLabel,
                permissions = state.manifest.permissions,
                onConnect = {
                    store.connect(state.manifest.id, "Account not configured")
                    refreshState()
                },
                onDisconnect = {
                    store.disconnect(state.manifest.id)
                    refreshState()
                },
                onSync = {
                    store.updateSync(state.manifest.id, System.currentTimeMillis())
                    refreshState()
                }
            )
        }
    }
}

@Composable
private fun ConnectorCard(
    name: String,
    status: ConnectorStatus,
    account: String?,
    permissions: Set<PermissionScope>,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSync: () -> Unit
) {
    NexusCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (status == ConnectorStatus.CONNECTED) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                contentDescription = null
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(statusLabel(status, account), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Capabilities: ${permissions.joinToString { it.name.lowercase().replace('_', ' ') }}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status == ConnectorStatus.CONNECTED) {
                OutlinedButton(onClick = onSync) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text("Sync", Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            } else {
                Button(onClick = onConnect) { Text("Connect") }
            }
        }
    }
}

private fun statusLabel(status: ConnectorStatus, account: String?): String = when (status) {
    ConnectorStatus.CONNECTED -> "Connected${account?.let { " • $it" } ?: ""}"
    ConnectorStatus.NEEDS_REAUTH -> "Authorization required"
    ConnectorStatus.ERROR -> "Connection error"
    ConnectorStatus.DISABLED -> "Disabled"
    ConnectorStatus.AVAILABLE -> "Not connected"
}
