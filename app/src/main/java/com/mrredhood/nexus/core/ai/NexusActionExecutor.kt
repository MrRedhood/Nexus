package com.mrredhood.nexus.core.ai

import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem

/** Executes approved Nexus file actions against the active workspace. */
class NexusActionExecutor(private val fileSystem: WorkspaceFileSystem) {
    suspend fun preview(workspace: Workspace, proposal: NexusActionProposal): NexusActionReview? {
        val action = proposal.action
        if (!NexusActionPolicy.requiresApproval(action)) return null
        val path = requirePath(action)
        val current = fileSystem.read(workspace, path)
        val proposed = when (action.type) {
            "replace_file" -> action.content ?: error("replace_file requires content")
            "patch_file" -> {
                val patch = action.patch ?: error("patch_file requires patch")
                applyUnifiedPatch(current.content, patch)
            }
            else -> return null
        }
        return NexusDiffBuilder.build(proposal.id, path, current.content, proposed)
    }

    suspend fun execute(workspace: Workspace, action: NexusAction): ActionExecutionResult {
        return runCatching {
            when (action.type) {
                "read_file" -> {
                    val path = requirePath(action)
                    val file = fileSystem.read(workspace, path)
                    ActionExecutionResult(true, "Read $path", file.content)
                }
                "replace_file" -> {
                    val path = requirePath(action)
                    val content = action.content ?: error("replace_file requires content")
                    fileSystem.write(workspace, path, content)
                    ActionExecutionResult(true, "Updated $path")
                }
                "patch_file" -> {
                    val path = requirePath(action)
                    val patch = action.patch ?: error("patch_file requires patch")
                    val current = fileSystem.read(workspace, path)
                    val updated = applyUnifiedPatch(current.content, patch)
                    fileSystem.writeIfUnchanged(workspace, path, updated, current.mimeType ?: "text/plain", current.sizeBytes, current.lastModified)
                    ActionExecutionResult(true, "Patched $path")
                }
                "open_file", "focus_file" -> {
                    val path = requirePath(action)
                    require(fileSystem.exists(workspace, path)) { "File not found: $path" }
                    ActionExecutionResult(true, "${action.type} requested for $path")
                }
                else -> ActionExecutionResult(false, "Unsupported action: ${action.type}")
            }
        }.getOrElse { ActionExecutionResult(false, it.message ?: "Action failed") }
    }

    private fun requirePath(action: NexusAction): String = action.path?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("${action.type} requires a file path")

    private fun indexOfFrom(lines: List<String>, element: String, startIndex: Int): Int {
        val start = startIndex.coerceAtLeast(0)
        for (index in start until lines.size) if (lines[index] == element) return index
        return -1
    }

    private fun applyUnifiedPatch(original: String, patch: String): String {
        val originalLines = original.split("\n").toMutableList()
        val patchLines = patch.replace("\r\n", "\n").split("\n")
        val hunks = patchLines.filter { it.startsWith("@@") }
        require(hunks.isNotEmpty()) { "patch_file requires a unified diff with at least one hunk" }

        var offset = 0
        var cursor = 0
        for (hunkIndex in hunks.indices) {
            val header = hunks[hunkIndex]
            val headerIndex = indexOfFrom(patchLines, header, cursor)
            require(headerIndex >= 0) { "Unable to locate unified diff hunk" }
            val match = Regex("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@").find(header)
                ?: error("Invalid unified diff hunk: $header")
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
