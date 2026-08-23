package com.mrredhood.nexus.core.editor

/**
 * View-only state that should survive editor recomposition and tab switches.
 * This is intentionally separate from file content and save/conflict state.
 */
data class EditorViewState(
    val cursorPosition: Int = 0,
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val scrollPosition: Int = 0
) {
    fun normalized(contentLength: Int): EditorViewState = copy(
        cursorPosition = cursorPosition.coerceIn(0, contentLength),
        selectionStart = selectionStart.coerceIn(0, contentLength),
        selectionEnd = selectionEnd.coerceIn(0, contentLength),
        scrollPosition = scrollPosition.coerceAtLeast(0)
    )
}
