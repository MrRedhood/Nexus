package com.mrredhood.nexus.core.ai

import com.mrredhood.nexus.core.settings.NexusSettingsRuntime
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Structured action protocol emitted by Nexus AI for workspace operations. */
object NexusActionProtocol {
    private const val OPEN = "<nexus-action>"
    private const val CLOSE = "</nexus-action>"

    private val allowedTypes = setOf(
        "list_files", "open_file", "focus_file", "read_file", "create_file", "create_directory",
        "patch_file", "replace_file", "delete_file", "rename_file", "copy_file", "move_file"
    )

    fun extract(text: String): List<NexusActionProposal> {
        if (text.isBlank()) return emptyList()
        val proposals = mutableListOf<NexusActionProposal>()
        var cursor = 0
        while (true) {
            val start = text.indexOf(OPEN, cursor)
            if (start < 0) break
            val payloadStart = start + OPEN.length
            val end = text.indexOf(CLOSE, payloadStart)
            if (end < 0) break
            parsePayload(text.substring(payloadStart, end).trim())?.let(proposals::add)
            cursor = end + CLOSE.length
        }
        return proposals
    }

    fun stripProtocol(text: String): String = text
        .replace(Regex("<nexus-action>.*?</nexus-action>", RegexOption.DOT_MATCHES_ALL), "")
        .trim()

    fun encode(action: NexusAction): String = JSONObject().apply {
        put("id", action.id)
        put("type", action.type)
        action.path?.let { put("path", it) }
        action.destination?.let { put("destination", it) }
        action.newName?.let { put("newName", it) }
        action.mimeType?.let { put("mimeType", it) }
        action.content?.let { put("content", it) }
        action.patch?.let { put("patch", it) }
    }.toString()

    fun encodeBatch(actions: List<NexusAction>): String = JSONArray().apply {
        actions.forEach { put(JSONObject(encode(it))) }
    }.toString()

    private fun parsePayload(payload: String): NexusActionProposal? = runCatching {
        val json = JSONObject(payload)
        val type = json.optString("type").trim()
        if (type !in allowedTypes) return null
        val path = json.optString("path").trim().takeIf { it.isNotBlank() }
        val destination = json.optString("destination").trim().takeIf { it.isNotBlank() }
        val newName = json.optString("newName").trim().takeIf { it.isNotBlank() }
        val mimeType = json.optString("mimeType").trim().takeIf { it.isNotBlank() }
        val content = json.optString("content").takeIf { json.has("content") }
        val patch = json.optString("patch").takeIf { json.has("patch") }
        require(type == "list_files" || path != null) { "$type requires a path" }
        require(type !in setOf("copy_file", "move_file") || destination != null) { "$type requires destination" }
        require(type != "rename_file" || newName != null) { "rename_file requires newName" }
        require(type != "replace_file" || content != null) { "replace_file requires content" }
        require(type != "patch_file" || patch != null) { "patch_file requires patch" }
        NexusActionProposal(
            id = json.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            action = NexusAction(type, path, destination, newName, mimeType, content, patch)
        )
    }.getOrNull()
}

data class NexusAction(
    val type: String,
    val path: String? = null,
    val destination: String? = null,
    val newName: String? = null,
    val mimeType: String? = null,
    val content: String? = null,
    val patch: String? = null,
    val id: String = UUID.randomUUID().toString()
)

data class NexusActionProposal(
    val id: String,
    val action: NexusAction,
    val status: NexusActionStatus = NexusActionStatus.PROPOSED
)

enum class NexusActionStatus { PROPOSED, APPROVED, REJECTED, EXECUTING, COMPLETED, FAILED }

/** Central AI workspace permission policy. */
object NexusActionPolicy {
    private val mutatingTypes = setOf(
        "create_file", "create_directory", "patch_file", "replace_file",
        "delete_file", "rename_file", "copy_file", "move_file"
    )
    private val destructiveTypes = setOf("delete_file", "rename_file", "move_file", "copy_file")

    fun isMutating(action: NexusAction): Boolean = action.type in mutatingTypes

    fun canAutoExecute(action: NexusAction, permissionMode: String): Boolean {
        if (!isMutating(action)) return true
        return when (permissionMode.lowercase()) {
            "never", "restricted" -> false
            "some" -> action.type !in destructiveTypes
            "standard", "autonomous", "full" -> true
            else -> false
        }
    }

    fun requiresApproval(action: NexusAction, permissionMode: String): Boolean =
        isMutating(action) && !canAutoExecute(action, permissionMode) && permissionMode.lowercase() != "never"

    /** Compatibility helper: always uses the live workspace permission setting. */
    fun requiresApproval(action: NexusAction): Boolean =
        requiresApproval(action, NexusSettingsRuntime.current().workspacePermission)
}
