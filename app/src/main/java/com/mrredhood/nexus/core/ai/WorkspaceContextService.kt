package com.mrredhood.nexus.core.ai

import com.mrredhood.nexus.core.workspace.EntryType
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds a compact, live summary of the currently opened workspace. */
class WorkspaceContextService(private val fileSystem: WorkspaceFileSystem) {
    suspend fun refresh(workspace: Workspace) = withContext(Dispatchers.IO) {
        val directories = ArrayDeque<String>()
        directories.add("")
        val files = mutableListOf<String>()
        val counts = linkedMapOf<String, Int>()
        var directoryCount = 0
        var totalBytes = 0L

        while (directories.isNotEmpty() && files.size < MAX_FILES) {
            val directory = directories.removeFirst()
            val entries = fileSystem.list(workspace, directory)
            for (entry in entries) {
                when (entry.type) {
                    EntryType.DIRECTORY -> {
                        directoryCount++
                        if (directories.size + files.size < MAX_TRAVERSAL_ITEMS) directories.add(entry.relativePath)
                    }
                    EntryType.FILE -> {
                        files.add(entry.relativePath)
                        totalBytes += entry.sizeBytes
                        val extension = entry.name.substringAfterLast('.', "").lowercase().ifBlank { "[no extension]" }
                        counts[extension] = (counts[extension] ?: 0) + 1
                        if (files.size >= MAX_FILES) break
                    }
                }
            }
        }

        val topExtensions = counts.entries.sortedByDescending { it.value }.take(10)
        val sampleFiles = files.take(MAX_SAMPLE_FILES)
        val summary = buildString {
            appendLine("WORKSPACE: ${workspace.displayName}")
            appendLine("FILES: ${files.size}${if (files.size >= MAX_FILES) "+" else ""}")
            appendLine("DIRECTORIES: $directoryCount")
            appendLine("TOTAL FILE SIZE: $totalBytes bytes")
            if (topExtensions.isNotEmpty()) {
                appendLine("FILE TYPES:")
                topExtensions.forEach { (extension, count) -> appendLine("- $extension: $count") }
            }
            if (sampleFiles.isNotEmpty()) {
                appendLine("SAMPLE FILES:")
                sampleFiles.forEach { appendLine("- $it") }
            }
        }.trim()

        ChatContextStore.updateWorkspaceSummary(workspace.id, summary)
        summary
    }

    companion object {
        private const val MAX_FILES = 300
        private const val MAX_SAMPLE_FILES = 80
        private const val MAX_TRAVERSAL_ITEMS = 600
    }
}
