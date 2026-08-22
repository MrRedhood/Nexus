package com.mrredhood.nexus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NexusLight = lightColorScheme(
    primary = Color(0xFF5B4FD6),
    onPrimary = Color.White,
    secondary = Color(0xFF665F7D),
    background = Color(0xFFF8F7FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9E6F0)
)

private val NexusDark = darkColorScheme(
    primary = Color(0xFFC5BCFF),
    onPrimary = Color(0xFF2B206A),
    secondary = Color(0xFFCDC4E5),
    background = Color(0xFF121116),
    surface = Color(0xFF19181E),
    surfaceVariant = Color(0xFF45434E)
)

@Composable
fun NexusTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NexusDark else NexusLight,
        content = content
    )
}
