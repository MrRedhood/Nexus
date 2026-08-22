package com.mrredhood.nexus.domain.connector

/** UI-safe state for a connector instance. Secrets/tokens are intentionally excluded. */
data class ConnectorState(
    val manifest: ConnectorManifest,
    val status: ConnectorStatus = ConnectorStatus.AVAILABLE,
    val accountLabel: String? = null,
    val lastSyncEpochMillis: Long? = null,
    val errorMessage: String? = null
)

class ConnectorStateStore(initial: List<ConnectorManifest> = ConnectorCatalog.official) {
    private val values = initial.associateBy { it.id }
        .mapValues { (_, manifest) -> ConnectorState(manifest) }
        .toMutableMap()

    fun all(): List<ConnectorState> = values.values.toList()

    fun get(id: String): ConnectorState? = values[id]

    fun connect(id: String, accountLabel: String): ConnectorState? = update(id) {
        it.copy(status = ConnectorStatus.CONNECTED, accountLabel = accountLabel, errorMessage = null)
    }

    fun disconnect(id: String): ConnectorState? = update(id) {
        it.copy(status = ConnectorStatus.AVAILABLE, accountLabel = null, errorMessage = null)
    }

    fun markNeedsReauth(id: String): ConnectorState? = update(id) {
        it.copy(status = ConnectorStatus.NEEDS_REAUTH)
    }

    fun markError(id: String, message: String): ConnectorState? = update(id) {
        it.copy(status = ConnectorStatus.ERROR, errorMessage = message.take(240))
    }

    fun updateSync(id: String, epochMillis: Long): ConnectorState? = update(id) {
        it.copy(status = ConnectorStatus.CONNECTED, lastSyncEpochMillis = epochMillis, errorMessage = null)
    }

    private fun update(id: String, transform: (ConnectorState) -> ConnectorState): ConnectorState? {
        val current = values[id] ?: return null
        return transform(current).also { values[id] = it }
    }
}
