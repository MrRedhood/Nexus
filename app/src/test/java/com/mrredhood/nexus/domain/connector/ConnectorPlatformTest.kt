package com.mrredhood.nexus.domain.connector

import com.mrredhood.nexus.domain.permission.PermissionScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorPlatformTest {
    @Test
    fun officialCatalogContainsCoreConnectors() {
        val ids = ConnectorCatalog.official.map { it.id }.toSet()
        assertTrue("google.gmail" in ids)
        assertTrue("google.calendar" in ids)
        assertTrue("google.drive" in ids)
        assertTrue("github" in ids)
        assertTrue("android.files" in ids)
    }

    @Test
    fun registryRejectsDuplicateConnectorIds() {
        val registry = ConnectorRegistry()
        val connector = FakeConnector(ConnectorCatalog.official.first())
        assertTrue(registry.register(connector).isSuccess)
        assertFalse(registry.register(connector).isSuccess)
        assertEquals(1, registry.all().size)
    }

    @Test
    fun stateStoreTransitionsWithoutExposingSecrets() {
        val store = ConnectorStateStore()
        val connected = store.connect("google.gmail", "demo@example.com")
        assertNotNull(connected)
        assertEquals(ConnectorStatus.CONNECTED, connected?.status)
        assertEquals("demo@example.com", connected?.accountLabel)
        assertTrue(connected?.manifest?.permissions?.contains(PermissionScope.COMMUNICATE) == true)

        val disconnected = store.disconnect("google.gmail")
        assertEquals(ConnectorStatus.AVAILABLE, disconnected?.status)
        assertEquals(null, disconnected?.accountLabel)
    }

    private class FakeConnector(override val manifest: ConnectorManifest) : Connector {
        override suspend fun connect(): Result<Unit> = Result.success(Unit)
        override suspend fun disconnect(): Result<Unit> = Result.success(Unit)
        override suspend fun health(): ConnectorStatus = ConnectorStatus.AVAILABLE
        override suspend fun execute(action: String, input: Map<String, Any?>): Result<Map<String, Any?>> =
            Result.success(emptyMap())
    }
}
