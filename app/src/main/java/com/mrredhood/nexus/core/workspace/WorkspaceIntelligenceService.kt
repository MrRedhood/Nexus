package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Converts the workspace index into compact project-level intelligence. */
class WorkspaceIntelligenceService(private val indexService: WorkspaceIndexService) {
    suspend fun analyze(workspace: Workspace): WorkspaceIntelligence = withContext(Dispatchers.Default) {
        val index = indexService.build(workspace)
        val languages = index.files.groupingBy { it.language }.eachCount().entries.sortedByDescending { it.value }
        val importantFiles = index.files.map { it.path }.filter { path ->
            val name = path.substringAfterLast('/').lowercase()
            name in IMPORTANT_FILES || name.endsWith(".gradle") || name.endsWith(".gradle.kts")
        }.take(30)

        val projectType = when {
            index.files.any { it.name == "settings.gradle" || it.name == "settings.gradle.kts" } -> "Android/Gradle"
            index.files.any { it.name == "pubspec.yaml" } -> "Flutter/Dart"
            index.files.any { it.name == "package.json" } -> "Node/Web"
            index.files.any { it.name == "pyproject.toml" || it.name == "requirements.txt" } -> "Python"
            index.files.any { it.name == "Cargo.toml" } -> "Rust"
            index.files.any { it.name == "go.mod" } -> "Go"
            else -> "Generic"
        }

        WorkspaceIntelligence(
            index = index,
            projectType = projectType,
            languages = languages.map { it.key to it.value },
            importantFiles = importantFiles
        )
    }

    companion object {
        private val IMPORTANT_FILES = setOf(
            "readme.md", "license", "build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "package.json", "pubspec.yaml", "pyproject.toml", "cargo.toml", "go.mod"
        )
    }
}

data class WorkspaceIntelligence(
    val index: WorkspaceIndex,
    val projectType: String,
    val languages: List<Pair<String, Int>>,
    val importantFiles: List<String>
) {
    fun toContextText(): String = buildString {
        appendLine("PROJECT TYPE: $projectType")
        appendLine("FILES: ${index.totalFiles}${if (index.truncated) "+" else ""}")
        appendLine("DIRECTORIES: ${index.directories.size}")
        appendLine("SYMBOLS: ${index.totalSymbols}")
        if (languages.isNotEmpty()) {
            appendLine("LANGUAGES:")
            languages.take(10).forEach { (language, count) -> appendLine("- $language: $count files") }
        }
        if (importantFiles.isNotEmpty()) {
            appendLine("IMPORTANT FILES:")
            importantFiles.forEach { appendLine("- $it") }
        }
    }.trim()
}
