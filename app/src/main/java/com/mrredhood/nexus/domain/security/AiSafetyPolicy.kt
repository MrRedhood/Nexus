package com.mrredhood.nexus.domain.security

import com.mrredhood.nexus.domain.permission.PermissionDecision
import com.mrredhood.nexus.domain.permission.PermissionRequest
import com.mrredhood.nexus.domain.permission.PermissionScope

/**
 * Conservative local policy. External content is never allowed to grant authority.
 * Consequential actions require explicit policy evaluation before connector execution.
 */
object AiSafetyPolicy {
    fun evaluate(request: PermissionRequest): PermissionDecision {
        if (request.scopes.contains(PermissionScope.FINANCIAL)) return PermissionDecision.ASK
        if (request.scopes.contains(PermissionScope.DELETE)) return PermissionDecision.ASK
        if (request.destructive) return PermissionDecision.ASK
        if (request.scopes.contains(PermissionScope.COMMUNICATE)) return PermissionDecision.ASK
        return PermissionDecision.ALLOW
    }
}
