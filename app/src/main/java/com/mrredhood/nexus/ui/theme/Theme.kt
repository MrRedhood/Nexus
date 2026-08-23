package com.mrredhood.nexus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrredhood.nexus.core.settings.NexusSettings
import com.mrredhood.nexus.core.settings.SettingsRepository

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsRepository(context.applicationContext).settings.collectAsStateWithLifecycle(initialValue = NexusSettings())
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        "light" -> false
        "dark", "amoled" -> true
        else -> systemDark
    }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val primary = accentColor(settings.accent)
    val colors = if (settings.accent == "blue") base else base.copy(primary = primary, secondary = primary)
    MaterialTheme(colorScheme = colors, content = content)
}

private fun accentColor(value: String): Color = when (value) {
    "purple" -> Color(0xFF9C6BFF)
    "cyan" -> Color(0xFF00AFC1)
    "green" -> Color(0xFF39A85A)
    "orange" -> Color(0xFFE98B2A)
    "red" -> Color(0xFFE55353)
    else -> Color(0xFF4F7CFF)
}
