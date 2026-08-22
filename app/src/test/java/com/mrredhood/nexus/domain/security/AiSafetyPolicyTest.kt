package com.mrredhood.nexus.domain.security

import com.mrredhood.nexus.domain.permission.PermissionDecision
import com.mrredhood.nexus.domain.permission.PermissionRequest
import com.mrredhood.nexus.domain.permission.PermissionScope
import org.junit.Assert.assertEquals
import org.junit.Test

class AiSafetyPolicyTest {
    @Test
    fun communicateRequiresApproval() {
        val request = PermissionRequest(
            connectorId = "gmail",
            capability = "send_email",
            scopes = setOf(PermissionScope.COMMUNICATE),
            reason = "Send a reply"
        )
        assertEquals(PermissionDecision.ASK, AiSafetyPolicy.evaluate(request))
    }

    @Test
    fun deleteRequiresApproval() {
        val request = PermissionRequest(
            connectorId = "drive",
            capability = "delete_file",
            scopes = setOf(PermissionScope.DELETE),
            reason = "Delete file"
        )
        assertEquals(PermissionDecision.ASK, AiSafetyPolicy.evaluate(request))
    }

    @Test
    fun readOnlyRequestCanBeAllowed() {
        val request = PermissionRequest(
            connectorId = "gmail",
            capability = "read_email",
            scopes = setOf(PermissionScope.READ),
            reason = "Read one message"
        )
        assertEquals(PermissionDecision.ALLOW, AiSafetyPolicy.evaluate(request))
    }
}
