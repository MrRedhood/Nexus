package com.mrredhood.nexus.core.editor

import com.mrredhood.nexus.core.workspace.WorkspaceFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns open editor documents independently from Compose UI. */
class EditorDocumentManager {
    private val mutex = Mutex()
    private val documents = LinkedHashMap<DocumentKey, EditorDocument>()
    private val recentlyClosed = LinkedHashMap<DocumentKey, EditorDocument>()
    private var activeKey: DocumentKey? = null

    suspend fun open(workspaceId: String, file: WorkspaceFile): EditorDocument = mutex.withLock {
        val key = DocumentKey(workspaceId, file.relativePath)
        val existing = documents[key]
        val document = if (existing == null) EditorDocument.from(workspaceId, file)
        else if (existing.isDirty) existing
        else existing.synchronizedWith(file)
        documents[key] = document
        recentlyClosed.remove(key)
        activeKey = key
        document
    }

    suspend fun activate(workspaceId: String, relativePath: String): EditorDocument? = mutex.withLock {
        val key = DocumentKey(workspaceId, relativePath)
        documents[key]?.also { activeKey = key }
    }

    suspend fun active(): EditorDocument? = mutex.withLock { activeKey?.let(documents::get) }

    suspend fun get(workspaceId: String, relativePath: String): EditorDocument? = mutex.withLock {
        documents[DocumentKey(workspaceId, relativePath)]
    }

    suspend fun update(workspaceId: String, relativePath: String, content: String): EditorDocument? = mutex.withLock {
        val key = DocumentKey(workspaceId, relativePath)
        val current = documents[key] ?: return@withLock null
        val updated = current.withContent(content)
        documents[key] = updated
        activeKey = key
        updated
    }

    suspend fun updateViewState(workspaceId: String, relativePath: String, viewState: EditorViewState): EditorDocument? = mutex.withLock {
        val key = DocumentKey(workspaceId, relativePath)
        val current = documents[key] ?: return@withLock null
        val updated = current.withViewState(viewState)
        documents[key] = updated
        activeKey = key
        updated
    }

    suspend fun markSaving(workspaceId: String, relativePath: String): EditorDocument? = mutex.withLock {
        updateLocked(workspaceId, relativePath) { it.markSaving() }
    }

    suspend fun markConflict(workspaceId: String, relativePath: String, message: String = EditorDocument.DEFAULT_CONFLICT_MESSAGE): EditorDocument? = mutex.withLock {
        updateLocked(workspaceId, relativePath) { it.markConflict(message) }
    }

    suspend fun markError(workspaceId: String, relativePath: String, message: String): EditorDocument? = mutex.withLock {
        updateLocked(workspaceId, relativePath) { it.markError(message) }
    }

    suspend fun synchronize(workspaceId: String, file: WorkspaceFile): EditorDocument = mutex.withLock {
        val key = DocumentKey(workspaceId, file.relativePath)
        val document = (documents[key] ?: EditorDocument.from(workspaceId, file)).synchronizedWith(file)
        documents[key] = document
        activeKey = key
        document
    }

    suspend fun close(workspaceId: String, relativePath: String): EditorDocument? = mutex.withLock {
        val key = DocumentKey(workspaceId, relativePath)
        val removed = documents.remove(key) ?: return@withLock null
        recentlyClosed[key] = removed
        while (recentlyClosed.size > MAX_RECENTLY_CLOSED) recentlyClosed.remove(recentlyClosed.keys.first())
        if (activeKey == key) activeKey = chooseAdjacentKey(key)
        removed
    }

    suspend fun closeOthers(workspaceId: String, keepRelativePath: String): List<EditorDocument> = mutex.withLock {
        val keep = DocumentKey(workspaceId, keepRelativePath)
        val removed = documents.filterKeys { it.workspaceId == workspaceId && it != keep }.values.toList()
        removed.forEach { recentlyClosed[DocumentKey(it.workspaceId, it.relativePath)] = it }
        documents.keys.removeAll { it.workspaceId == workspaceId && it != keep }
        activeKey = if (documents.containsKey(keep)) keep else activeKey
        trimRecentlyClosed()
        removed
    }

    suspend fun closeAll(workspaceId: String): List<EditorDocument> = closeWorkspace(workspaceId)

    suspend fun closeWorkspace(workspaceId: String): List<EditorDocument> = mutex.withLock {
        val removed = documents.filterKeys { it.workspaceId == workspaceId }.values.toList()
        removed.forEach { recentlyClosed[DocumentKey(it.workspaceId, it.relativePath)] = it }
        documents.keys.removeAll { it.workspaceId == workspaceId }
        if (activeKey?.workspaceId == workspaceId) activeKey = documents.keys.lastOrNull()
        trimRecentlyClosed()
        removed
    }

    suspend fun closeEverything(): List<EditorDocument> = mutex.withLock {
        val removed = documents.values.toList()
        removed.forEach { recentlyClosed[DocumentKey(it.workspaceId, it.relativePath)] = it }
        documents.clear()
        activeKey = null
        trimRecentlyClosed()
        removed
    }

    suspend fun reopenLastClosed(workspaceId: String): EditorDocument? = mutex.withLock {
        val key = recentlyClosed.keys.lastOrNull { it.workspaceId == workspaceId } ?: return@withLock null
        val document = recentlyClosed.remove(key) ?: return@withLock null
        documents[key] = document
        activeKey = key
        document
    }

    suspend fun all(): List<EditorDocument> = mutex.withLock { documents.values.toList() }

    suspend fun dirtyDocuments(): List<EditorDocument> = mutex.withLock { documents.values.filter(EditorDocument::isDirty) }

    suspend fun hasUnsavedChanges(workspaceId: String? = null): Boolean = mutex.withLock {
        documents.values.any { document -> (workspaceId == null || document.workspaceId == workspaceId) && document.isDirty }
    }

    suspend fun activeKey(): Pair<String, String>? = mutex.withLock { activeKey?.let { it.workspaceId to it.relativePath } }

    private fun chooseAdjacentKey(closedKey: DocumentKey): DocumentKey? {
        val keys = documents.keys.filter { it.workspaceId == closedKey.workspaceId }
        return keys.lastOrNull()
    }

    private fun trimRecentlyClosed() {
        while (recentlyClosed.size > MAX_RECENTLY_CLOSED) recentlyClosed.remove(recentlyClosed.keys.first())
    }

    private fun updateLocked(workspaceId: String, relativePath: String, transform: (EditorDocument) -> EditorDocument): EditorDocument? {
        val key = DocumentKey(workspaceId, relativePath)
        val current = documents[key] ?: return null
        val updated = transform(current)
        documents[key] = updated
        activeKey = key
        return updated
    }

    private data class DocumentKey(val workspaceId: String, val relativePath: String)

    companion object {
        private const val MAX_RECENTLY_CLOSED = 20
    }
}
