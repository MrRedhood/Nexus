package com.mrredhood.nexus.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mrredhood.nexus.core.model.NexusProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.nexusDataStore by preferencesDataStore("nexus")

class ProjectRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val projectsKey = stringPreferencesKey("projects")

    val projects: Flow<List<NexusProject>> = context.nexusDataStore.data.map { prefs ->
        prefs[projectsKey]?.let { json.decodeFromString<List<NexusProject>>(it) } ?: emptyList()
    }

    suspend fun save(project: NexusProject) {
        context.nexusDataStore.edit { prefs ->
            val current = prefs[projectsKey]?.let { json.decodeFromString<List<NexusProject>>(it) } ?: emptyList()
            val next = (current.filterNot { it.id == project.id } + project).sortedByDescending { it.updatedAt }
            prefs[projectsKey] = json.encodeToString(next)
        }
    }

    suspend fun delete(id: String) {
        context.nexusDataStore.edit { prefs ->
            val current = prefs[projectsKey]?.let { json.decodeFromString<List<NexusProject>>(it) } ?: emptyList()
            prefs[projectsKey] = json.encodeToString(current.filterNot { it.id == id })
        }
    }
}
