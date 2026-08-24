package com.mrredhood.nexus.ui.theme

import android.app.Activity
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
    val primary = accentColor(settings.accent)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colors = if (dark) {
        base.copy(
            background = Color(0xFF212121),
            surface = Color(0xFF212121),
            surfaceContainerLowest = Color(0xFF171717),
            surfaceContainerLow = Color(0xFF262626),
            surfaceContainer = Color(0xFF2A2A2A),
            surfaceContainerHigh = Color(0xFF303030),
            surfaceContainerHighest = Color(0xFF353535),
            primary = primary,
            secondary = primary
        )
    } else {
        base.copy(
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF7F7F8),
            surfaceContainer = Color(0xFFF1F1F1),
            surfaceContainerHigh = Color(0xFFECECEC),
            surfaceContainerHighest = Color(0xFFE7E7E7),
            primary = primary,
            secondary = primary
        )
    }

    val activity = context as? Activity
    DisposableEffect(activity, settings.fullscreen, settings.immersiveCoding) {
        val window = activity?.window
        val oldFlags = window?.decorView?.systemUiVisibility ?: 0
        if (window != null) {
            var flags = oldFlags
            if (settings.fullscreen) flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN
            else flags = flags and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
            if (settings.immersiveCoding) flags = flags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            else flags = flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv() and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY.inv()
            window.decorView.systemUiVisibility = flags
        }
        onDispose { window?.decorView?.systemUiVisibility = oldFlags }
    }

    val density = LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density * settings.uiScale, density.fontScale)
    ) {
        MaterialTheme(
            colorScheme = colors,
            shapes = Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            ),
            content = content
        )
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
