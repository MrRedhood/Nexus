package com.mrredhood.nexus.core.ai

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Structured action protocol emitted by Nexus AI instead of fragile prose commands. */
object NexusActionProtocol {
    private const val OPEN = "<nexus-action>"
    private const val CLOSE = "</nexus-action>"

    private val allowedTypes = setOf(
        "open_file",
        "focus_file",
        "read_file",
        "patch_file",
        "replace_file"
    )

    fun extract(text: String): List<NexusActionProposal> {
        if (text.isBlank()) return emptyList()
        val proposals = mutableListOf<NexusActionProposal>()
        var cursor = 0
        while (true) {
            val start = text.indexOf(OPEN, cursor, ignoreCase = false)
            if (start < 0) break
            val payloadStart = start + OPEN.length
            val end = text.indexOf(CLOSE, payloadStart, ignoreCase = false)
            if (end < 0) break
            val payload = text.substring(payloadStart, end).trim()
            parsePayload(payload)?.let(proposals::add)
            cursor = end + CLOSE.length
        }
        return proposals
    }

    fun stripProtocol(text: String): String {
        if (text.isBlank()) return text
        return text.replace(Regex("<nexus-action>.*?</nexus-action>", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
    }

    fun encode(action: NexusAction): String = JSONObject().apply {
        put("id", action.id)
        put("type", action.type)
        action.path?.let { put("path", it) }
        action.content?.let { put("content", it) }
        action.patch?.let { put("patch", it) }
    }.toString()

    fun encodeBatch(actions: List<NexusAction>): String {
        val array = JSONArray()
        actions.forEach { action -> array.put(JSONObject(encode(action))) }
        return array.toString()
    }

    private fun parsePayload(payload: String): NexusActionProposal? {
        return runCatching {
            val json = JSONObject(payload)
            val type = json.optString("type").trim()
            if (type !in allowedTypes) return null
            val path = json.optString("path").trim().takeIf { it.isNotBlank() }
            val content = json.optString("content").takeIf { json.has("content") }
            val patch = json.optString("patch").takeIf { json.has("patch") }
            require(type == "open_file" || type == "focus_file" || path != null) { "Action path is required" }
            require(type != "replace_file" || content != null) { "replace_file requires content" }
            require(type != "patch_file" || patch != null) { "patch_file requires patch" }
            NexusActionProposal(
                id = json.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                action = NexusAction(type = type, path = path, content = content, patch = patch)
            )
        }.getOrNull()
    }
}

data class NexusAction(
    val type: String,
    val path: String? = null,
    val content: String? = null,
    val patch: String? = null,
    val id: String = UUID.randomUUID().toString()
)

data class NexusActionProposal(
    val id: String,
    val action: NexusAction,
    val status: NexusActionStatus = NexusActionStatus.PROPOSED
)

enum class NexusActionStatus {
    PROPOSED,
    APPROVED,
    REJECTED,
    EXECUTING,
    COMPLETED,
    FAILED
}

object NexusActionPolicy {
    fun requiresApproval(action: NexusAction): Boolean = when (action.type) {
        "open_file", "focus_file", "read_file", "git_diff" -> false
        "patch_file", "replace_file", "create_file", "delete_file", "rename_file" -> true
        else -> true
    }
}
