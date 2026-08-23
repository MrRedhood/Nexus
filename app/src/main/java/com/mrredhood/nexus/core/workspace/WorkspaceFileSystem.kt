package com.mrredhood.nexus.core.workspace

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class WorkspaceFileSystem(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun list(workspace: Workspace, relativeDirectory: String = ""): List<WorkspaceEntry> = withContext(Dispatchers.IO) {
        val directory = resolveDocument(workspace, relativeDirectory)
        require(directory.isDirectory) { "Workspace directory does not exist: $relativeDirectory" }
        directory.listFiles().map { file ->
            WorkspaceEntry(
                relativePath = join(relativeDirectory, file.name ?: ""),
                name = file.name ?: "Unnamed",
                type = if (file.isDirectory) EntryType.DIRECTORY else EntryType.FILE,
                sizeBytes = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
                mimeType = file.type
            )
        }.sortedWith(compareBy<WorkspaceEntry> { it.type != EntryType.DIRECTORY }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    suspend fun read(workspace: Workspace, relativePath: String): WorkspaceFile = withContext(Dispatchers.IO) {
        val document = resolveDocument(workspace, relativePath)
        require(document.isFile) { "File does not exist: $relativePath" }
        val bytes = resolver.openInputStream(document.uri)?.use { it.readBytes() }
            ?: throw IOException("Unable to open file: $relativePath")
        val content = bytes.toString(Charsets.UTF_8)
        WorkspaceFile(relativePath, document.name ?: leaf(relativePath), content, document.length(), document.lastModified(), document.type)
    }

    suspend fun write(workspace: Workspace, relativePath: String, content: String, mimeType: String = "text/plain"): WorkspaceFile = withContext(Dispatchers.IO) {
        val normalized = normalize(relativePath)
        require(normalized.isNotEmpty()) { "A file path is required" }
        val parent = ensureDirectory(workspace, parent(normalized))
        val name = leaf(normalized)
        val existing = parent.findFile(name)
        val target = existing ?: parent.createFile(mimeType, name) ?: throw IOException("Unable to create file: $normalized")
        require(target.isFile) { "Path is a directory: $normalized" }
        resolver.openOutputStream(target.uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Unable to write file: $normalized")
        WorkspaceFile(normalized, target.name ?: name, content, target.length(), target.lastModified(), target.type)
    }

    suspend fun createFile(workspace: Workspace, relativePath: String, mimeType: String = "text/plain"): WorkspaceFile = withContext(Dispatchers.IO) {
        val normalized = normalize(relativePath)
        require(normalized.isNotEmpty()) { "A file path is required" }
        val parent = ensureDirectory(workspace, parent(normalized))
        val name = leaf(normalized)
        require(parent.findFile(name) == null) { "Path already exists: $normalized" }
        val target = parent.createFile(mimeType, name) ?: throw IOException("Unable to create file: $normalized")
        WorkspaceFile(normalized, target.name ?: name, "", 0L, target.lastModified(), target.type)
    }

    suspend fun createDirectory(workspace: Workspace, relativePath: String): WorkspaceEntry = withContext(Dispatchers.IO) {
        val normalized = normalize(relativePath)
        require(normalized.isNotEmpty()) { "A directory path is required" }
        val parent = ensureDirectory(workspace, parent(normalized))
        val name = leaf(normalized)
        require(parent.findFile(name) == null) { "Path already exists: $normalized" }
        val target = parent.createDirectory(name) ?: throw IOException("Unable to create directory: $normalized")
        WorkspaceEntry(normalized, target.name ?: name, EntryType.DIRECTORY, 0L, target.lastModified(), target.type)
    }

    suspend fun delete(workspace: Workspace, relativePath: String) = withContext(Dispatchers.IO) {
        val normalized = normalize(relativePath)
        require(normalized.isNotEmpty()) { "Cannot delete workspace root" }
        val document = resolveDocument(workspace, normalized)
        require(document.uri != workspace.uri()) { "Cannot delete workspace root" }
        require(document.delete()) { "Unable to delete: $normalized" }
    }

    suspend fun rename(workspace: Workspace, relativePath: String, newName: String): WorkspaceEntry = withContext(Dispatchers.IO) {
        val normalized = normalize(relativePath)
        val cleanName = validateName(newName)
        val document = resolveDocument(workspace, normalized)
        val parent = resolveDocument(workspace, parent(normalized))
        require(parent.findFile(cleanName) == null) { "Path already exists: ${join(parent(normalized), cleanName)}" }
        require(document.renameTo(cleanName)) { "Unable to rename: $normalized" }
        WorkspaceEntry(join(parent(normalized), cleanName), cleanName, if (document.isDirectory) EntryType.DIRECTORY else EntryType.FILE, if (document.isFile) document.length() else 0L, document.lastModified(), document.type)
    }

    suspend fun copy(workspace: Workspace, sourcePath: String, destinationPath: String) = withContext(Dispatchers.IO) {
        val source = resolveDocument(workspace, sourcePath)
        val destination = normalize(destinationPath)
        require(destination.isNotEmpty()) { "Destination is required" }
        require(resolveDocumentOrNull(workspace, destination) == null) { "Destination already exists: $destination" }
        if (source.isDirectory) {
            copyDirectory(workspace, source, destination)
        } else {
            val parent = ensureDirectory(workspace, parent(destination))
            val target = parent.createFile(source.type ?: "application/octet-stream", leaf(destination))
                ?: throw IOException("Unable to create copy: $destination")
            resolver.openInputStream(source.uri)?.use { input -> resolver.openOutputStream(target.uri)?.use { output -> input.copyTo(output) } }
                ?: throw IOException("Unable to read source: $sourcePath")
        }
    }

    suspend fun exists(workspace: Workspace, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        resolveDocumentOrNull(workspace, relativePath) != null
    }

    private fun copyDirectory(workspace: Workspace, source: DocumentFile, destination: String) {
        val target = ensureDirectory(workspace, destination)
        source.listFiles().forEach { child ->
            val childName = child.name ?: return@forEach
            if (child.isDirectory) copyDirectory(workspace, child, join(destination, childName))
            else {
                val out = target.createFile(child.type ?: "application/octet-stream", childName) ?: throw IOException("Unable to copy $childName")
                resolver.openInputStream(child.uri)?.use { input -> resolver.openOutputStream(out.uri)?.use { output -> input.copyTo(output) } }
            }
        }
    }

    private fun resolveDocument(workspace: Workspace, relativePath: String): DocumentFile =
        resolveDocumentOrNull(workspace, relativePath) ?: throw IOException("Path not found: ${normalize(relativePath)}")

    private fun resolveDocumentOrNull(workspace: Workspace, relativePath: String): DocumentFile? {
        val normalized = normalize(relativePath)
        var current = DocumentFile.fromTreeUri(context, workspace.uri()) ?: throw IOException("Workspace is unavailable")
        if (normalized.isEmpty()) return current
        for (part in normalized.split('/')) {
            current = current.findFile(part) ?: return null
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

    private fun validateName(name: String): String {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean != "." && clean != ".." && !clean.contains('/') && !clean.contains('\\') && !clean.contains('\u0000')) { "Invalid file name" }
        return clean
    }

    private fun parent(path: String): String = path.substringBeforeLast('/', "")
    private fun leaf(path: String): String = path.substringAfterLast('/')
    private fun join(parent: String, child: String): String = if (parent.isBlank()) child else "$parent/$child"
}
