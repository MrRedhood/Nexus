package com.mrredhood.nexus.core.editor

import com.mrredhood.nexus.core.workspace.WorkspaceFile

/**
 * Stable in-memory representation of one opened workspace file.
 *
 * The document keeps the last successfully loaded/saved snapshot separate from
 * the current editor content so dirty/conflict decisions do not depend on the UI.
 * Editor view state is kept here as well so it survives Compose recomposition
 * and tab switches without being tied to a particular UI instance.
 */
data class EditorDocument(
    val workspaceId: String,
    val relativePath: String,
    val name: String,
    val originalContent: String,
    val content: String,
    val originalSizeBytes: Long,
    val originalLastModified: Long,
    val mimeType: String?,
    val state: EditorDocumentState = EditorDocumentState.READY,
    val errorMessage: String? = null,
    val cursorPosition: Int = 0,
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val scrollPosition: Int = 0
) {
    val isDirty: Boolean
        get() = content != originalContent

    val hasConflictBaseline: Boolean
        get() = originalSizeBytes >= 0L && originalLastModified >= 0L

    val viewState: EditorViewState
        get() = EditorViewState(
            cursorPosition = cursorPosition,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            scrollPosition = scrollPosition
        )

    fun withContent(newContent: String): EditorDocument = copy(
        content = newContent,
        cursorPosition = cursorPosition.coerceIn(0, newContent.length),
        selectionStart = selectionStart.coerceIn(0, newContent.length),
        selectionEnd = selectionEnd.coerceIn(0, newContent.length),
        state = EditorDocumentState.READY,
        errorMessage = null
    )

    fun withViewState(viewState: EditorViewState): EditorDocument {
        val normalized = viewState.normalized(content.length)
        return copy(
            cursorPosition = normalized.cursorPosition,
            selectionStart = normalized.selectionStart,
            selectionEnd = normalized.selectionEnd,
            scrollPosition = normalized.scrollPosition
        )
    }

    fun markSaving(): EditorDocument = copy(
        state = EditorDocumentState.SAVING,
        errorMessage = null
    )

    fun markError(message: String): EditorDocument = copy(
        state = EditorDocumentState.ERROR,
        errorMessage = message
    )

    fun markConflict(message: String = DEFAULT_CONFLICT_MESSAGE): EditorDocument = copy(
        state = EditorDocumentState.CONFLICT,
        errorMessage = message
    )

    /**
     * Creates the clean baseline from a file that has just been loaded or saved.
     * Existing editor view state is intentionally preserved when synchronizing
     * an already-open document.
     */
    fun synchronizedWith(file: WorkspaceFile): EditorDocument = copy(
        workspaceId = workspaceId,
        relativePath = file.relativePath,
        name = file.name,
        originalContent = file.content,
        content = file.content,
        originalSizeBytes = file.sizeBytes,
        originalLastModified = file.lastModified,
        mimeType = file.mimeType,
        cursorPosition = cursorPosition.coerceIn(0, file.content.length),
        selectionStart = selectionStart.coerceIn(0, file.content.length),
        selectionEnd = selectionEnd.coerceIn(0, file.content.length),
        scrollPosition = scrollPosition.coerceAtLeast(0),
        state = EditorDocumentState.READY,
        errorMessage = null
    )

    companion object {
        const val DEFAULT_CONFLICT_MESSAGE =
            "The file changed outside Nexus. Reload it before saving."

        fun from(workspaceId: String, file: WorkspaceFile): EditorDocument = EditorDocument(
            workspaceId = workspaceId,
            relativePath = file.relativePath,
            name = file.name,
            originalContent = file.content,
            content = file.content,
            originalSizeBytes = file.sizeBytes,
            originalLastModified = file.lastModified,
            mimeType = file.mimeType
        )
    }
}

enum class EditorDocumentState {
    READY,
    SAVING,
    CONFLICT,
    ERROR
}
