package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GitHubIssue(
    val number: Int,
    val title: String,
    val body: String,
    val state: String,
    val author: String,
    val labels: List<String>,
    val assignees: List<String>,
    val comments: Int,
    val createdAt: String,
    val updatedAt: String,
    val closedAt: String?,
    val htmlUrl: String?
)

data class GitHubIssueComment(
    val id: Long,
    val author: String,
    val body: String,
    val createdAt: String,
    val updatedAt: String
)

class GitHubIssueService {
    suspend fun list(repository: String, token: String, state: String = "open", page: Int = 1, limit: Int = 25): List<GitHubIssue> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val safeLimit = limit.coerceIn(1, 100)
        val items = apiArray("GET", "/repos/$repository/issues?state=${encode(state)}&page=$safePage&per_page=$safeLimit", token)
        (0 until items.length()).mapNotNull { i ->
            val item = items.getJSONObject(i)
            if (item.has("pull_request")) null else parseIssue(item)
        }
    }

    suspend fun get(repository: String, number: Int, token: String): GitHubIssue = withContext(Dispatchers.IO) {
        parseIssue(apiJson("GET", "/repos/$repository/issues/$number", token))
    }

    suspend fun comments(repository: String, number: Int, token: String, page: Int = 1, limit: Int = 50): List<GitHubIssueComment> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val safeLimit = limit.coerceIn(1, 100)
        val items = apiArray("GET", "/repos/$repository/issues/$number/comments?page=$safePage&per_page=$safeLimit", token)
        (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            val user = o.optJSONObject("user")
            GitHubIssueComment(
                id = o.getLong("id"),
                author = user?.optString("login", "Unknown") ?: "Unknown",
                body = o.optString("body"),
                createdAt = o.optString("created_at"),
                updatedAt = o.optString("updated_at")
            )
        }
    }

    suspend fun create(repository: String, title: String, body: String, token: String, labels: List<String> = emptyList(), assignees: List<String> = emptyList()): GitHubIssue = withContext(Dispatchers.IO) {
        require(title.isNotBlank()) { "Issue title is required" }
        val payload = JSONObject().apply {
            put("title", title.trim())
            put("body", body)
            if (labels.isNotEmpty()) put("labels", JSONArray(labels))
            if (assignees.isNotEmpty()) put("assignees", JSONArray(assignees))
        }
        parseIssue(apiJson("POST", "/repos/$repository/issues", token, payload))
    }

    suspend fun update(repository: String, number: Int, token: String, title: String? = null, body: String? = null, state: String? = null, labels: List<String>? = null, assignees: List<String>? = null): GitHubIssue = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            title?.let { put("title", it.trim()) }
            body?.let { put("body", it) }
            state?.let { put("state", it) }
            labels?.let { put("labels", JSONArray(it)) }
            assignees?.let { put("assignees", JSONArray(it)) }
        }
        parseIssue(apiJson("PATCH", "/repos/$repository/issues/$number", token, payload))
    }

    suspend fun addComment(repository: String, number: Int, body: String, token: String): GitHubIssueComment = withContext(Dispatchers.IO) {
        require(body.isNotBlank()) { "Comment cannot be empty" }
        val o = apiJson("POST", "/repos/$repository/issues/$number/comments", token, JSONObject().put("body", body.trim()))
        val user = o.optJSONObject("user")
        GitHubIssueComment(o.getLong("id"), user?.optString("login", "Unknown") ?: "Unknown", o.optString("body"), o.optString("created_at"), o.optString("updated_at"))
    }

    suspend fun updateComment(repository: String, commentId: Long, body: String, token: String): GitHubIssueComment = withContext(Dispatchers.IO) {
        require(body.isNotBlank()) { "Comment cannot be empty" }
        val o = apiJson("PATCH", "/repos/$repository/issues/comments/$commentId", token, JSONObject().put("body", body.trim()))
        val user = o.optJSONObject("user")
        GitHubIssueComment(o.getLong("id"), user?.optString("login", "Unknown") ?: "Unknown", o.optString("body"), o.optString("created_at"), o.optString("updated_at"))
    }

    suspend fun deleteComment(repository: String, commentId: Long, token: String) = withContext(Dispatchers.IO) {
        apiRequest("DELETE", "/repos/$repository/issues/comments/$commentId", token, null)
    }

    private fun parseIssue(o: JSONObject): GitHubIssue {
        val user = o.optJSONObject("user")
        val labels = o.optJSONArray("labels")?.let { array -> (0 until array.length()).map { array.getJSONObject(it).optString("name") }.filter { it.isNotBlank() } } ?: emptyList()
        val assignees = o.optJSONArray("assignees")?.let { array -> (0 until array.length()).map { array.getJSONObject(it).optString("login") }.filter { it.isNotBlank() } } ?: emptyList()
        return GitHubIssue(
            number = o.getInt("number"), title = o.optString("title"), body = o.optString("body"), state = o.optString("state"),
            author = user?.optString("login", "Unknown") ?: "Unknown", labels = labels, assignees = assignees,
            comments = o.optInt("comments", 0), createdAt = o.optString("created_at"), updatedAt = o.optString("updated_at"),
            closedAt = o.optString("closed_at").ifBlank { null }, htmlUrl = o.optString("html_url").ifBlank { null }
        )
    }

    private fun apiJson(method: String, path: String, token: String, body: JSONObject? = null): JSONObject = JSONObject(apiRequest(method, path, token, body))
    private fun apiArray(method: String, path: String, token: String): JSONArray = JSONArray(apiRequest(method, path, token, null))

    private fun apiRequest(method: String, path: String, token: String, body: JSONObject?): String {
        require(token.isNotBlank()) { "GitHub token is not configured. Add it in Settings > GitHub." }
        val connection = (URL("https://api.github.com$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Nexus-Android")
        }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(text).optString("message") }.getOrDefault(text)
            throw IllegalStateException("GitHub API $code: ${message.ifBlank { "Request failed" }}")
        }
        return text
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
