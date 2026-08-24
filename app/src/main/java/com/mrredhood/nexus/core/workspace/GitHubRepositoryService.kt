package com.mrredhood.nexus.core.workspace

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * GitHub-backed Git transport for Nexus.
 *
 * Nexus deliberately does not bundle a native Git executable. This service uses the
 * GitHub Git Database API to fetch a branch and create real Git commits from the
 * SAF-backed workspace. The caller supplies a personal access token with repository
 * contents/write permission.
 */
data class GitHubRemoteFile(val path: String, val sha: String, val size: Long = 0L)
data class GitHubSyncStatus(
    val branch: String,
    val remoteCommit: String,
    val changed: List<String>,
    val added: List<String>,
    val deleted: List<String>,
    val unchanged: Int
)

data class GitHubCommitResult(val commitSha: String, val treeSha: String, val message: String)

class GitHubRepositoryService(private val fileSystem: WorkspaceFileSystem) {
    suspend fun status(repository: String, branch: String, token: String, workspace: Workspace): GitHubSyncStatus = withContext(Dispatchers.IO) {
        val remote = loadRemoteTree(repository, branch, token)
        val local = collectLocalFiles(workspace)
        val changed = mutableListOf<String>()
        val added = mutableListOf<String>()
        local.forEach { (path, content) ->
            val remoteFile = remote.files[path]
            if (remoteFile == null) added += path
            else if (gitBlobSha(content) != remoteFile.sha) changed += path
        }
        val deleted = remote.files.keys.filter { it !in local.keys }.sorted()
        GitHubSyncStatus(branch, remote.commitSha, changed.sorted(), added.sorted(), deleted, local.size - changed.size - added.size)
    }

    suspend fun fetch(repository: String, branch: String, token: String, workspace: Workspace): Int = withContext(Dispatchers.IO) {
        val remote = loadRemoteTree(repository, branch, token)
        var count = 0
        remote.files.keys.sorted().forEach { path ->
            val content = remote.contents[path] ?: return@forEach
            if (path.startsWith(".git/")) return@forEach
            fileSystem.write(workspace, path, content, mimeTypeFor(path))
            count++
        }
        count
    }

    suspend fun commitAndPush(
        repository: String,
        branch: String,
        token: String,
        workspace: Workspace,
        message: String
    ): GitHubCommitResult = withContext(Dispatchers.IO) {
        require(message.isNotBlank()) { "Commit message is required" }
        val remote = loadRemoteTree(repository, branch, token)
        val local = collectLocalFiles(workspace)
        val entries = mutableListOf<Map<String, Any?>>()

        local.forEach { (path, content) ->
            if (!path.startsWith(".git/")) {
                entries += mapOf("path" to path, "mode" to "100644", "type" to "blob", "content" to content)
            }
        }
        remote.files.keys.filter { it !in local.keys }.forEach { deleted ->
            entries += mapOf("path" to deleted, "mode" to "100644", "type" to "blob", "sha" to null)
        }

        val tree = apiPost("/repos/$repository/git/trees", token, JSONObject().apply {
            put("base_tree", remote.treeSha)
            put("tree", JSONArray(entries.map { JSONObject(it) }))
        })
        val treeSha = tree.getString("sha")
        val commit = apiPost("/repos/$repository/git/commits", token, JSONObject().apply {
            put("message", message.trim())
            put("tree", treeSha)
            put("parents", JSONArray().put(remote.commitSha))
        })
        val commitSha = commit.getString("sha")
        apiPatch("/repos/$repository/git/refs/heads/${encodePath(branch)}", token, JSONObject().put("sha", commitSha))
        GitHubCommitResult(commitSha, treeSha, message.trim())
    }

    private suspend fun loadRemoteTree(repository: String, branch: String, token: String): RemoteTree {
        val ref = apiGet("/repos/$repository/git/ref/heads/${encodePath(branch)}", token)
        val commitSha = ref.getJSONObject("object").getString("sha")
        val commit = apiGet("/repos/$repository/git/commits/$commitSha", token)
        val treeSha = commit.getJSONObject("tree").getString("sha")
        val tree = apiGet("/repos/$repository/git/trees/$treeSha?recursive=1", token)
        val files = linkedMapOf<String, GitHubRemoteFile>()
        val contents = linkedMapOf<String, String>()
        val treeItems = tree.optJSONArray("tree") ?: JSONArray()
        for (i in 0 until treeItems.length()) {
            val item = treeItems.getJSONObject(i)
            if (item.optString("type") != "blob") continue
            val path = item.getString("path")
            if (path.startsWith(".git/")) continue
            val sha = item.getString("sha")
            val size = item.optLong("size", 0L)
            files[path] = GitHubRemoteFile(path, sha, size)
            if (size <= WorkspaceFileSystem.MAX_EDITABLE_FILE_BYTES) {
                val blob = apiGet("/repos/$repository/git/blobs/$sha", token)
                if (blob.optString("encoding") == "base64") {
                    val decoded = Base64.decode(blob.getString("content").replace("\n", ""), Base64.DEFAULT)
                    contents[path] = decoded.toString(Charsets.UTF_8)
                }
            }
        }
        return RemoteTree(commitSha, treeSha, files, contents)
    }

    private suspend fun collectLocalFiles(workspace: Workspace): Map<String, String> {
        val result = linkedMapOf<String, String>()
        suspend fun visit(directory: String) {
            fileSystem.list(workspace, directory).forEach { entry ->
                if (entry.name == ".git") return@forEach
                if (entry.type == EntryType.DIRECTORY) visit(entry.relativePath)
                else if (entry.sizeBytes <= WorkspaceFileSystem.MAX_EDITABLE_FILE_BYTES) {
                    runCatching { fileSystem.read(workspace, entry.relativePath).content }.onSuccess { result[entry.relativePath] = it }
                }
            }
        }
        visit("")
        return result
    }

    private fun gitBlobSha(content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val header = "blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-1").digest(header + bytes).joinToString("") { "%02x".format(it) }
    }

    private fun apiGet(path: String, token: String): JSONObject = request("GET", path, token, null)
    private fun apiPost(path: String, token: String, body: JSONObject): JSONObject = request("POST", path, token, body)
    private fun apiPatch(path: String, token: String, body: JSONObject): JSONObject = request("PATCH", path, token, body)

    private fun request(method: String, path: String, token: String, body: JSONObject?): JSONObject {
        require(token.isNotBlank()) { "GitHub token is not configured. Add it in Settings > GitHub." }
        val connection = (URL("https://api.github.com$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Nexus-Android")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        body?.toString()?.toByteArray(Charsets.UTF_8)?.let { connection.outputStream.use { stream -> stream.write(it) } }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(response).optString("message") }.getOrNull().orEmpty()
            error("GitHub API $status${if (message.isNotBlank()) ": $message" else ""}")
        }
        return if (response.isBlank()) JSONObject() else JSONObject(response)
    }

    private fun encodePath(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "json" -> "application/json"; "xml" -> "application/xml"; "html", "htm" -> "text/html"; "css" -> "text/css"; "js", "ts" -> "text/javascript"; "kt", "kts", "java", "c", "cpp", "h", "hpp", "py", "rb", "go", "rs", "swift", "dart", "cs", "php", "sh" -> "text/plain"; else -> "text/plain"
    }

    private data class RemoteTree(val commitSha: String, val treeSha: String, val files: Map<String, GitHubRemoteFile>, val contents: Map<String, String>)
}
