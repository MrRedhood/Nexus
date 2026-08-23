package com.mrredhood.nexus.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mrredhood.nexus.core.workspace.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.workspaceDataStore by preferencesDataStore("workspace_state")

class WorkspaceRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val workspacesKey = stringPreferencesKey("workspaces")

    val workspaces: Flow<List<Workspace>> = context.workspaceDataStore.data.map { prefs ->
        prefs[workspacesKey]?.let { json.decodeFromString<List<Workspace>>(it) } ?: emptyList()
    }

    suspend fun save(workspace: Workspace) {
        context.workspaceDataStore.edit { prefs ->
            val current = decode(prefs[workspacesKey])
            val next = (current.filterNot { it.id == workspace.id } + workspace).sortedByDescending { it.updatedAt }
            prefs[workspacesKey] = json.encodeToString(next)
        }
    }

    suspend fun removeForProject(projectId: String) {
        context.workspaceDataStore.edit { prefs ->
            prefs[workspacesKey] = json.encodeToString(decode(prefs[workspacesKey]).filterNot { it.projectId == projectId })
        }
    }

    suspend fun getForProject(projectId: String): Workspace? =
        workspaces.firstValue().firstOrNull { it.projectId == projectId }

    fun takePersistablePermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }

    fun hasPermission(uri: Uri): Boolean = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }

    private fun decode(value: String?): List<Workspace> = value?.let { json.decodeFromString<List<Workspace>>(it) } ?: emptyList()
}

private suspend fun <T> Flow<T>.firstValue(): T = kotlinx.coroutines.flow.first()
