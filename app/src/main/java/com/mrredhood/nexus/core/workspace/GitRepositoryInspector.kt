package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only Git repository awareness for SAF-backed Nexus workspaces. */
data class GitRepositoryInfo(
    val isRepository: Boolean,
    val branch: String? = null,
    val headCommit: String? = null,
    val remoteName: String? = null,
    val remoteUrl: String? = null,
    val detachedHead: Boolean = false
)

object GitTextParser {
    fun parseHead(head: String): Pair<String?, String?> {
        val value = head.trim()
        if (value.startsWith("ref: ")) {
            val ref = value.removePrefix("ref: ").trim()
            val branch = ref.removePrefix("refs/heads/").takeIf { it.isNotBlank() && it != ref || ref.startsWith("refs/heads/") }
            return branch to null
        }
        return null to value.takeIf { it.matches(Regex("[0-9a-fA-F]{7,64}")) }
    }

    fun parseRemoteConfig(config: String, preferredRemote: String = "origin"): Pair<String?, String?> {
        val lines = config.replace("\r\n", "\n").split('\n')
        var currentRemote: String? = null
        val remotes = linkedMapOf<String, String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("[remote \"") && line.endsWith("\"]")) {
                currentRemote = line.substringAfter("[remote \"").removeSuffix("\"]").trim()
                continue
            }
            if (currentRemote != null && line.startsWith("url =")) {
                val url = line.substringAfter('=').trim()
                if (url.isNotBlank()) remotes.putIfAbsent(currentRemote!!, url)
            }
        }
        val name = remotes.keys.firstOrNull { it == preferredRemote } ?: remotes.keys.firstOrNull()
        return name to name?.let { remotes[it] }
    }
}

class GitRepositoryInspector(private val fileSystem: WorkspaceFileSystem) {
    suspend fun inspect(workspace: Workspace): GitRepositoryInfo = withContext(Dispatchers.IO) {
        if (!fileSystem.exists(workspace, ".git")) return@withContext GitRepositoryInfo(false)

        val head = runCatching { fileSystem.read(workspace, ".git/HEAD").content }.getOrNull().orEmpty()
        val (branch, detachedCommit) = GitTextParser.parseHead(head)
        val config = runCatching { fileSystem.read(workspace, ".git/config").content }.getOrNull().orEmpty()
        val (remoteName, remoteUrl) = GitTextParser.parseRemoteConfig(config)

        GitRepositoryInfo(
            isRepository = true,
            branch = branch,
            headCommit = detachedCommit,
            remoteName = remoteName,
            remoteUrl = remoteUrl,
            detachedHead = branch == null && detachedCommit != null
        )
    }
}
