package com.mrredhood.nexus.ui.theme

import android.app.Activity
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    val colors = base.copy(primary = primary, secondary = primary)

    val activity = context as? Activity
    DisposableEffect(activity, settings.fullscreen, settings.immersiveCoding) {
        val window = activity?.window
        val oldFlags = window?.decorView?.systemUiVisibility ?: 0
        if (window != null) {
            var flags = oldFlags
            if (settings.fullscreen) {
                flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN
            } else {
                flags = flags and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
            }
            if (settings.immersiveCoding) {
                flags = flags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else {
                flags = flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv() and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY.inv()
            }
            window.decorView.systemUiVisibility = flags
        }
        onDispose {
            window?.decorView?.systemUiVisibility = oldFlags
        }
    }

    val density = LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density * settings.uiScale, density.fontScale)
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

private fun accentColor(value: String): Color = when (value) {
    "purple" -> Color(0xFF9C6BFF)
    "cyan" -> Color(0xFF00AFC1)
    "green" -> Color(0xFF39A85A)
    "orange" -> Color(0xFFE98B2A)
    "red" -> Color(0xFFE55353)
    else -> Color(0xFF4F7CFF)
}
