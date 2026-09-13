package com.mrredhood.nexus.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ActionRisk {
    READ_ONLY,
    MUTATING,
    DESTRUCTIVE,
    REMOTE,
    CREDENTIAL,
    RELEASE,
}

@Serializable
enum class PermissionMode {
    NEVER,
    SOME,
    AUTONOMOUS,
}

@Serializable
enum class ActionState {
    PROPOSED,
    AWAITING_APPROVAL,
    APPROVED,
    REJECTED,
    EXECUTING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

@Serializable
enum class ActionType {
    ReadFile,
    WriteFile,
    CreateFile,
    DeleteFile,
    MoveFile,
    PatchFile,
    SearchFiles,
    ListDirectory,
    Git,
    Build,
    ControlledTerminal,
}

@Serializable
data class ActionPrecondition(
    val path: String? = null,
    val expectedSha256: String? = null,
    val expectedRevision: String? = null,
)

@Serializable
data class WorkspaceAction(
    val schemaVersion: Int = 1,
    val actionId: String,
    val idempotencyKey: String,
    val type: ActionType,
    val risk: ActionRisk,
    val state: ActionState = ActionState.PROPOSED,
    val title: String,
    val targetPath: String? = null,
    val payload: String,
    val precondition: ActionPrecondition? = null,
    val snapshotId: String? = null,
)

@Serializable
data class ActionResult(
    val actionId: String,
    val state: ActionState,
    val message: String? = null,
    val evidenceIds: List<String> = emptyList(),
)
