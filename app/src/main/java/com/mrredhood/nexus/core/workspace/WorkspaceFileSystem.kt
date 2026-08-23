package com.mrredhood.nexus.core.workspace

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class WorkspaceFileSystem(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun list(workspace: Workspace, relativeDirectory: String = ""): List<WorkspaceEntry> = withContext(Dispatchers.IO) {
        val normalized = normalize(relativeDirectory)
        val directory = resolveDocument(workspace, normalized)
        require(directory.isDirectory) { "Not a directory: ${normalized.ifEmpty { "/" }}" }
        directory.listFiles()
            .mapNotNull { file ->
                val name = file.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WorkspaceEntry(
                    relativePath = join(normalized, name),
                    name = name,
                    type = if (file.isDirectory) EntryType.DIRECTORY else EntryType.FILE,
                    sizeBytes = if (file.isFile) file.length().coerceAtLeast(0L) else 0L,
                    lastModified = file.lastModified().coerceAtLeast(0L),
                    mimeType = file.type
                )
            }
            .sortedWith(compareBy<WorkspaceEntry> { it.type != EntryType.DIRECTORY }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    suspend fun read(workspace: Workspace, relativePath: String): WorkspaceFile = withContext(Dispatchers.IO) {
        val normalized = requireFilePath(relativePath)
        val document = resolveDocument(workspace, normalized)
        require(document.isFile) { "Not a file: $normalized" }
        val bytes = resolver.openInputStream(document.uri)?.use { it.readBytes() }
            ?: throw IOException("Unable to open file: $normalized")
        val content = bytes.toString(Charsets.UTF_8)
        WorkspaceFile(normalized, document.name ?: leaf(normalized), content, document.length().coerceAtLeast(0L), document.lastModified().coerceAtLeast(0L), document.type)
    }

    suspend fun write(workspace: Workspace, relativePath: String, content: String, mimeType: String = "text/plain"): WorkspaceFile = withContext(Dispatchers.IO) {
        val normalized = requireFilePath(relativePath)
        val parent = ensureDirectory(workspace, parent(normalized))
        val name = leaf(normalized)
        val existing = parent.findFile(name)
        require(existing == null || existing.isFile) { "Path is a directory: $normalized" }
        val target = existing ?: parent.createFile(safeMimeType(mimeType), name)
            ?: throw IOException("Unable to create file: $normalized")
        resolver.openOutputStream(target.uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Unable to write file: $normalized")
        WorkspaceFile(normalized, target.name ?: name, content, target.length().coerceAtLeast(0L), target.lastModified().coerceAtLeast(0L), target.type ?: safeMimeType(mimeType))
    }

    suspend fun createFile(workspace: Workspace, relativePath: String, mimeType: String = "text/plain"): WorkspaceFile = withContext(Dispatchers.IO) {
        val normalized = requireFilePath(relativePath)
        val parent = ensureDirectory(workspace, parent(normalized))
        val name = leaf(normalized)
        require(parent.findFile(name) == null) { "Path already exists: $normalized" }
        val target = parent.createFile(safeMimeType(mimeType), name) ?: throw IOException("Unable to create file: $normalized")
        WorkspaceFile(normalized, target.name ?: name, "", 0L, target.lastModified().coerceAtLeast(0L), target.type ?: safeMimeType(mimeType))
    }

    suspend fun createDirectory(workspace: Workspace, relativePath: String): WorkspaceEntry = withContext(Dispatchers.IO) {
        val normalized = requireDirectoryPath(relativePath)
        val parent = ensureDirectory(workspace, parent(normalized))
        val name = leaf(normalized)
        require(parent.findFile(name) == null) { "Path already exists: $normalized" }
        val target = parent.createDirectory(name) ?: throw IOException("Unable to create directory: $normalized")
        WorkspaceEntry(normalized, target.name ?: name, EntryType.DIRECTORY, 0L, target.lastModified().coerceAtLeast(0L), target.type)
    }

    suspend fun delete(workspace: Workspace, relativePath: String) = withContext(Dispatchers.IO) {
        val normalized = requireFilePath(relativePath)
        val document = resolveDocument(workspace, normalized)
        require(document.uri != workspace.uri()) { "Cannot delete workspace root" }
        require(document.delete()) { "Unable to delete: $normalized" }
    }

    suspend fun rename(workspace: Workspace, relativePath: String, newName: String): WorkspaceEntry = withContext(Dispatchers.IO) {
        val normalized = requireFilePath(relativePath)
        val cleanName = validateName(newName)
        val document = resolveDocument(workspace, normalized)
        require(document.uri != workspace.uri()) { "Cannot rename workspace root" }
        val parentPath = parent(normalized)
        val parent = resolveDocument(workspace, parentPath)
        require(parent.isDirectory) { "Parent is not a directory: $parentPath" }
        val collision = parent.findFile(cleanName)
        require(collision == null || collision.uri == document.uri) { "Path already exists: ${join(parentPath, cleanName)}" }
        require(document.renameTo(cleanName)) { "Unable to rename: $normalized" }
        WorkspaceEntry(join(parentPath, cleanName), cleanName, if (document.isDirectory) EntryType.DIRECTORY else EntryType.FILE, if (document.isFile) document.length().coerceAtLeast(0L) else 0L, document.lastModified().coerceAtLeast(0L), document.type)
    }

    suspend fun copy(workspace: Workspace, sourcePath: String, destinationPath: String) = withContext(Dispatchers.IO) {
        val source = resolveDocument(workspace, requireFilePath(sourcePath))
        val destination = requireFilePath(destinationPath)
        require(source.uri != workspace.uri()) { "Cannot copy workspace root" }
        require(resolveDocumentOrNull(workspace, destination) == null) { "Destination already exists: $destination" }
        require(!isDescendant(destination, sourcePath)) { "Cannot copy a directory into itself" }
        if (source.isDirectory) copyDirectory(workspace, source, destination) else copyFile(source, ensureDirectory(workspace, parent(destination)), leaf(destination))
    }

    suspend fun move(workspace: Workspace, sourcePath: String, destinationPath: String) = withContext(Dispatchers.IO) {
        val sourceNormalized = requireFilePath(sourcePath)
        val destination = requireFilePath(destinationPath)
        val source = resolveDocument(workspace, sourceNormalized)
        require(source.uri != workspace.uri()) { "Cannot move workspace root" }
        require(resolveDocumentOrNull(workspace, destination) == null) { "Destination already exists: $destination" }
        require(!isDescendant(destination, sourceNormalized)) { "Cannot move a directory into itself" }

        val destinationParent = ensureDirectory(workspace, parent(destination))
        val destinationName = leaf(destination)
        val sourceParent = resolveDocument(workspace, parent(sourceNormalized))
        if (sourceParent.uri == destinationParent.uri && source.renameTo(destinationName)) return@withContext

        if (source.isDirectory) copyDirectory(workspace, source, destination) else copyFile(source, destinationParent, destinationName)
        require(source.delete()) { "Copied item but failed to remove source: $sourceNormalized" }
    }

    suspend fun exists(workspace: Workspace, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        resolveDocumentOrNull(workspace, normalize(relativePath)) != null
    }

    suspend fun isDirectory(workspace: Workspace, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        resolveDocumentOrNull(workspace, normalize(relativePath))?.isDirectory == true
    }

    private fun copyFile(source: DocumentFile, parent: DocumentFile, name: String) {
        val target = parent.createFile(source.type ?: "application/octet-stream", name) ?: throw IOException("Unable to create copy: $name")
        try {
            resolver.openInputStream(source.uri)?.use { input ->
                resolver.openOutputStream(target.uri)?.use { output -> input.copyTo(output) }
                    ?: throw IOException("Unable to open destination: $name")
            } ?: throw IOException("Unable to read source: ${source.name ?: source.uri}")
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun copyDirectory(workspace: Workspace, source: DocumentFile, destination: String) {
        val target = ensureDirectory(workspace, destination)
        source.listFiles().forEach { child ->
            val childName = child.name ?: return@forEach
            if (child.isDirectory) copyDirectory(workspace, child, join(destination, childName))
            else copyFile(child, target, childName)
        }
    }

    private fun resolveDocument(workspace: Workspace, relativePath: String): DocumentFile =
        resolveDocumentOrNull(workspace, normalize(relativePath)) ?: throw IOException("Path not found: ${normalize(relativePath).ifEmpty { "/" }}")

    private fun resolveDocumentOrNull(workspace: Workspace, relativePath: String): DocumentFile? {
        val normalized = normalize(relativePath)
        var current = DocumentFile.fromTreeUri(context, workspace.uri()) ?: throw IOException("Workspace is unavailable")
        if (normalized.isEmpty()) return current
        for (part in normalized.split('/')) {
            current = current.findFile(part) ?: return null
            if (!current.isDirectory && part != normalized.substringAfterLast('/')) return null
        }
        return current
    }

    private fun ensureDirectory(workspace: Workspace, relativePath: String): DocumentFile {
        var current = DocumentFile.fromTreeUri(context, workspace.uri()) ?: throw IOException("Workspace is unavailable")
        val normalized = normalize(relativePath)
        if (normalized.isEmpty()) return current
        for (part in normalized.split('/')) {
            current = current.findFile(part)?.takeIf { it.isDirectory } ?: current.createDirectory(part)
                ?: throw IOException("Unable to create directory: $part")
        }
        return current
    }

    private fun normalize(path: String): String {
        val raw = path.trim().replace('\\', '/')
        require(!raw.startsWith('/')) { "Absolute paths are not allowed" }
        val parts = raw.split('/').filter { it.isNotEmpty() }
        require(parts.none { it == "." || it == ".." || it.contains('\u0000') }) { "Invalid path: $path" }
        return parts.joinToString("/")
    }

    private fun requireFilePath(path: String): String = normalize(path).also { require(it.isNotEmpty()) { "A file path is required" } }
    private fun requireDirectoryPath(path: String): String = normalize(path).also { require(it.isNotEmpty()) { "A directory path is required" } }

    private fun validateName(name: String): String {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean != "." && clean != ".." && !clean.contains('/') && !clean.contains('\\') && !clean.contains('\u0000')) { "Invalid file name" }
        return clean
    }

    private fun safeMimeType(value: String): String = value.trim().takeIf { it.contains('/') && !it.contains(' ') } ?: "text/plain"
    private fun parent(path: String): String = path.substringBeforeLast('/', "")
    private fun leaf(path: String): String = path.substringAfterLast('/')
    private fun join(parent: String, child: String): String = if (parent.isBlank()) child else "$parent/$child"
    private fun isDescendant(destination: String, source: String): Boolean {
        val s = normalize(source)
        val d = normalize(destination)
        return s.isNotEmpty() && (d == s || d.startsWith("$s/"))
    }
}
