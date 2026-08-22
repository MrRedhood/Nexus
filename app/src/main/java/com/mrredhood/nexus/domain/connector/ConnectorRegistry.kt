package com.mrredhood.nexus.domain.connector

/** Central registry. UI and workflows depend on this abstraction, not connector-specific classes. */
class ConnectorRegistry {
    private val connectors = linkedMapOf<String, Connector>()

    fun register(connector: Connector): Result<Unit> {
        val id = connector.manifest.id.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Connector id cannot be empty"))
        if (connectors.containsKey(id)) {
            return Result.failure(IllegalStateException("Connector already registered: $id"))
        }
        connectors[id] = connector
        return Result.success(Unit)
    }

    fun unregister(id: String): Boolean = connectors.remove(id) != null

    fun get(id: String): Connector? = connectors[id]

    fun all(): List<Connector> = connectors.values.toList()

    fun findByAction(action: String): List<Connector> =
        connectors.values.filter { action in it.manifest.actions }

    fun findByTrigger(trigger: String): List<Connector> =
        connectors.values.filter { trigger in it.manifest.triggers }
}
