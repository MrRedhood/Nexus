package com.mrredhood.nexus

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.CardColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * Compatibility overload for the workspace AI entry card.
 * Material 3's clickable Card takes onClick as its first parameter,
 * while this call site intentionally keeps Modifier positional.
 */
@Composable
fun Card(
    modifier: Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: CardColors = androidx.compose.material3.CardDefaults.cardColors(),
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = colors,
        content = content
    )
}
