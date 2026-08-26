package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder


data class GitHubRepository(val fullName: String, val defaultBranch: String, val private: Boolean, val description: String?)
data class GitHubWorkflow(val id: Long, val name: String, val path: String, val state: String, val url: String?)
data class GitHubWorkflowRun(val id: Long, val name: String, val status: String, val conclusion: String?, val branch: String, val sha: String, val createdAt: String, val updatedAt: String, val htmlUrl: String?)

data class GitHubRemoteFile(val path: String, val sha: String, val size: Long = 0L)
data class GitHubSyncStatus(val branch: String, val remoteCommit: String, val changed: List<String>, val added: List<String>, val deleted: List<String>, val unchanged: Int)
data class GitHubCommitResult(val commitSha: String, val treeSha: String, val message: String)
data class GitBranch(val name: String, val sha: String, val current: Boolean)
data class GitCommit(val sha: String, val message: String, val author: String, val timestamp: String, val filesChanged: Int)
data class GitDiff(val path: String, val before: String, val after: String, val addedLines: Int, val removedLines: Int)
data class GitHubArtifact(val id: Long, val name: String, val sizeBytes: Long, val expired: Boolean, val createdAt: String, val expiresAt: String?, val downloadUrl: String?)

class GitHubRepositoryService(private val fileSystem: WorkspaceFileSystem) {
    suspend fun repositories(token: String, query: String? = null, limit: Int = 50): List<GitHubRepository> = withContext(Dispatchers.IO) {
        val path = if (query.isNullOrBlank()) "/user/repos?sort=updated&per_page=${limit.coerceIn(1, 100)}" else "/search/repositories?q=${encode(query)}&per_page=${limit.coerceIn(1, 100)}"
        val root = apiJson("GET", path, token)
        val items = if (root.has("items")) root.getJSONArray("items") else root.getJSONArray("items")
        (0 until items.length()).map { parseRepository(items.getJSONObject(it)) }
    }

    suspend fun repository(repository: String, token: String): GitHubRepository = withContext(Dispatchers.IO) {
        parseRepository(apiJson("GET", "/repos/$repository", token))
    }

    suspend fun branches(repository: String, current: String, token: String): List<GitBranch> = withContext(Dispatchers.IO) {
        val items = apiArray("GET", "/repos/$repository/branches?per_page=100", token)
        (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            GitBranch(o.getString("name"), o.getJSONObject("commit").getString("sha"), o.getString("name") == current)
        }.sortedBy { it.name }
    }

    suspend fun createBranch(repository: String, name: String, fromSha: String, token: String) = withContext(Dispatchers.IO) {
        require(name.matches(Regex("[A-Za-z0-9._/-]+"))) { "Invalid branch name" }
        apiJson("POST", "/repos/$repository/git/refs", token, JSONObject().apply {
            put("ref", "refs/heads/$name")
            put("sha", fromSha)
        })
    }

    suspend fun checkout(repository: String, branch: String, token: String): String = withContext(Dispatchers.IO) {
        apiJson("GET", "/repos/$repository/git/ref/heads/${encode(branch)}", token).getJSONObject("object").getString("sha")
    }

    suspend fun workflows(repository: String, token: String): List<GitHubWorkflow> = withContext(Dispatchers.IO) {
        val items = apiJson("GET", "/repos/$repository/actions/workflows?per_page=100", token).getJSONArray("workflows")
        (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            GitHubWorkflow(o.getLong("id"), o.getString("name"), o.getString("path"), o.getString("state"), o.optString("html_url").ifBlank { null })
        }
    }

    suspend fun workflowRuns(repository: String, token: String, workflowId: Long? = null, branch: String? = null, limit: Int = 30): List<GitHubWorkflowRun> = withContext(Dispatchers.IO) {
        val path = buildString {
            append("/repos/$repository/actions/")
            if (workflowId != null) append("workflows/$workflowId/")
            append("runs?per_page=${limit.coerceIn(1, 100)}")
            if (!branch.isNullOrBlank()) append("&branch=${encode(branch)}")
        }
        val items = apiJson("GET", path, token).getJSONArray("workflow_runs")
        (0 until items.length()).map { i -> parseRun(items.getJSONObject(i)) }
    }

    suspend fun dispatchWorkflow(repository: String, workflowId: Long, token: String, branch: String = "main", inputs: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("ref", branch)
            if (inputs.isNotEmpty()) put("inputs", JSONObject(inputs))
        }
        apiRequest("POST", "/repos/$repository/actions/workflows/$workflowId/dispatches", token, body)
    }

    suspend fun workflowRun(repository: String, runId: Long, token: String): GitHubWorkflowRun = withContext(Dispatchers.IO) {
        parseRun(apiJson("GET", "/repos/$repository/actions/runs/$runId", token))
    }

    suspend fun artifacts(repository: String, token: String, runId: Long? = null, limit: Int = 50): List<GitHubArtifact> = withContext(Dispatchers.IO) {
        val path = if (runId == null) "/repos/$repository/actions/artifacts?per_page=${limit.coerceIn(1, 100)}" else "/repos/$repository/actions/runs/$runId/artifacts?per_page=${limit.coerceIn(1, 100)}"
        val items = apiJson("GET", path, token).getJSONArray("artifacts")
        (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            GitHubArtifact(o.getLong("id"), o.getString("name"), o.optLong("size_in_bytes"), o.optBoolean("expired"), o.optString("created_at"), o.optString("expires_at").ifBlank { null }, o.optString("archive_download_url").ifBlank { null })
        }
    }

    private fun parseRepository(o: JSONObject) = GitHubRepository(o.getString("full_name"), o.optString("default_branch", "main"), o.optBoolean("private", false), o.optString("description").ifBlank { null })

    private fun parseRun(o: JSONObject) = GitHubWorkflowRun(o.getLong("id"), o.optString("name", "Workflow"), o.optString("status"), o.optString("conclusion").ifBlank { null }, o.optString("head_branch"), o.optString("head_sha"), o.optString("created_at"), o.optString("updated_at"), o.optString("html_url").ifBlank { null })

    private fun apiJson(method: String, path: String, token: String, body: JSONObject? = null): JSONObject = JSONObject(apiRequest(method, path, token, body))

    private fun apiArray(method: String, path: String, token: String): JSONArray = JSONArray(apiRequest(method, path, token, null))

    private fun apiRequest(method: String, path: String, token: String, body: JSONObject? = null): String {
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
        body?.toString()?.toByteArray(Charsets.UTF_8)?.let { bytes -> connection.outputStream.use { it.write(bytes) } }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(response).optString("message") }.getOrNull().orEmpty()
            error("GitHub API $status${if (message.isNotBlank()) ": $message" else ""}")
        }
        return response
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
