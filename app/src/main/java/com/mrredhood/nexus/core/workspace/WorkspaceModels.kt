package com.mrredhood.nexus.core.workspace

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
data class Workspace(
    val id: String,
    val projectId: String,
    val displayName: String,
    val treeUri: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun uri(): Uri = Uri.parse(treeUri)
}

enum class EntryType { FILE, DIRECTORY }

data class WorkspaceEntry(
    val relativePath: String,
    val name: String,
    val type: EntryType,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String?
)

data class WorkspaceFile(
    val relativePath: String,
    val name: String,
    val content: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String?
)
