package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** A file-level change relative to the workspace snapshot captured when tracking started. */
data class WorkspaceChange(
    val relativePath: String,
    val status: WorkspaceChangeStatus,
    val additions: Int,
    val deletions: Int
)

enum class WorkspaceChangeStatus { CREATED, MODIFIED, DELETED }

data class WorkspaceChangeSummary(
    val changes: List<WorkspaceChange> = emptyList(),
    val additions: Int = changes.sumOf { it.additions },
    val deletions: Int = changes.sumOf { it.deletions }
) {
    val created: Int get() = changes.count { it.status == WorkspaceChangeStatus.CREATED }
    val modified: Int get() = changes.count { it.status == WorkspaceChangeStatus.MODIFIED }
    val deleted: Int get() = changes.count { it.status == WorkspaceChangeStatus.DELETED }
}

/**
 * Tracks text-file changes made after a workspace is opened. The baseline is
 * intentionally independent of Git so it also works for non-Git workspaces.
 */
class WorkspaceChangeTracker(private val fileSystem: WorkspaceFileSystem) {
    private var workspaceId: String? = null
    private var baseline: Map<String, String> = emptyMap()

    suspend fun start(workspace: Workspace) = withContext(Dispatchers.IO) {
        if (workspaceId == workspace.id && baseline.isNotEmpty()) return@withContext
        baseline = snapshot(workspace)
        workspaceId = workspace.id
    }

    suspend fun refresh(workspace: Workspace): WorkspaceChangeSummary = withContext(Dispatchers.IO) {
        if (workspaceId != workspace.id || baseline.isEmpty()) {
            baseline = snapshot(workspace)
            workspaceId = workspace.id
            return@withContext WorkspaceChangeSummary()
        }
        val current = snapshot(workspace)
        buildSummary(baseline, current)
    }

    suspend fun reset(workspace: Workspace): WorkspaceChangeSummary = withContext(Dispatchers.IO) {
        baseline = snapshot(workspace)
        workspaceId = workspace.id
        WorkspaceChangeSummary()
    }

    private suspend fun snapshot(workspace: Workspace): Map<String, String> {
        val files = linkedMapOf<String, String>()
        suspend fun walk(path: String) {
            coroutineContext.ensureActive()
            fileSystem.list(workspace, path).forEach { entry ->
                coroutineContext.ensureActive()
                if (entry.type == EntryType.DIRECTORY) {
                    if (entry.name !in EXCLUDED_DIRECTORIES) walk(entry.relativePath)
                } else if (entry.sizeBytes in 0..MAX_FILE_BYTES && isTextLike(entry.name, entry.mimeType)) {
                    runCatching { fileSystem.read(workspace, entry.relativePath) }
                        .onSuccess { file -> if (!file.content.take(4096).contains('\u0000')) files[file.relativePath] = normalize(file.content) }
                }
            }
        }
        walk("")
        return files
    }

    private fun buildSummary(before: Map<String, String>, after: Map<String, String>): WorkspaceChangeSummary {
        val paths = (before.keys + after.keys).toSortedSet()
        val changes = paths.mapNotNull { path ->
            val old = before[path]
            val new = after[path]
            when {
                old == null && new != null -> WorkspaceChange(path, WorkspaceChangeStatus.CREATED, new.lineCount(), 0)
                old != null && new == null -> WorkspaceChange(path, WorkspaceChangeStatus.DELETED, 0, old.lineCount())
                old != new -> {
                    val (additions, deletions) = lineDiffCounts(old.orEmpty().lines(), new.orEmpty().lines())
                    WorkspaceChange(path, WorkspaceChangeStatus.MODIFIED, additions, deletions)
                }
                else -> null
            }
        }
        return WorkspaceChangeSummary(changes)
    }

    private fun normalize(content: String): String = content.replace("\r\n", "\n").replace('\r', '\n')
    private fun String.lineCount(): Int = if (isEmpty()) 0 else split('\n').size

    /** Myers line diff; modified lines count as one deletion plus one addition. */
    private fun lineDiffCounts(a: List<String>, b: List<String>): Pair<Int, Int> {
        val n = a.size
        val m = b.size
        if (n == 0) return m to 0
        if (m == 0) return 0 to n
        val max = n + m
        val offset = max
        var v = IntArray(2 * max + 1)
        v[offset + 1] = 0
        val trace = ArrayList<IntArray>()
        for (d in 0..max) {
            for (k in -d..d step 2) {
                val index = offset + k
                var x = when {
                    k == -d -> v[index + 1]
                    k == d -> v[index - 1] + 1
                    v[index - 1] < v[index + 1] -> v[index + 1]
                    else -> v[index - 1] + 1
                }
                var y = x - k
                while (x < n && y < m && a[x] == b[y]) { x++; y++ }
                v[index] = x
                if (x >= n && y >= m) {
                    trace += v.copyOf()
                    return backtrackCounts(trace, a.size, b.size, offset)
                }
            }
            trace += v.copyOf()
        }
        return m to n
    }

    private fun backtrackCounts(trace: List<IntArray>, n: Int, m: Int, offset: Int): Pair<Int, Int> {
        var x = n
        var y = m
        var additions = 0
        var deletions = 0
        for (d in trace.lastIndex downTo 1) {
            val v = trace[d - 1]
            val k = x - y
            val prevK = when {
                k == -d -> k + 1
                k == d -> k - 1
                v[offset + k - 1] < v[offset + k + 1] -> k + 1
                else -> k - 1
            }
            val prevX = v[offset + prevK]
            val prevY = prevX - prevK
            while (x > prevX && y > prevY) { x--; y-- }
            if (x == prevX) { additions++; y-- } else { deletions++; x-- }
        }
        return additions to deletions
    }

    private fun isTextLike(name: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("text/") == true) return true
        return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "kt", "kts", "java", "groovy", "gradle", "xml", "json", "js", "mjs", "cjs", "ts", "tsx", "jsx",
            "html", "htm", "css", "scss", "sass", "less", "md", "markdown", "txt", "properties", "yaml", "yml",
            "toml", "sh", "bash", "zsh", "bat", "cmd", "sql", "c", "h", "cpp", "hpp", "cc", "rs", "go", "py",
            "rb", "php", "swift", "dart", "ini", "cfg", "conf" -> true
            else -> false
        }
    }

    companion object {
        private const val MAX_FILE_BYTES = 2L * 1024L * 1024L
        private val EXCLUDED_DIRECTORIES = setOf(".git", ".gradle", ".idea", "build", "out", "target", "node_modules")
    }
}

object WorkspaceChangeTrackerRegistry {
    private val trackers = ConcurrentHashMap<String, WorkspaceChangeTracker>()
    fun get(workspace: Workspace, fileSystem: WorkspaceFileSystem): WorkspaceChangeTracker =
        trackers.getOrPut(workspace.id) { WorkspaceChangeTracker(fileSystem) }
}
