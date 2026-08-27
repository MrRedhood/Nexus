package com.mrredhood.nexus.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

private enum class AiActivity(val glyph: String, val label: String) {
    WRITING("✎", "Writing"),
    HAMMERING("⚒", "Hammering"),
    RUNNING("🏃", "Running"),
    CRAFTING("🛠", "Crafting"),
    THINKING("💭", "Thinking"),
    BLINKING("●", "Thinking")
}

@Composable
fun AiActivityIndicator() {
    val activity = remember { AiActivity.entries.random() }
    val transition = rememberInfiniteTransition(label = "ai-activity-${activity.name}")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "phase"
    )
    val rotation by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "rotation"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "alpha"
    )
    val scale = when (activity) {
        AiActivity.BLINKING -> 0.85f + phase * 0.15f
        AiActivity.RUNNING -> 0.92f + phase * 0.08f
        else -> 0.96f + phase * 0.04f
    }
    val angle = when (activity) {
        AiActivity.HAMMERING -> rotation * 2f
        AiActivity.WRITING -> rotation
        AiActivity.CRAFTING -> -rotation
        else -> 0f
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                activity.glyph,
                modifier = Modifier.size(34.dp).rotate(angle).scale(scale).alpha(if (activity == AiActivity.BLINKING) alpha else 1f).padding(7.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
        Text(
            "Nexus is ${activity.label.lowercase()}…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { index ->
                val dotAlpha = if (activity == AiActivity.BLINKING) {
                    if (index == 0) alpha else 0.3f
                } else 0.35f + (phase * 0.45f)
                Surface(
                    Modifier.size(5.dp).alpha(dotAlpha),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {}
            }
        }
    }
}
