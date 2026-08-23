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

    fun relocate(workspaceId: String, oldPath: String, newPath: String) {
        val tabs = tabsByWorkspace[workspaceId] ?: return
        val index = tabs.indexOf(oldPath)
        if (index >= 0) {
            tabs[index] = newPath
            if (tabs.count { it == newPath } > 1) tabs.removeAt(index)
        }
        if (activeByWorkspace[workspaceId] == oldPath) activeByWorkspace[workspaceId] = newPath
        closedByWorkspace[workspaceId]?.let { closed ->
            val closedIndex = closed.indexOf(oldPath)
            if (closedIndex >= 0) { closed.removeAt(closedIndex); if (newPath !in closed) closed.addFirst(newPath) }
        }
        persist(workspaceId)
    }

    fun relocateTree(workspaceId: String, oldPrefix: String, newPrefix: String) {
        val tabs = tabsByWorkspace[workspaceId] ?: return
        for (i in tabs.indices) {
            val path = tabs[i]
            if (path == oldPrefix || path.startsWith("$oldPrefix/")) {
                val suffix = path.removePrefix(oldPrefix).trimStart('/')
                tabs[i] = if (suffix.isBlank()) newPrefix else "$newPrefix/$suffix"
            }
        }
        activeByWorkspace[workspaceId]?.let { active ->
            if (active == oldPrefix || active.startsWith("$oldPrefix/")) {
                val suffix = active.removePrefix(oldPrefix).trimStart('/')
                activeByWorkspace[workspaceId] = if (suffix.isBlank()) newPrefix else "$newPrefix/$suffix"
            }
        }
        persist(workspaceId)
    }

    fun remove(workspaceId: String, relativePath: String) {
        tabsByWorkspace[workspaceId]?.remove(relativePath)
        if (activeByWorkspace[workspaceId] == relativePath) activeByWorkspace[workspaceId] = tabsByWorkspace[workspaceId]?.lastOrNull().orEmpty()
        persist(workspaceId)
    }

    fun removeTree(workspaceId: String, prefix: String) {
        val tabs = tabsByWorkspace[workspaceId]
        tabs?.removeAll { it == prefix || it.startsWith("$prefix/") }
        activeByWorkspace[workspaceId]?.let { active ->
            if (active == prefix || active.startsWith("$prefix/")) activeByWorkspace[workspaceId] = tabs?.lastOrNull().orEmpty()
        }
        persist(workspaceId)
    }

    fun close(workspaceId: String, relativePath: String): String? {
        val tabs = tabsByWorkspace[workspaceId] ?: return null
        val index = tabs.indexOf(relativePath)
        if (index < 0) return activeByWorkspace[workspaceId]
        tabs.removeAt(index)
        addClosed(workspaceId, relativePath)
        if (activeByWorkspace[workspaceId] == relativePath) activeByWorkspace[workspaceId] = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex)) ?: tabs.lastOrNull().orEmpty()
        persist(workspaceId)
        return activeByWorkspace[workspaceId]?.ifBlank { null }
    }

    fun closeOthers(workspaceId: String, keepRelativePath: String) {
        val tabs = tabsByWorkspace.getOrPut(workspaceId) { mutableListOf() }
        tabs.filter { it != keepRelativePath }.asReversed().forEach { addClosed(workspaceId, it) }
        tabs.clear(); tabs += keepRelativePath; activeByWorkspace[workspaceId] = keepRelativePath; persist(workspaceId)
    }

    fun closeAll(workspaceId: String) {
        tabsByWorkspace[workspaceId].orEmpty().asReversed().forEach { addClosed(workspaceId, it) }
        tabsByWorkspace.remove(workspaceId); activeByWorkspace.remove(workspaceId); persist(workspaceId)
    }

    fun reopenLastClosed(workspaceId: String): String? {
        val closed = closedByWorkspace[workspaceId] ?: return null
        val path = if (closed.isEmpty()) null else closed.removeFirst() ?: return null
        val tabs = tabsByWorkspace.getOrPut(workspaceId) { mutableListOf() }
        if (path !in tabs) tabs += path
        activeByWorkspace[workspaceId] = path; persist(workspaceId); return path
    }

    fun tabs(workspaceId: String): List<String> = tabsByWorkspace[workspaceId].orEmpty().toList()
    fun active(workspaceId: String): String? = activeByWorkspace[workspaceId]?.ifBlank { null }

    fun clearWorkspace(workspaceId: String) {
        tabsByWorkspace.remove(workspaceId); closedByWorkspace.remove(workspaceId); activeByWorkspace.remove(workspaceId); preferences.edit().remove(key(workspaceId)).apply()
    }

    fun restore(workspaceId: String): List<String> {
        if (tabsByWorkspace.containsKey(workspaceId)) return tabs(workspaceId)
        val raw = preferences.getString(key(workspaceId), null) ?: return emptyList()
        return runCatching {
            val json = JSONObject(raw); val tabs = mutableListOf<String>(); val array = json.optJSONArray("tabs") ?: JSONArray()
            for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(tabs::add)
            tabsByWorkspace[workspaceId] = tabs; activeByWorkspace[workspaceId] = json.optString("active").ifBlank { tabs.lastOrNull().orEmpty() }; tabs
        }.getOrElse { emptyList() }
    }

    private fun addClosed(workspaceId: String, path: String) {
        closedByWorkspace.getOrPut(workspaceId) { ArrayDeque() }.apply { remove(path); addFirst(path); while (size > MAX_CLOSED_TABS) removeLast() }
    }
    private fun persist(workspaceId: String) {
        val json = JSONObject().apply { put("tabs", JSONArray(tabsByWorkspace[workspaceId].orEmpty())); put("active", activeByWorkspace[workspaceId].orEmpty()) }
        preferences.edit().putString(key(workspaceId), json.toString()).apply()
    }
    private fun key(workspaceId: String): String = "workspace_tabs_$workspaceId"
    companion object { private const val PREFERENCES = "nexus_editor_tabs"; private const val MAX_CLOSED_TABS = 20 }
}
