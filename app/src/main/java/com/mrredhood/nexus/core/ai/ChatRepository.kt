package com.mrredhood.nexus.core.ai

import android.content.Context
import com.mrredhood.nexus.core.settings.NexusSettingsRuntime
import org.json.JSONArray
import org.json.JSONObject

class ChatRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nexus_chat_history", Context.MODE_PRIVATE)

    fun load(workspaceId: String): List<ChatMessage> = runCatching {
        val array = JSONArray(prefs.getString(key(workspaceId), "[]"))
        buildList { for (i in 0 until array.length()) { val item = array.getJSONObject(i); add(ChatMessage(item.optString("role"), item.optString("content"), item.optLong("timestamp"))) } }
    }.getOrDefault(emptyList())

    fun save(workspaceId: String, messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.takeLast(100).forEach { message -> array.put(JSONObject().apply { put("role", message.role); put("content", message.content); put("timestamp", message.timestamp) }) }
        prefs.edit().putString(key(workspaceId), array.toString()).apply()
    }

    fun clear(workspaceId: String) { prefs.edit().remove(key(workspaceId)).apply() }
    private fun key(workspaceId: String) = "workspace.$workspaceId"
}

data class ChatMessage(val role: String, val content: String, val timestamp: Long = System.currentTimeMillis())

data class ChatContext(
    val currentFile: String? = null,
    val selection: String? = null,
    val gitDiff: String? = null,
    val terminalOutput: String? = null,
    val workspaceSummary: String? = null
)

class ChatContextBuilder {
    fun build(context: ChatContext): String = build(context, null)

    fun build(context: ChatContext, snapshot: AIContextSnapshot?): String {
        val settings = NexusSettingsRuntime.current()
        return buildString {
            append("You are Nexus, an Android-native AI engineering assistant. Be precise, inspect context before proposing changes, and never claim an action was performed unless it actually was.\n\n")
            append("When a software-engineering action is required, emit a structured action block instead of inventing prose commands. Use exactly this form: <nexus-action>{\"type\":\"open_file\",\"path\":\"app/src/main/...\"}</nexus-action>. Supported actions are open_file, focus_file, read_file, patch_file, and replace_file. Mutating actions must be treated as proposals and require user approval. Do not use action blocks for ordinary explanations.\n\n")
            if (snapshot != null) {
                append("BOUNDED AI CONTEXT:\n")
                append(snapshot.asPromptContext())
                append("\n")
            } else {
                if (settings.includeCurrentFile && !context.currentFile.isNullOrBlank()) append("CURRENT FILE:\n${context.currentFile}\n\n")
                if (settings.includeSelection && !context.selection.isNullOrBlank()) append("SELECTED CODE:\n${context.selection}\n\n")
                if (settings.includeGitDiff && !context.gitDiff.isNullOrBlank()) append("GIT DIFF:\n${context.gitDiff}\n\n")
                if (settings.includeTerminalContext && !context.terminalOutput.isNullOrBlank()) append("TERMINAL OUTPUT:\n${context.terminalOutput}\n\n")
                if (settings.includeWorkspaceSummary && !context.workspaceSummary.isNullOrBlank()) append("WORKSPACE SUMMARY:\n${context.workspaceSummary}\n\n")
            }
        }
    }
}
