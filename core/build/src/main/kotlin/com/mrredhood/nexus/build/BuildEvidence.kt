package com.mrredhood.nexus.build

import kotlinx.serialization.Serializable

@Serializable
enum class RemoteRunState {
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    FAILED,
    UNKNOWN,
}

@Serializable
data class RemoteRun(
    val schemaVersion: Int = 1,
    val runId: String,
    val workflow: String,
    val ref: String,
    val sourceCommitSha: String? = null,
    val dispatchKey: String,
    val state: RemoteRunState,
    val conclusion: String? = null,
)

@Serializable
data class ArtifactEvidence(
    val schemaVersion: Int = 1,
    val artifactId: String,
    val runId: String,
    val name: String,
    val downloadUrl: String? = null,
    val sha256: String? = null,
    val expiresAtEpochMillis: Long? = null,
)
