package com.mrredhood.nexus.core.editor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Owns tab order, closed-tab history and lightweight tab persistence. */
class EditorTabManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val tabsByWorkspace = LinkedHashMap<String, MutableList<String>>()
    private val closedByWorkspace = LinkedHashMap<String, ArrayDeque<String>>()
    private val activeByWorkspace = LinkedHashMap<String, String>()

    fun open(workspaceId: String, relativePath: String) {
        val tabs = tabsByWorkspace.getOrPut(workspaceId) { mutableListOf() }
        if (relativePath !in tabs) tabs += relativePath
        activeByWorkspace[workspaceId] = relativePath
        persist(workspaceId)
    }

    fun activate(workspaceId: String, relativePath: String): Boolean {
        if (relativePath !in tabsByWorkspace.getOrPut(workspaceId) { mutableListOf() }) return false
        activeByWorkspace[workspaceId] = relativePath
        persist(workspaceId)
        return true
    }

    fun close(workspaceId: String, relativePath: String): String? {
        val tabs = tabsByWorkspace[workspaceId] ?: return null
        val index = tabs.indexOf(relativePath)
        if (index < 0) return activeByWorkspace[workspaceId]
        tabs.removeAt(index)
        addClosed(workspaceId, relativePath)
        if (activeByWorkspace[workspaceId] == relativePath) {
            activeByWorkspace[workspaceId] = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex)) ?: tabs.lastOrNull().orEmpty()
        }
        persist(workspaceId)
        return activeByWorkspace[workspaceId]?.ifBlank { null }
    }

    fun closeOthers(workspaceId: String, keepRelativePath: String) {
        val tabs = tabsByWorkspace.getOrPut(workspaceId) { mutableListOf() }
        tabs.filter { it != keepRelativePath }.asReversed().forEach { addClosed(workspaceId, it) }
        tabs.clear()
        tabs += keepRelativePath
        activeByWorkspace[workspaceId] = keepRelativePath
        persist(workspaceId)
    }

    fun closeAll(workspaceId: String) {
        tabsByWorkspace[workspaceId].orEmpty().asReversed().forEach { addClosed(workspaceId, it) }
        tabsByWorkspace.remove(workspaceId)
        activeByWorkspace.remove(workspaceId)
        persist(workspaceId)
    }

    fun reopenLastClosed(workspaceId: String): String? {
        val closed = closedByWorkspace[workspaceId] ?: return null
        val path = if (closed.isEmpty()) null else closed.removeFirst()
        path ?: return null
        val tabs = tabsByWorkspace.getOrPut(workspaceId) { mutableListOf() }
        if (path !in tabs) tabs += path
        activeByWorkspace[workspaceId] = path
        persist(workspaceId)
        return path
    }

    fun tabs(workspaceId: String): List<String> = tabsByWorkspace[workspaceId].orEmpty().toList()
    fun active(workspaceId: String): String? = activeByWorkspace[workspaceId]?.ifBlank { null }

    fun clearWorkspace(workspaceId: String) {
        tabsByWorkspace.remove(workspaceId)
        closedByWorkspace.remove(workspaceId)
        activeByWorkspace.remove(workspaceId)
        preferences.edit().remove(key(workspaceId)).apply()
    }

    fun restore(workspaceId: String): List<String> {
        if (tabsByWorkspace.containsKey(workspaceId)) return tabs(workspaceId)
        val raw = preferences.getString(key(workspaceId), null) ?: return emptyList()
        return runCatching {
            val json = JSONObject(raw)
            val tabs = mutableListOf<String>()
            val array = json.optJSONArray("tabs") ?: JSONArray()
            for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(tabs::add)
            tabsByWorkspace[workspaceId] = tabs
            activeByWorkspace[workspaceId] = json.optString("active").ifBlank { tabs.lastOrNull().orEmpty() }
            tabs
        }.getOrElse { emptyList() }
    }

    private fun addClosed(workspaceId: String, path: String) {
        closedByWorkspace.getOrPut(workspaceId) { ArrayDeque() }.apply {
            remove(path)
            addFirst(path)
            while (size > MAX_CLOSED_TABS) removeLast()
        }
    }

    private fun persist(workspaceId: String) {
        val json = JSONObject().apply {
            put("tabs", JSONArray(tabsByWorkspace[workspaceId].orEmpty()))
            put("active", activeByWorkspace[workspaceId].orEmpty())
        }
        preferences.edit().putString(key(workspaceId), json.toString()).apply()
    }

    private fun key(workspaceId: String): String = "workspace_tabs_$workspaceId"

    companion object {
        private const val PREFERENCES = "nexus_editor_tabs"
        private const val MAX_CLOSED_TABS = 20
    }
}
