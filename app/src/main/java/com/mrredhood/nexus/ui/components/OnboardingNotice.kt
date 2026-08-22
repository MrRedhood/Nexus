package com.mrredhood.nexus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingNotice(
    onDismiss: () -> Unit,
    onPrivacy: () -> Unit
) {
    androidx.compose.material3.BasicAlertDialog(onDismissRequest = onDismiss) {
        NexusCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Security, contentDescription = null)
                    Text("Important access notice", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                }
                Text(
                    "Nexus does not control what an external AI provider may do with information that you explicitly authorize it to receive. AI access is optional and controlled by you. Review connector permissions and AI context settings before enabling access."
                )
                Text(
                    "External content is treated as data, not as authority. Nexus actions are routed through its permission system, but no software can guarantee that every AI output or provider-side outcome will be harmless."
                )
                Text(
                    "This notice is informational and is not a substitute for the final legal terms or privacy policy."
                )
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onPrivacy, modifier = Modifier.weight(1f)) {
                        Text("Privacy")
                    }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Continue")
                    }
                }
            }
        }
    }
}
