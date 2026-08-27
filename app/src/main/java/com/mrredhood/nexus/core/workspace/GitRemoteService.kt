package com.mrredhood.nexus.core.workspace

import android.content.Context
import com.mrredhood.nexus.core.settings.GitCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.SshdSessionFactory
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.nio.charset.StandardCharsets

/** Native Git transport. It does not require a GitHub API token or a system git executable. */
class GitRemoteService(context: Context) {
    private val appContext = context.applicationContext
    private val credentials = GitCredentialStore(appContext)
    private val root = File(appContext.filesDir, "git-remotes")

    suspend fun connect(remoteUrl: String, workspace: Workspace, projectId: String, branch: String? = null): GitRemoteResult = withContext(Dispatchers.IO) {
        val url = normalizeUrl(remoteUrl); val repoDir = repoDir(projectId); repoDir.parentFile?.mkdirs()
        if (repoDir.exists() && File(repoDir, ".git").exists()) {
            Git.open(repoDir).use { git -> if (!hasRemote(git)) git.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(url)).call() }
        } else {
            if (repoDir.exists()) repoDir.deleteRecursively()
            val command = Git.cloneRepository().setURI(url).setDirectory(repoDir).setCloneAllBranches(true)
            configure(command, url); branch?.takeIf { it.isNotBlank() }?.let { command.setBranch(it) }; command.call().use { }
        }
        syncRepoToWorkspace(repoDir, workspace)
        Git.open(repoDir).use { git -> GitRemoteResult(git.repository.config.getString("remote", "origin", "url") ?: url, git.repository.fullBranch?.removePrefix("refs/heads/") ?: branch ?: "main", "Connected and fetched repository.") }
    }

    suspend fun pull(remoteUrl: String, workspace: Workspace, projectId: String, branch: String): GitRemoteResult = withContext(Dispatchers.IO) {
        val url = normalizeUrl(remoteUrl); ensureRepository(url, projectId, branch); val repoDir = repoDir(projectId)
        Git.open(repoDir).use { git -> configure(git); checkoutBranch(git, branch); val result = git.pull().setRemote("origin").setRemoteBranchName(branch).call(); if (!result.isSuccessful) throw IllegalStateException("Git pull did not complete cleanly.") }
        syncRepoToWorkspace(repoDir, workspace); GitRemoteResult(url, branch, "Pulled $branch from remote.")
    }

    suspend fun workingTreeDiff(projectId: String, workspace: Workspace): List<GitDiff> = withContext(Dispatchers.IO) {
        val repoDir = repoDir(projectId); require(File(repoDir, ".git").exists()) { "Connect a repository first." }; val fs = WorkspaceFileSystem(appContext); val files = collectWorkspaceFiles(fs, workspace)
        Git.open(repoDir).use { git ->
            val status = git.status().call(); val paths = (status.added + status.changed + status.removed + status.missing + status.modified + status.untracked + status.conflicting).toSortedSet()
            paths.map { path -> val afterBytes = files[path].orEmpty(); val after = runCatching { afterBytes.toString(StandardCharsets.UTF_8) }.getOrDefault(""); val added = if (after.isBlank()) 0 else after.lines().size; val removed = if (path in status.missing || path in status.removed) 1 else 0; GitDiff(path, "", after, added, removed) }
        }
    }

    suspend fun commitAndPush(remoteUrl: String, workspace: Workspace, projectId: String, branch: String, message: String): GitRemoteResult = withContext(Dispatchers.IO) {
        require(message.isNotBlank()) { "Commit message is required." }; val url = normalizeUrl(remoteUrl); ensureRepository(url, projectId, branch); val repoDir = repoDir(projectId); syncWorkspaceToRepo(repoDir, workspace)
        Git.open(repoDir).use { git ->
            configure(git); checkoutBranch(git, branch); git.add().addFilepattern(".").call(); git.add().setUpdate(true).addFilepattern(".").call(); val status = git.status().call()
            if (status.isClean) return@use GitRemoteResult(url, branch, "Nothing to commit.")
            val commit = git.commit().setMessage(message.trim()).setAll(true).call(); val push = git.push().setRemote("origin").add(branch).call()
            if (push.any { it.remoteUpdates.any { update -> update.status.isRejected } }) throw IllegalStateException("Git push was rejected by the remote. Pull first and resolve any divergence.")
            GitRemoteResult(url, branch, "Committed ${commit.name.take(7)} and pushed to $branch.")
        }
    }

    suspend fun fetch(remoteUrl: String, projectId: String, branch: String): GitRemoteResult = withContext(Dispatchers.IO) {
        val url = normalizeUrl(remoteUrl); ensureRepository(url, projectId, branch); Git.open(repoDir(projectId)).use { git -> configure(git); git.fetch().setRemote("origin").call() }; GitRemoteResult(url, branch, "Fetched remote refs.")
    }

    suspend fun resetLocal(projectId: String, branch: String) = withContext(Dispatchers.IO) {
        val dir = repoDir(projectId); require(File(dir, ".git").exists()) { "No native Git checkout exists for this project." }; Git.open(dir).use { git -> checkoutBranch(git, branch); git.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/$branch").call() }
    }

    private fun ensureRepository(url: String, projectId: String, branch: String) {
        val dir = repoDir(projectId); if (!File(dir, ".git").exists()) { dir.deleteRecursively(); dir.parentFile?.mkdirs(); val command = Git.cloneRepository().setURI(url).setDirectory(dir).setCloneAllBranches(true).setBranch(branch); configure(command, url); command.call().use { } }
    }

    private fun checkoutBranch(git: Git, branch: String) {
        if (git.repository.findRef(branch) == null) { if (git.repository.findRef("refs/remotes/origin/$branch") != null) git.checkout().setCreateBranch(true).setName(branch).setStartPoint("origin/$branch").call() else git.checkout().setCreateBranch(true).setName(branch).call() } else if (git.repository.fullBranch != "refs/heads/$branch") git.checkout().setName(branch).call()
    }

    private fun hasRemote(git: Git): Boolean = git.repository.config.getString("remote", "origin", "url")?.isNotBlank() == true
    private fun configure(command: org.eclipse.jgit.api.TransportCommand<*, *>, url: String) { if (url.startsWith("http://") || url.startsWith("https://")) { val user = credentials.httpsUsername().orEmpty(); val password = credentials.httpsPassword().orEmpty(); if (user.isNotBlank() || password.isNotBlank()) command.setCredentialsProvider(UsernamePasswordCredentialsProvider(user, password)) }; if (url.startsWith("ssh://") || url.startsWith("git@")) configureSsh() }
    private fun configure(git: Git) { val url = git.repository.config.getString("remote", "origin", "url").orEmpty(); if (url.startsWith("ssh://") || url.startsWith("git@")) configureSsh() }

    private fun configureSsh() {
        val key = credentials.sshPrivateKey()?.trim().orEmpty(); require(key.isNotBlank()) { "SSH remote selected, but no SSH private key is configured in Nexus Git credentials." }
        val sshDir = File(appContext.filesDir, "git-ssh").apply { mkdirs() }; val keyFile = File(sshDir, "id_ed25519"); keyFile.writeText(key, StandardCharsets.UTF_8); keyFile.setReadable(false, false); keyFile.setReadable(true, true); keyFile.setWritable(true, true)
        val knownHosts = File(sshDir, "known_hosts"); knownHosts.writeText(credentials.knownHosts()?.trim().takeUnless { it.isNullOrBlank() } ?: GITHUB_ED25519_HOST_KEY, StandardCharsets.UTF_8)
        val factory = SshdSessionFactory(); factory.setHomeDirectory(appContext.filesDir); factory.setSshDirectory(sshDir); SshSessionFactory.setInstance(factory)
    }

    private suspend fun syncRepoToWorkspace(repoDir: File, workspace: Workspace) {
        val fs = WorkspaceFileSystem(appContext); val repoFiles = linkedSetOf<String>(); repoDir.walkTopDown().forEach { file -> if (!file.isFile || file.path.contains("${File.separator}.git${File.separator}")) return@forEach; val relative = file.relativeTo(repoDir).invariantSeparatorsPath; if (file.length() <= WorkspaceFileSystem.MAX_EDITABLE_FILE_BYTES) repoFiles += relative }
        val workspaceFiles = collectWorkspaceFiles(fs, workspace); for (path in workspaceFiles.keys - repoFiles) runCatching { fs.delete(workspace, path) }; for (path in repoFiles) { val file = File(repoDir, path); fs.writeBytes(workspace, path, file.readBytes(), mimeFor(path)) }
    }

    private suspend fun syncWorkspaceToRepo(repoDir: File, workspace: Workspace) {
        val fs = WorkspaceFileSystem(appContext); val files = collectWorkspaceFiles(fs, workspace); repoDir.walkTopDown().filter { it.isFile && !it.path.contains("${File.separator}.git${File.separator}") }.forEach { file -> if (file.relativeTo(repoDir).invariantSeparatorsPath !in files.keys) file.delete() }; for ((path, bytes) in files) { val target = File(repoDir, path); target.parentFile?.mkdirs(); target.writeBytes(bytes) }
    }

    private suspend fun collectWorkspaceFiles(fs: WorkspaceFileSystem, workspace: Workspace): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>(); suspend fun visit(directory: String) { fs.list(workspace, directory).forEach { entry -> if (entry.name == ".git") return@forEach; if (entry.type == EntryType.DIRECTORY) visit(entry.relativePath) else if (entry.sizeBytes <= WorkspaceFileSystem.MAX_EDITABLE_FILE_BYTES) runCatching { fs.readBytes(workspace, entry.relativePath) }.onSuccess { result[entry.relativePath] = it } } }; visit(""); return result
    }

    private fun repoDir(projectId: String): File = File(root, projectId.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    private fun normalizeUrl(value: String): String { val url = value.trim(); require(url.isNotBlank()) { "Repository URL is required." }; require(url.startsWith("https://") || url.startsWith("http://") || url.startsWith("ssh://") || url.matches(Regex("git@[^:]+:.+"))) { "Unsupported repository URL. Use HTTPS or SSH." }; return url }
    private fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) { "kt" -> "text/x-kotlin"; "java" -> "text/x-java"; "xml" -> "application/xml"; "json" -> "application/json"; "md" -> "text/markdown"; "txt", "gradle", "kts", "properties", "yml", "yaml", "css", "js", "ts" -> "text/plain"; else -> "application/octet-stream" }
    data class GitRemoteResult(val remoteUrl: String, val branch: String, val message: String)
    companion object { private const val GITHUB_ED25519_HOST_KEY = "github.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl" }
}
