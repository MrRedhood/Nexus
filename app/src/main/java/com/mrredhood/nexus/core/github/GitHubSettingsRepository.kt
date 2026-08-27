package com.mrredhood.nexus.core.github

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nexusGitHubSettingsDataStore by preferencesDataStore(name = "nexus_github_settings")

data class GitHubSettings(
    val autoSync: Boolean = true,
    val fetchBranches: Boolean = true,
    val defaultBranch: String = "main"
)

/** GitHub-only settings. It intentionally has no dependency on AI provider configuration or API keys. */
class GitHubSettingsRepository(private val context: Context) {
    private object Keys {
        val autoSync = booleanPreferencesKey("sync.auto")
        val fetchBranches = booleanPreferencesKey("branches.fetch")
        val defaultBranch = stringPreferencesKey("branch.default")
    }

    val settings: Flow<GitHubSettings> = context.nexusGitHubSettingsDataStore.data.map { p ->
        GitHubSettings(
            autoSync = p[Keys.autoSync] ?: true,
            fetchBranches = p[Keys.fetchBranches] ?: true,
            defaultBranch = p[Keys.defaultBranch] ?: "main"
        )
    }

    suspend fun update(transform: (GitHubSettings) -> GitHubSettings) {
        context.nexusGitHubSettingsDataStore.edit { p ->
            val current = GitHubSettings(
                autoSync = p[Keys.autoSync] ?: true,
                fetchBranches = p[Keys.fetchBranches] ?: true,
                defaultBranch = p[Keys.defaultBranch] ?: "main"
            )
            val next = transform(current)
            p[Keys.autoSync] = next.autoSync
            p[Keys.fetchBranches] = next.fetchBranches
            p[Keys.defaultBranch] = next.defaultBranch.trim().ifBlank { "main" }
        }
    }
}
