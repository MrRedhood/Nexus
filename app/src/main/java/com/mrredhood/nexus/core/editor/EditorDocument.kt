package com.mrredhood.nexus.core.editor

import com.mrredhood.nexus.core.workspace.WorkspaceFile

/**
 * Stable in-memory representation of one opened workspace file.
 *
 * The document keeps the last successfully loaded/saved snapshot separate from
 * the current editor content so dirty/conflict decisions do not depend on the UI.
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
    val errorMessage: String? = null
) {
    val isDirty: Boolean
        get() = content != originalContent

    val hasConflictBaseline: Boolean
        get() = originalSizeBytes >= 0L && originalLastModified >= 0L

    fun withContent(newContent: String): EditorDocument = copy(
        content = newContent,
        state = EditorDocumentState.READY,
        errorMessage = null
    )

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
