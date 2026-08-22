package com.mrredhood.nexus.domain.permission

/** High-level authority classes used by connectors and the AI tool layer. */
enum class PermissionScope {
    READ,
    SEARCH,
    ANALYZE,
    CREATE,
    MODIFY,
    COMMUNICATE,
    DELETE,
    FINANCIAL,
    SENSITIVE
}

enum class PermissionGrant {
    ONCE,
    SESSION,
    TIME_LIMITED,
    PERSISTENT,
    NEVER
}

data class PermissionRequest(
    val connectorId: String,
    val capability: String,
    val scopes: Set<PermissionScope>,
    val reason: String,
    val destructive: Boolean = false
)

enum class PermissionDecision {
    ALLOW,
    ASK,
    DENY
}
