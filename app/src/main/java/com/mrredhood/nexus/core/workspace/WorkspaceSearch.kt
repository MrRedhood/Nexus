package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** Result of a recursive workspace search. */
data class WorkspaceSearchResult(
    val relativePath: String,
    val name: String,
    val type: EntryType,
    val lineNumber: Int? = null,
    val lineText: String? = null,
    val matchStart: Int? = null,
    val matchEnd: Int? = null
)

data class WorkspaceSearchOptions(
    val query: String,
    val searchFileNames: Boolean = true,
    val searchContents: Boolean = true,
    val caseSensitive: Boolean = false,
    val useRegex: Boolean = false,
    val maxResults: Int = 200,
    val maxFileBytes: Long = 2L * 1024L * 1024L,
    val excludedDirectories: Set<String> = DEFAULT_EXCLUDED_DIRECTORIES
) {
    init {
        require(query.isNotEmpty()) { "Search query cannot be empty" }
        require(maxResults in 1..5000) { "maxResults must be between 1 and 5000" }
        require(maxFileBytes > 0) { "maxFileBytes must be positive" }
    }

    companion object {
        val DEFAULT_EXCLUDED_DIRECTORIES = setOf(
            ".git", ".gradle", ".idea", "build", "out", "target", "node_modules"
        )
    }
}

/**
 * Recursive, SAF-compatible workspace search. It deliberately works through
 * WorkspaceFileSystem so search never escapes the selected workspace tree.
 */
class WorkspaceSearch(private val fileSystem: WorkspaceFileSystem) {
    suspend fun search(
        workspace: Workspace,
        options: WorkspaceSearchOptions
    ): List<WorkspaceSearchResult> = withContext(Dispatchers.IO) {
        val results = ArrayList<WorkspaceSearchResult>(minOf(options.maxResults, 64))
        val nameMatcher = Matcher(options)
        val contentMatcher = Matcher(options)

        suspend fun walk(path: String) {
            coroutineContext.ensureActive()
            if (results.size >= options.maxResults) return

            val entries = fileSystem.list(workspace, path)
            for (entry in entries) {
                coroutineContext.ensureActive()
                if (results.size >= options.maxResults) return

                if (entry.type == EntryType.DIRECTORY) {
                    if (entry.name !in options.excludedDirectories) walk(entry.relativePath)
                    continue
                }

                if (options.searchFileNames) {
                    val match = nameMatcher.find(entry.name)
                    if (match != null) {
                        results += WorkspaceSearchResult(
                            relativePath = entry.relativePath,
                            name = entry.name,
                            type = EntryType.FILE,
                            matchStart = match.first,
                            matchEnd = match.second
                        )
                        if (results.size >= options.maxResults) return
                    }
                }

                if (!options.searchContents || entry.sizeBytes < 0L || entry.sizeBytes > options.maxFileBytes) continue
                if (!isTextLike(entry.name, entry.mimeType)) continue

                try {
                    val file = fileSystem.read(workspace, entry.relativePath)
                    val content = file.content
                    if (looksBinary(content)) continue
                    val lines = content.split('\n')
                    for (index in lines.indices) {
                        coroutineContext.ensureActive()
                        if (results.size >= options.maxResults) return
                        val line = lines[index].removeSuffix("\r")
                        val match = contentMatcher.find(line) ?: continue
                        results += WorkspaceSearchResult(
                            relativePath = entry.relativePath,
                            name = entry.name,
                            type = EntryType.FILE,
                            lineNumber = index + 1,
                            lineText = line,
                            matchStart = match.first,
                            matchEnd = match.second
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A single unreadable file must not abort a workspace search.
                }
            }
        }

        walk("")
        results
    }

    private class Matcher(private val options: WorkspaceSearchOptions) {
        private val normalizedQuery = if (options.caseSensitive) options.query else options.query.lowercase(Locale.ROOT)
        private val regex = if (options.useRegex) {
            runCatching { Regex(options.query, if (options.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)) }.getOrNull()
        } else null

        fun find(value: String): Pair<Int, Int>? {
            if (options.useRegex) {
                val match = regex?.find(value) ?: return null
                return match.range.first to match.range.last + 1
            }
            val target = if (options.caseSensitive) value else value.lowercase(Locale.ROOT)
            val start = target.indexOf(normalizedQuery)
            return if (start >= 0) start to start + options.query.length else null
        }
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

    private fun looksBinary(content: String): Boolean {
        val sample = content.take(4096)
        return sample.any { it == '\u0000' }
    }
}
