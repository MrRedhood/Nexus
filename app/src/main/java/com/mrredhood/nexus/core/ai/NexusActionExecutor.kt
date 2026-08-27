package com.mrredhood.nexus.core.ai

import com.mrredhood.nexus.core.settings.NexusSettingsRuntime
import com.mrredhood.nexus.core.workspace.EntryType
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import java.security.MessageDigest

/** Executes AI workspace actions, enforcing the live permission mode and recording recovery snapshots. */
class NexusActionExecutor(private val fileSystem: WorkspaceFileSystem) {
    private val snapshotStore = NexusActionSnapshotStore(fileSystem)

    suspend fun preview(workspace: Workspace, proposal: NexusActionProposal): NexusActionReview? {
        val action = proposal.action
        val path = action.path?.trim().orEmpty()
        return when (action.type) {
            "create_file" -> NexusDiffBuilder.build(proposal.id, path, "", action.content.orEmpty())
            "replace_file" -> { val current = fileSystem.read(workspace, requirePath(action)); NexusDiffBuilder.build(proposal.id, path, current.content, action.content ?: error("replace_file requires content")) }
            "patch_file" -> { val current = fileSystem.read(workspace, requirePath(action)); NexusDiffBuilder.build(proposal.id, path, current.content, applyUnifiedPatch(current.content, action.patch ?: error("patch_file requires patch"))) }
            "delete_file" -> { val target = requirePath(action); if (fileSystem.isDirectory(workspace, target)) NexusDiffBuilder.build(proposal.id, target, "[directory]", "") else { val current = fileSystem.read(workspace, target); NexusDiffBuilder.build(proposal.id, path, current.content, "") } }
            else -> null
        }
    }

    suspend fun execute(workspace: Workspace, action: NexusAction): ActionExecutionResult {
        val permissionMode = NexusSettingsRuntime.current().workspacePermission
        if (NexusActionPolicy.isMutating(action) && permissionMode.equals("never", true)) {
            val message = "Workspace change blocked: AI permission is set to Never."
            NexusActionExecutionRegistry.record(NexusActionExecutionSummary(action.id, action.type, action.path, false, message = message))
            return ActionExecutionResult(false, message)
        }

        val conflict = verifyExpectedHash(workspace, action)
        if (conflict != null) {
            NexusActionExecutionRegistry.record(NexusActionExecutionSummary(action.id, action.type, action.path, false, message = conflict))
            return ActionExecutionResult(false, conflict)
        }

        val snapshotId = runCatching { snapshotStore.capture(workspace, action) }.getOrNull()
        val result = runCatching {
            when (action.type) {
                "list_files" -> { val directory = action.path?.trim().orEmpty(); val entries = fileSystem.list(workspace, directory); val output = entries.joinToString("\n") { "${if (it.type == EntryType.DIRECTORY) "DIR " else "FILE"} ${it.relativePath} (${it.sizeBytes} bytes)" }; ActionExecutionResult(true, "Listed ${entries.size} entries", output) }
                "read_file" -> { val path = requirePath(action); val file = fileSystem.read(workspace, path); ActionExecutionResult(true, "Read $path (sha256=${sha256(file.content)})", file.content) }
                "open_file", "focus_file" -> { val path = requirePath(action); require(fileSystem.exists(workspace, path)) { "File not found: $path" }; ActionExecutionResult(true, "${action.type} requested for $path") }
                "create_file" -> { val path = requirePath(action); val content = action.content.orEmpty(); val file = fileSystem.write(workspace, path, content, action.mimeType ?: "text/plain"); ActionExecutionResult(true, "Created $path (+${physicalLineCount(content)} lines)", file.content, snapshotId) }
                "create_directory" -> { val path = requirePath(action); fileSystem.createDirectory(workspace, path); ActionExecutionResult(true, "Created directory $path", snapshotId = snapshotId) }
                "replace_file" -> { val path = requirePath(action); val content = action.content ?: error("replace_file requires content"); val current = fileSystem.read(workspace, path); val review = NexusDiffBuilder.build(action.id, path, current.content, content); fileSystem.write(workspace, path, content); ActionExecutionResult(true, "Updated $path (+${review.additions} -${review.deletions} lines)", content, snapshotId) }
                "patch_file" -> { val path = requirePath(action); val patch = action.patch ?: error("patch_file requires patch"); val current = fileSystem.read(workspace, path); val updated = applyUnifiedPatch(current.content, patch); val review = NexusDiffBuilder.build(action.id, path, current.content, updated); fileSystem.writeIfUnchanged(workspace, path, updated, current.mimeType ?: "text/plain", current.sizeBytes, current.lastModified); ActionExecutionResult(true, "Patched $path (+${review.additions} -${review.deletions} lines)", updated, snapshotId) }
                "delete_file" -> { val path = requirePath(action); val directory = fileSystem.isDirectory(workspace, path); val deletedLines = if (!directory) physicalLineCount(fileSystem.read(workspace, path).content) else 0; fileSystem.delete(workspace, path); ActionExecutionResult(true, if (directory) "Deleted directory $path" else "Deleted $path (-$deletedLines lines)", snapshotId = snapshotId) }
                "rename_file" -> { val path = requirePath(action); val newName = action.newName?.trim().orEmpty(); require(newName.isNotBlank()) { "rename_file requires newName" }; val renamed = fileSystem.rename(workspace, path, newName); ActionExecutionResult(true, "Renamed $path to ${renamed.relativePath}", snapshotId = snapshotId) }
                "copy_file" -> { val source = requirePath(action); val destination = requireDestination(action); fileSystem.copy(workspace, source, destination); ActionExecutionResult(true, "Copied $source to $destination", snapshotId = snapshotId) }
                "move_file" -> { val source = requirePath(action); val destination = requireDestination(action); fileSystem.move(workspace, source, destination); ActionExecutionResult(true, "Moved $source to $destination", snapshotId = snapshotId) }
                else -> ActionExecutionResult(false, "Unsupported action: ${action.type}")
            }
        }.getOrElse { ActionExecutionResult(false, it.message ?: "Action failed") }

        if (!result.success && snapshotId != null) snapshotStore.forget(snapshotId)
        val counts = parseCounts(result.message)
        NexusActionExecutionRegistry.record(NexusActionExecutionSummary(action.id, action.type, action.path, result.success, counts.first, counts.second, result.message, result.snapshotId))
        if (result.success && result.snapshotId != null) {
            NexusActionExecutionRegistry.registerRollback(action.id) { targetWorkspace, storedSnapshotId ->
                snapshotStore.rollback(targetWorkspace, storedSnapshotId)
            }
        }
        return result
    }

    suspend fun rollback(workspace: Workspace, actionId: String): Boolean = snapshotStore.rollback(workspace, actionId)

    private suspend fun verifyExpectedHash(workspace: Workspace, action: NexusAction): String? {
        val expected = action.expectedHash?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        if (action.type !in setOf("patch_file", "replace_file", "delete_file", "rename_file", "copy_file", "move_file")) return null
        val path = action.path?.trim().orEmpty()
        if (path.isBlank() || !fileSystem.exists(workspace, path) || fileSystem.isDirectory(workspace, path)) {
            return "Edit conflict: expected file $path to exist with sha256=$expected, but it is missing or is a directory. Refresh the file and retry."
        }
        val actual = sha256(fileSystem.read(workspace, path).content)
        return if (actual == expected) null else "Edit conflict: $path changed after AI inspection (expected sha256=$expected, actual sha256=$actual). Refresh and review the current file before applying the change."
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun physicalLineCount(content: String): Int { val normalized = content.replace("\r\n", "\n").replace('\r', '\n'); if (normalized.isEmpty()) return 0; return normalized.count { it == '\n' } + if (normalized.last() == '\n') 0 else 1 }
    private fun parseCounts(message: String): Pair<Int, Int> { val additions = Regex("\\+(\\d+) lines?").find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0; val deletions = Regex("-(\\d+) lines?").find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0; return additions to deletions }
    private fun requirePath(action: NexusAction): String = action.path?.trim()?.takeIf { it.isNotEmpty() } ?: error("${action.type} requires a file or directory path")
    private fun requireDestination(action: NexusAction): String = action.destination?.trim()?.takeIf { it.isNotEmpty() } ?: error("${action.type} requires destination")
    private fun indexOfFrom(lines: List<String>, element: String, startIndex: Int): Int { for (index in startIndex.coerceAtLeast(0) until lines.size) if (lines[index] == element) return index; return -1 }

    private fun applyUnifiedPatch(original: String, patch: String): String {
        val originalLines = original.replace("\r\n", "\n").split("\n").toMutableList()
        val patchLines = patch.replace("\r\n", "\n").split("\n")
        val hunks = patchLines.filter { it.startsWith("@@") }
        require(hunks.isNotEmpty()) { "patch_file requires a unified diff with at least one hunk" }
        var offset = 0; var cursor = 0
        for (hunkIndex in hunks.indices) {
            val header = hunks[hunkIndex]; val headerIndex = indexOfFrom(patchLines, header, cursor); require(headerIndex >= 0) { "Unable to locate unified diff hunk" }
            val match = Regex("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@").find(header) ?: error("Invalid unified diff hunk: $header")
            val oldStart = match.groupValues[1].toInt() - 1 + offset
            val nextHeader = if (hunkIndex + 1 < hunks.size) indexOfFrom(patchLines, hunks[hunkIndex + 1], headerIndex + 1).takeIf { it >= 0 } ?: patchLines.size else patchLines.size
            var sourceIndex = oldStart; val replacement = mutableListOf<String>()
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
            val consumed = sourceIndex - oldStart; originalLines.subList(oldStart, sourceIndex).clear(); originalLines.addAll(oldStart, replacement); offset += replacement.size - consumed; cursor = nextHeader
        }
        return originalLines.joinToString("\n")
    }
}

data class ActionExecutionResult(val success: Boolean, val message: String, val output: String? = null, val snapshotId: String? = null)
