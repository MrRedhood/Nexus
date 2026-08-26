package com.mrredhood.nexus.core.ai

import com.mrredhood.nexus.core.workspace.EntryType
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import java.util.UUID

/** In-memory, per-process recovery snapshots for AI workspace mutations. */
class NexusActionSnapshotStore(private val fileSystem: WorkspaceFileSystem) {
    private val snapshots = linkedMapOf<String, Snapshot>()

    suspend fun capture(workspace: Workspace, action: NexusAction): String? {
        if (!NexusActionPolicy.isMutating(action)) return null
        val roots = affectedRoots(action)
        val entries = linkedMapOf<String, SnapshotEntry>()
        roots.forEach { root -> capturePath(workspace, root, entries) }
        val id = UUID.randomUUID().toString()
        snapshots[id] = Snapshot(entries.values.toList())
        while (snapshots.size > MAX_SNAPSHOTS) snapshots.remove(snapshots.keys.first())
        return id
    }

    suspend fun rollback(workspace: Workspace, snapshotId: String): Boolean {
        val snapshot = snapshots[snapshotId] ?: return false
        val roots = snapshot.entries.filter { it.parentPath == null }.map { it.path }
        roots.sortedByDescending { it.count { c -> c == '/' } }.forEach { root ->
            if (fileSystem.exists(workspace, root)) deleteTree(workspace, root)
        }
        snapshot.entries.filter { it.isDirectory }.sortedBy { it.path.count { c -> c == '/' } }.forEach { entry ->
            if (!fileSystem.exists(workspace, entry.path)) runCatching { fileSystem.createDirectory(workspace, entry.path) }
        }
        snapshot.entries.filter { !it.isDirectory }.sortedBy { it.path }.forEach { entry ->
            fileSystem.write(workspace, entry.path, entry.content.orEmpty(), entry.mimeType ?: "text/plain")
        }
        snapshots.remove(snapshotId)
        return true
    }

    fun forget(snapshotId: String) { snapshots.remove(snapshotId) }

    private suspend fun capturePath(workspace: Workspace, path: String, output: MutableMap<String, SnapshotEntry>) {
        if (path.isBlank() || !fileSystem.exists(workspace, path)) return
        if (fileSystem.isDirectory(workspace, path)) {
            output[path] = SnapshotEntry(path, true, null, null, parentPath(path))
            fileSystem.list(workspace, path).forEach { entry -> capturePath(workspace, entry.relativePath, output) }
        } else {
            val file = fileSystem.read(workspace, path)
            output[path] = SnapshotEntry(path, false, file.content, file.mimeType, parentPath(path))
        }
    }

    private suspend fun deleteTree(workspace: Workspace, path: String) {
        if (fileSystem.isDirectory(workspace, path)) {
            fileSystem.list(workspace, path).forEach { entry -> deleteTree(workspace, entry.relativePath) }
        }
        if (fileSystem.exists(workspace, path)) fileSystem.delete(workspace, path)
    }

    private fun affectedRoots(action: NexusAction): List<String> = buildList {
        action.path?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        if (action.type == "copy_file" || action.type == "move_file") action.destination?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        if (action.type == "rename_file") {
            val path = action.path?.trim().orEmpty()
            val name = action.newName?.trim().orEmpty()
            if (path.isNotBlank() && name.isNotBlank()) add(path.substringBeforeLast('/', "").let { if (it.isBlank()) name else "$it/$name" })
        }
    }.distinct()

    private fun parentPath(path: String): String? = path.substringBeforeLast('/', "").takeIf { it.isNotBlank() }

    private data class Snapshot(val entries: List<SnapshotEntry>)
    private data class SnapshotEntry(
        val path: String,
        val isDirectory: Boolean,
        val content: String?,
        val mimeType: String?,
        val parentPath: String?
    )

    companion object { private const val MAX_SNAPSHOTS = 30 }
}
