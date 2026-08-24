package com.mrredhood.nexus.core.ai

import com.mrredhood.nexus.core.workspace.EntryType
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem

/** Executes AI workspace actions and records their change statistics. */
class NexusActionExecutor(private val fileSystem: WorkspaceFileSystem) {
    suspend fun preview(workspace: Workspace, proposal: NexusActionProposal): NexusActionReview? {
        val action = proposal.action
        val path = action.path?.trim().orEmpty()
        return when (action.type) {
            "create_file" -> NexusDiffBuilder.build(proposal.id, path, "", action.content.orEmpty())
            "replace_file" -> {
                val current = fileSystem.read(workspace, requirePath(action))
                NexusDiffBuilder.build(proposal.id, path, current.content, action.content ?: error("replace_file requires content"))
            }
            "patch_file" -> {
                val current = fileSystem.read(workspace, requirePath(action))
                NexusDiffBuilder.build(proposal.id, path, current.content, applyUnifiedPatch(current.content, action.patch ?: error("patch_file requires patch")))
            }
            "delete_file" -> {
                val current = fileSystem.read(workspace, requirePath(action))
                NexusDiffBuilder.build(proposal.id, path, current.content, "")
            }
            else -> null
        }
    }

    suspend fun execute(workspace: Workspace, action: NexusAction): ActionExecutionResult {
        val result = runCatching {
            when (action.type) {
                "list_files" -> {
                    val directory = action.path?.trim().orEmpty()
                    val entries = fileSystem.list(workspace, directory)
                    val output = entries.joinToString("\n") { "${if (it.type == EntryType.DIRECTORY) "DIR " else "FILE"} ${it.relativePath} (${it.sizeBytes} bytes)" }
                    ActionExecutionResult(true, "Listed ${entries.size} entries", output)
                }
                "read_file" -> {
                    val path = requirePath(action)
                    val file = fileSystem.read(workspace, path)
                    ActionExecutionResult(true, "Read $path", file.content)
                }
                "open_file", "focus_file" -> {
                    val path = requirePath(action)
                    require(fileSystem.exists(workspace, path)) { "File not found: $path" }
                    ActionExecutionResult(true, "${action.type} requested for $path")
                }
                "create_file" -> {
                    val path = requirePath(action)
                    val content = action.content.orEmpty()
                    val file = fileSystem.write(workspace, path, content, action.mimeType ?: "text/plain")
                    ActionExecutionResult(true, "Created $path", file.content)
                }
                "create_directory" -> {
                    val path = requirePath(action)
                    fileSystem.createDirectory(workspace, path)
                    ActionExecutionResult(true, "Created directory $path")
                }
                "replace_file" -> {
                    val path = requirePath(action)
                    val content = action.content ?: error("replace_file requires content")
                    val current = fileSystem.read(workspace, path)
                    val review = NexusDiffBuilder.build(action.id, path, current.content, content)
                    fileSystem.write(workspace, path, content)
                    ActionExecutionResult(true, "Updated $path (+${review.additions} -${review.deletions} lines)", content)
                }
                "patch_file" -> {
                    val path = requirePath(action)
                    val patch = action.patch ?: error("patch_file requires patch")
                    val current = fileSystem.read(workspace, path)
                    val updated = applyUnifiedPatch(current.content, patch)
                    val review = NexusDiffBuilder.build(action.id, path, current.content, updated)
                    fileSystem.writeIfUnchanged(workspace, path, updated, current.mimeType ?: "text/plain", current.sizeBytes, current.lastModified)
                    ActionExecutionResult(true, "Patched $path (+${review.additions} -${review.deletions} lines)", updated)
                }
                "delete_file" -> {
                    val path = requirePath(action)
                    val current = fileSystem.read(workspace, path)
                    val deletedLines = current.content.split('\n').let { if (it.size == 1 && it[0].isEmpty()) 0 else it.size }
                    fileSystem.delete(workspace, path)
                    ActionExecutionResult(true, "Deleted $path (-$deletedLines lines)")
                }
                "rename_file" -> {
                    val path = requirePath(action)
                    val newName = action.newName?.trim().orEmpty()
                    require(newName.isNotBlank()) { "rename_file requires newName" }
                    val result = fileSystem.rename(workspace, path, newName)
                    ActionExecutionResult(true, "Renamed $path to ${result.relativePath}")
                }
                "copy_file" -> {
                    val source = requirePath(action)
                    val destination = requireDestination(action)
                    fileSystem.copy(workspace, source, destination)
                    ActionExecutionResult(true, "Copied $source to $destination")
                }
                "move_file" -> {
                    val source = requirePath(action)
                    val destination = requireDestination(action)
                    fileSystem.move(workspace, source, destination)
                    ActionExecutionResult(true, "Moved $source to $destination")
                }
                else -> ActionExecutionResult(false, "Unsupported action: ${action.type}")
            }
        }.getOrElse { ActionExecutionResult(false, it.message ?: "Action failed") }

        val review = if (result.success && action.type in setOf("create_file", "replace_file", "patch_file", "delete_file")) {
            runCatching { preview(workspace, action.let { NexusActionProposal(it.id, it) }) }.getOrNull()
        } else null
        NexusActionExecutionRegistry.record(
            NexusActionExecutionSummary(
                actionId = action.id,
                actionType = action.type,
                path = action.path,
                success = result.success,
                additions = review?.additions ?: 0,
                deletions = review?.deletions ?: 0,
                message = result.message
            )
        )
        return result
    }

    private fun requirePath(action: NexusAction): String = action.path?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("${action.type} requires a file or directory path")

    private fun requireDestination(action: NexusAction): String = action.destination?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("${action.type} requires destination")

    private fun indexOfFrom(lines: List<String>, element: String, startIndex: Int): Int {
        for (index in startIndex.coerceAtLeast(0) until lines.size) if (lines[index] == element) return index
        return -1
    }

    private fun applyUnifiedPatch(original: String, patch: String): String {
        val originalLines = original.replace("\r\n", "\n").split("\n").toMutableList()
        val patchLines = patch.replace("\r\n", "\n").split("\n")
        val hunks = patchLines.filter { it.startsWith("@@") }
        require(hunks.isNotEmpty()) { "patch_file requires a unified diff with at least one hunk" }
        var offset = 0
        var cursor = 0
        for (hunkIndex in hunks.indices) {
            val header = hunks[hunkIndex]
            val headerIndex = indexOfFrom(patchLines, header, cursor)
            require(headerIndex >= 0) { "Unable to locate unified diff hunk" }
            val match = Regex("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@").find(header) ?: error("Invalid unified diff hunk: $header")
            val oldStart = match.groupValues[1].toInt() - 1 + offset
            val nextHeader = if (hunkIndex + 1 < hunks.size) indexOfFrom(patchLines, hunks[hunkIndex + 1], headerIndex + 1).takeIf { it >= 0 } ?: patchLines.size else patchLines.size
            var sourceIndex = oldStart
            val replacement = mutableListOf<String>()
            for (line in patchLines.subList(headerIndex + 1, nextHeader)) {
                if (line.isEmpty()) continue
                when (line[0]) {
                    ' ' -> { require(sourceIndex < originalLines.size && originalLines[sourceIndex] == line.substring(1)) { "Patch context does not match $sourceIndex" }; replacement += originalLines[sourceIndex++] }
                    '-' -> { require(sourceIndex < originalLines.size && originalLines[sourceIndex] == line.substring(1)) { "Patch removal does not match $sourceIndex" }; sourceIndex++ }
                    '+' -> replacement += line.substring(1)
                    '\\' -> Unit
                    else -> error("Invalid unified diff line")
                }
            }
            val consumed = sourceIndex - oldStart
            originalLines.subList(oldStart, sourceIndex).clear()
            originalLines.addAll(oldStart, replacement)
            offset += replacement.size - consumed
            cursor = nextHeader
        }
        return originalLines.joinToString("\n")
    }
}

data class ActionExecutionResult(val success: Boolean, val message: String, val output: String? = null)
