package com.mrredhood.nexus.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nexusFeatureSettingsDataStore by preferencesDataStore(name = "nexus_feature_settings")

data class NexusFeatureSettings(
    val provider: String = "Gemini",
    val model: String = "default",
    val endpoint: String = "",
    val apiKeyConfigured: Boolean = false,
    val githubAutoSync: Boolean = true,
    val githubFetchBranches: Boolean = true,
    val githubDefaultBranch: String = "main",
    val memoryEnabled: Boolean = true,
    val memoryRetentionDays: Int = 90,
    val notificationsEnabled: Boolean = true,
    val notificationSound: Boolean = false,
    val ciNotifications: Boolean = true,
    val cacheLimitMb: Int = 256,
    val clearCacheOnExit: Boolean = false,
    val pluginsEnabled: Boolean = true,
    val crashReports: Boolean = true,
    val analytics: Boolean = false,
    val autoCheckUpdates: Boolean = true
)

class AdvancedSettingsRepository(private val context: Context) {
    private object Keys {
        val provider = stringPreferencesKey("provider.name")
        val model = stringPreferencesKey("provider.model")
        val endpoint = stringPreferencesKey("provider.endpoint")
        val apiKeyConfigured = booleanPreferencesKey("provider.api_key_configured")
        val githubAutoSync = booleanPreferencesKey("github.auto_sync")
        val githubFetchBranches = booleanPreferencesKey("github.fetch_branches")
        val githubDefaultBranch = stringPreferencesKey("github.default_branch")
        val memoryEnabled = booleanPreferencesKey("memory.enabled")
        val memoryRetentionDays = intPreferencesKey("memory.retention_days")
        val notificationsEnabled = booleanPreferencesKey("notifications.enabled")
        val notificationSound = booleanPreferencesKey("notifications.sound")
        val ciNotifications = booleanPreferencesKey("notifications.ci")
        val cacheLimitMb = intPreferencesKey("storage.cache_limit_mb")
        val clearCacheOnExit = booleanPreferencesKey("storage.clear_on_exit")
        val pluginsEnabled = booleanPreferencesKey("plugins.enabled")
        val crashReports = booleanPreferencesKey("privacy.crash_reports")
        val analytics = booleanPreferencesKey("privacy.analytics")
        val autoCheckUpdates = booleanPreferencesKey("updates.auto_check")
    }

    val settings: Flow<NexusFeatureSettings> = context.nexusFeatureSettingsDataStore.data.map { p ->
        NexusFeatureSettings(
            provider = p[Keys.provider] ?: "Gemini",
            model = p[Keys.model] ?: "default",
            endpoint = p[Keys.endpoint] ?: "",
            apiKeyConfigured = p[Keys.apiKeyConfigured] ?: false,
            githubAutoSync = p[Keys.githubAutoSync] ?: true,
            githubFetchBranches = p[Keys.githubFetchBranches] ?: true,
            githubDefaultBranch = p[Keys.githubDefaultBranch] ?: "main",
            memoryEnabled = p[Keys.memoryEnabled] ?: true,
            memoryRetentionDays = p[Keys.memoryRetentionDays] ?: 90,
            notificationsEnabled = p[Keys.notificationsEnabled] ?: true,
            notificationSound = p[Keys.notificationSound] ?: false,
            ciNotifications = p[Keys.ciNotifications] ?: true,
            cacheLimitMb = p[Keys.cacheLimitMb] ?: 256,
            clearCacheOnExit = p[Keys.clearCacheOnExit] ?: false,
            pluginsEnabled = p[Keys.pluginsEnabled] ?: true,
            crashReports = p[Keys.crashReports] ?: true,
            analytics = p[Keys.analytics] ?: false,
            autoCheckUpdates = p[Keys.autoCheckUpdates] ?: true
        )
    }

    suspend fun update(transform: (NexusFeatureSettings) -> NexusFeatureSettings) {
        context.nexusFeatureSettingsDataStore.edit { p ->
            val current = NexusFeatureSettings(
                provider = p[Keys.provider] ?: "Gemini", model = p[Keys.model] ?: "default", endpoint = p[Keys.endpoint] ?: "",
                apiKeyConfigured = p[Keys.apiKeyConfigured] ?: false, githubAutoSync = p[Keys.githubAutoSync] ?: true,
                githubFetchBranches = p[Keys.githubFetchBranches] ?: true, githubDefaultBranch = p[Keys.githubDefaultBranch] ?: "main",
                memoryEnabled = p[Keys.memoryEnabled] ?: true, memoryRetentionDays = p[Keys.memoryRetentionDays] ?: 90,
                notificationsEnabled = p[Keys.notificationsEnabled] ?: true, notificationSound = p[Keys.notificationSound] ?: false,
                ciNotifications = p[Keys.ciNotifications] ?: true, cacheLimitMb = p[Keys.cacheLimitMb] ?: 256,
                clearCacheOnExit = p[Keys.clearCacheOnExit] ?: false, pluginsEnabled = p[Keys.pluginsEnabled] ?: true,
                crashReports = p[Keys.crashReports] ?: true, analytics = p[Keys.analytics] ?: false, autoCheckUpdates = p[Keys.autoCheckUpdates] ?: true
            )
            val s = transform(current)
            p[Keys.provider] = s.provider; p[Keys.model] = s.model; p[Keys.endpoint] = s.endpoint; p[Keys.apiKeyConfigured] = s.apiKeyConfigured
            p[Keys.githubAutoSync] = s.githubAutoSync; p[Keys.githubFetchBranches] = s.githubFetchBranches; p[Keys.githubDefaultBranch] = s.githubDefaultBranch
            p[Keys.memoryEnabled] = s.memoryEnabled; p[Keys.memoryRetentionDays] = s.memoryRetentionDays
            p[Keys.notificationsEnabled] = s.notificationsEnabled; p[Keys.notificationSound] = s.notificationSound; p[Keys.ciNotifications] = s.ciNotifications
            p[Keys.cacheLimitMb] = s.cacheLimitMb; p[Keys.clearCacheOnExit] = s.clearCacheOnExit; p[Keys.pluginsEnabled] = s.pluginsEnabled
            p[Keys.crashReports] = s.crashReports; p[Keys.analytics] = s.analytics; p[Keys.autoCheckUpdates] = s.autoCheckUpdates
        }
    }
}
