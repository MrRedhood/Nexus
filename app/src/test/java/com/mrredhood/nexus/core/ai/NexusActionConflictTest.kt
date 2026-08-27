package com.mrredhood.nexus.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusActionConflictTest {
    @Test
    fun protocol_roundTrip_preservesExpectedHash() {
        val action = NexusAction(
            type = "patch_file",
            path = "app/src/main/Main.kt",
            patch = "@@ -1 +1 @@\n-old\n+new",
            expectedHash = "abc123"
        )

        val parsed = NexusActionProtocol.extract(
            "<nexus-action>${NexusActionProtocol.encode(action)}</nexus-action>"
        ).single()

        assertEquals("abc123", parsed.action.expectedHash)
        assertEquals("patch_file", parsed.action.type)
        assertEquals("app/src/main/Main.kt", parsed.action.path)
    }

    @Test
    fun policyStillTreatsConflictCheckedActionsAsMutating() {
        val action = NexusAction(
            type = "replace_file",
            path = "src/Main.kt",
            content = "new",
            expectedHash = "hash"
        )

        assertTrue(NexusActionPolicy.isMutating(action))
    }
}
