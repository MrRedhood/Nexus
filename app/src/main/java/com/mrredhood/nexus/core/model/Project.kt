package com.mrredhood.nexus.core.model

import kotlinx.serialization.Serializable

@Serializable
data class NexusProject(
    val id: String,
    val name: String,
    val repository: String? = null,
    val branch: String = "main",
    val workspaceId: String? = null,
    val remoteUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
