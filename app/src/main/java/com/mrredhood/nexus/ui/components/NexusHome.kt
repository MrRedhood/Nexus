package com.mrredhood.nexus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NexusHome(padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Good evening", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Your connected information and automation hub.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            NexusCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                    Column {
                        Text("Search or ask Nexus", style = MaterialTheme.typography.titleMedium)
                        Text("Find information across connected sources.")
                    }
                }
            }
        }
        item {
            NexusCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Inbox, contentDescription = null)
                    Column {
                        Text("Needs your attention", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("No urgent items yet.")
                    }
                }
            }
        }
        item {
            NexusCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Security, contentDescription = null)
                    Column {
                        Text("Privacy first", style = MaterialTheme.typography.titleMedium)
                        Text("Permissions, AI context and connector access stay user-controlled.")
                    }
                }
            }
        }
        item {
            NexusCard {
                Text("Connect your first service", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Start with Google services and GitHub. The connector runtime will own service-specific logic.")
                Spacer(Modifier.height(12.dp))
                Row {
                    Button(onClick = { }) {
                        Icon(Icons.Rounded.AddLink, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Connect")
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = { }) {
                        Text("Learn more")
                    }
                }
            }
        }
        item {
            NexusCard {
                Text("Today in Nexus", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("0 connector events • 0 automation runs • 0 pending approvals")
            }
        }
    }
}
