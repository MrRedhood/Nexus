package com.mrredhood.nexus.domain.connector

import com.mrredhood.nexus.domain.permission.PermissionScope

data class ConnectorManifest(
    val id: String,
    val name: String,
    val version: String,
    val authentication: AuthenticationType,
    val permissions: Set<PermissionScope>,
    val triggers: Set<String>,
    val actions: Set<String>
)

enum class AuthenticationType { OAUTH2, API_KEY, LOCAL, NONE }

enum class ConnectorStatus { AVAILABLE, CONNECTED, NEEDS_REAUTH, ERROR, DISABLED }

data class ConnectorInstance(
    val manifest: ConnectorManifest,
    val status: ConnectorStatus,
    val accountLabel: String? = null,
    val lastSyncEpochMillis: Long? = null,
    val errorMessage: String? = null
)

interface Connector {
    val manifest: ConnectorManifest

    suspend fun connect(): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    suspend fun health(): ConnectorStatus
    suspend fun execute(action: String, input: Map<String, Any?>): Result<Map<String, Any?>>
}
