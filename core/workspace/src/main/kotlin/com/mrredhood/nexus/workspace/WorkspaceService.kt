package com.mrredhood.nexus.workspace

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * Single filesystem boundary for Nexus workspace operations.
 *
 * All paths are workspace-relative. Mutations use temporary files followed by
 * an atomic move where the filesystem supports it, preventing partial writes.
 */
class WorkspaceService(private val root: Path) {
    init {
        Files.createDirectories(root)
        require(Files.isDirectory(root)) { "Workspace root must be a directory" }
    }

    fun exists(relativePath: String): Boolean = resolve(relativePath).let(Files::exists)

    fun read(relativePath: String): String = Files.readString(resolve(relativePath))

    fun sha256(relativePath: String): String = sha256(resolve(relativePath))

    fun list(relativeDirectory: String = ""): List<WorkspaceEntry> {
        val directory = if (relativeDirectory.isBlank()) root else resolve(relativeDirectory)
        require(Files.isDirectory(directory)) { "Not a directory: $relativeDirectory" }
        return Files.list(directory).use { stream ->
            stream.map { path ->
                WorkspaceEntry(
                    path = root.relativize(path).toString().replace('\\', '/'),
                    isDirectory = Files.isDirectory(path),
                    sizeBytes = if (Files.isRegularFile(path)) Files.size(path) else null,
                )
            }.sorted(compareBy<WorkspaceEntry> { !it.isDirectory }.thenBy { it.path.lowercase() }).toList()
        }
    }

    fun write(relativePath: String, content: String) {
        val target = resolve(relativePath)
        Files.createDirectories(target.parent)
        val temporary = target.resolveSibling(".${target.fileName}.nexus-tmp-${UUID.randomUUID()}")
        try {
            Files.writeString(temporary, content)
            atomicReplace(temporary, target)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun create(relativePath: String, content: String = "") {
        val target = resolve(relativePath)
        require(!Files.exists(target)) { "Path already exists: $relativePath" }
        write(relativePath, content)
    }

    fun delete(relativePath: String) {
        val target = resolve(relativePath)
        require(target != root) { "Cannot delete workspace root" }
        Files.deleteIfExists(target)
    }

    fun move(from: String, to: String) {
        val source = resolve(from)
        val target = resolve(to)
        require(Files.exists(source)) { "Source does not exist: $from" }
        require(!Files.exists(target)) { "Destination already exists: $to" }
        Files.createDirectories(target.parent)
        Files.move(source, target)
    }

    private fun resolve(relativePath: String): Path = root.resolve(WorkspacePath.normalize(relativePath)).normalize().also {
        require(it.startsWith(root.normalize())) { "Path escapes the workspace root" }
    }

    private fun atomicReplace(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class WorkspaceEntry(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
)

/** Persistent filesystem snapshot/rollback primitive for mutating actions. */
class WorkspaceSnapshotStore(
    private val snapshotRoot: Path,
) {
    init { Files.createDirectories(snapshotRoot) }

    fun createSnapshot(workspaceRoot: Path): String {
        val id = UUID.randomUUID().toString()
        val destination = snapshotRoot.resolve(id)
        copyTree(workspaceRoot, destination)
        return id
    }

    fun restoreSnapshot(snapshotId: String, workspaceRoot: Path) {
        val snapshot = snapshotRoot.resolve(snapshotId).normalize()
        require(snapshot.startsWith(snapshotRoot.normalize())) { "Invalid snapshot id" }
        require(Files.isDirectory(snapshot)) { "Snapshot not found: $snapshotId" }
        clearDirectory(workspaceRoot)
        copyTree(snapshot, workspaceRoot)
    }

    fun deleteSnapshot(snapshotId: String) {
        val snapshot = snapshotRoot.resolve(snapshotId).normalize()
        require(snapshot.startsWith(snapshotRoot.normalize())) { "Invalid snapshot id" }
        if (Files.exists(snapshot)) deleteTree(snapshot)
    }

    private fun copyTree(source: Path, destination: Path) {
        Files.createDirectories(destination)
        Files.walkFileTree(source, object : java.nio.file.SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: java.nio.file.attribute.BasicFileAttributes): FileVisitResult {
                Files.createDirectories(destination.resolve(source.relativize(dir)))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes): FileVisitResult {
                Files.copy(file, destination.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun clearDirectory(directory: Path) {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory)
            return
        }
        Files.list(directory).use { stream -> stream.forEach { deleteTree(it) } }
    }

    private fun deleteTree(path: Path) {
        Files.walkFileTree(path, object : java.nio.file.SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
