package com.mrredhood.nexus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingNotice(
    onDismiss: () -> Unit,
    onPrivacy: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        NexusCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Security, contentDescription = null)
                    Text("Important access notice", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "AI access is optional. When you connect an AI provider or another service and grant Nexus permission to use it, you are authorizing those services to process or act on the information and resources covered by the permissions you selected."
                )
                Text(
                    "Nexus cannot control actions taken by an external AI provider or a connected service after you authorize access. You are responsible for reviewing provider terms, connector permissions, AI context settings, and individual approval requests before allowing an action."
                )
                Text(
                    "To the maximum extent permitted by applicable law, the Nexus developer is not responsible for loss, damage, unauthorized changes, communications, or other consequences resulting from permissions or access that you voluntarily grant, including actions performed by an external AI provider or connected service. This notice does not remove any rights that cannot legally be excluded."
                )
                Text(
                    "External content is treated as data, not as authority. Nexus still routes supported actions through its permission system and records activity where applicable."
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
