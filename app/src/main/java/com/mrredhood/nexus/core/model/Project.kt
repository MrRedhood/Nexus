package com.mrredhood.nexus.core.model

import kotlinx.serialization.Serializable

@Serializable
data class NexusProject(
    val id: String,
    val name: String,
    val repository: String? = null,
    val branch: String = "main",
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class WorkspaceFile(
    val path: String,
    val language: String,
    val content: String = ""
)
