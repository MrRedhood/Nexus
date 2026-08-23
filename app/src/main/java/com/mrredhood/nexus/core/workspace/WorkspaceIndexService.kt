package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lightweight workspace index for AI context and project intelligence. */
class WorkspaceIndexService(private val fileSystem: WorkspaceFileSystem) {
    suspend fun build(workspace: Workspace): WorkspaceIndex = withContext(Dispatchers.IO) {
        val queue = ArrayDeque<String>()
        queue.add("")
        val files = mutableListOf<WorkspaceIndexEntry>()
        val directories = mutableListOf<String>()
        var truncated = false

        while (queue.isNotEmpty()) {
            val directory = queue.removeFirst()
            for (entry in fileSystem.list(workspace, directory)) {
                when (entry.type) {
                    EntryType.DIRECTORY -> {
                        if (!isExcluded(entry.relativePath)) {
                            directories += entry.relativePath
                            if (queue.size + files.size < MAX_ITEMS) queue.add(entry.relativePath)
                        }
                    }
                    EntryType.FILE -> {
                        if (isExcluded(entry.relativePath)) continue
                        if (files.size >= MAX_FILES) { truncated = true; continue }
                        val language = languageOf(entry.name)
                        val symbols = if (entry.sizeBytes <= MAX_SYMBOL_SCAN_BYTES && language in SOURCE_LANGUAGES) {
                            runCatching { extractSymbols(fileSystem.read(workspace, entry.relativePath).content, language) }
                                .getOrDefault(emptyList())
                        } else emptyList()
                        files += WorkspaceIndexEntry(entry.relativePath, entry.name, entry.sizeBytes, entry.lastModified, language, symbols)
                    }
                }
            }
            if (queue.size + files.size >= MAX_ITEMS) truncated = true
        }

        WorkspaceIndex(workspace.id, workspace.displayName, files, directories.distinct().sorted(), truncated)
    }

    private fun isExcluded(path: String): Boolean {
        val normalized = path.replace('\\', '/').trim('/')
        return EXCLUDED_SEGMENTS.any { normalized == it || normalized.startsWith("$it/") || normalized.contains("/$it/") }
    }

    private fun languageOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "js", "mjs", "cjs" -> "javascript"
        "ts", "tsx" -> "typescript"
        "jsx" -> "javascript-react"
        "py" -> "python"
        "dart" -> "dart"
        "c" -> "c"
        "cc", "cpp", "cxx", "h", "hpp" -> "cpp"
        "go" -> "go"
        "rs" -> "rust"
        "php" -> "php"
        "html", "htm" -> "html"
        "css", "scss", "sass", "less" -> "css"
        "xml" -> "xml"
        "json" -> "json"
        "yaml", "yml" -> "yaml"
        "md", "markdown" -> "markdown"
        "sh", "bash" -> "shell"
        else -> "text"
    }

    private fun extractSymbols(content: String, language: String): List<String> {
        val regex = when (language) {
            "kotlin" -> Regex("\\b(?:class|interface|object|fun|val|var)\\s+([A-Za-z_][A-Za-z0-9_]*)")
            "java" -> Regex("\\b(?:class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)")
            "javascript", "javascript-react", "typescript" -> Regex("\\b(?:class|function|const|let|var|interface|type|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)")
            "python" -> Regex("^\\s*(?:class|def)\\s+([A-Za-z_][A-Za-z0-9_]*)", RegexOption.MULTILINE)
            "dart" -> Regex("\\b(?:class|mixin|extension|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)")
            "go" -> Regex("\\b(?:type|func)\\s+([A-Za-z_][A-Za-z0-9_]*)")
            "rust" -> Regex("\\b(?:fn|struct|enum|trait|mod|type)\\s+([A-Za-z_][A-Za-z0-9_]*)")
            else -> return emptyList()
        }
        return regex.findAll(content).map { it.groupValues[1] }.distinct().take(MAX_SYMBOLS_PER_FILE).toList()
    }

    companion object {
        private const val MAX_FILES = 2000
        private const val MAX_ITEMS = 5000
        private const val MAX_SYMBOLS_PER_FILE = 80
        private const val MAX_SYMBOL_SCAN_BYTES = 512 * 1024L
        private val EXCLUDED_SEGMENTS = setOf(".git", ".gradle", "build", "dist", "out", "node_modules", "target", "coverage", ".idea")
        private val SOURCE_LANGUAGES = setOf("kotlin", "java", "javascript", "javascript-react", "typescript", "python", "dart", "c", "cpp", "go", "rust", "php")
    }
}

data class WorkspaceIndex(
    val workspaceId: String,
    val workspaceName: String,
    val files: List<WorkspaceIndexEntry>,
    val directories: List<String>,
    val truncated: Boolean
) {
    val totalFiles: Int get() = files.size
    val totalSymbols: Int get() = files.sumOf { it.symbols.size }

    fun find(query: String, limit: Int = 20): List<WorkspaceIndexEntry> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return files.take(limit)
        return files.asSequence()
            .filter { it.path.lowercase().contains(normalized) || it.name.lowercase().contains(normalized) || it.symbols.any { symbol -> symbol.lowercase().contains(normalized) } }
            .sortedBy { entry ->
                when {
                    entry.path.equals(query, true) -> 0
                    entry.name.equals(query, true) -> 1
                    entry.symbols.any { it.equals(query, true) } -> 2
                    else -> 3
                }
            }
            .take(limit)
            .toList()
    }
}

data class WorkspaceIndexEntry(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val language: String,
    val symbols: List<String>
)
