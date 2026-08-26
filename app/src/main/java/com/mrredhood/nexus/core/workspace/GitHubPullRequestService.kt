package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GitHubPullRequest(
    val number: Int,
    val title: String,
    val body: String,
    val state: String,
    val draft: Boolean,
    val head: String,
    val base: String,
    val headSha: String,
    val author: String,
    val createdAt: String,
    val updatedAt: String,
    val merged: Boolean,
    val additions: Int,
    val deletions: Int,
    val changedFiles: Int,
    val htmlUrl: String?
)

data class GitHubPullRequestFile(
    val path: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String?
)

class GitHubPullRequestService {
    suspend fun list(repository: String, token: String, state: String = "open", limit: Int = 50): List<GitHubPullRequest> = withContext(Dispatchers.IO) {
        val items = apiArray("GET", "/repos/$repository/pulls?state=${encode(state)}&per_page=${limit.coerceIn(1, 100)}", token)
        (0 until items.length()).map { parse(items.getJSONObject(it)) }
    }

    suspend fun get(repository: String, number: Int, token: String): GitHubPullRequest = withContext(Dispatchers.IO) {
        parse(apiJson("GET", "/repos/$repository/pulls/$number", token))
    }

    suspend fun files(repository: String, number: Int, token: String, limit: Int = 100): List<GitHubPullRequestFile> = withContext(Dispatchers.IO) {
        val items = apiArray("GET", "/repos/$repository/pulls/$number/files?per_page=${limit.coerceIn(1, 100)}", token)
        (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            GitHubPullRequestFile(o.getString("filename"), o.optString("status"), o.optInt("additions"), o.optInt("deletions"), o.optInt("changes"), o.optString("patch").ifBlank { null })
        }
    }

    suspend fun create(repository: String, head: String, base: String, title: String, body: String, token: String, draft: Boolean = false): GitHubPullRequest = withContext(Dispatchers.IO) {
        require(head.isNotBlank() && base.isNotBlank()) { "Head and base branches are required" }
        require(title.isNotBlank()) { "Pull request title is required" }
        parse(apiJson("POST", "/repos/$repository/pulls", token, JSONObject().apply {
            put("title", title.trim())
            put("head", head.trim())
            put("base", base.trim())
            put("body", body)
            put("draft", draft)
        }))
    }

    suspend fun update(repository: String, number: Int, token: String, title: String? = null, body: String? = null, state: String? = null, base: String? = null): GitHubPullRequest = withContext(Dispatchers.IO) {
        parse(apiJson("PATCH", "/repos/$repository/pulls/$number", token, JSONObject().apply {
            title?.let { put("title", it) }
            body?.let { put("body", it) }
            state?.let { put("state", it) }
            base?.let { put("base", it) }
        }))
    }

    suspend fun merge(repository: String, number: Int, token: String, method: String = "squash"): Boolean = withContext(Dispatchers.IO) {
        val result = apiJson("PUT", "/repos/$repository/pulls/$number/merge", token, JSONObject().put("merge_method", method))
        result.optBoolean("merged", false)
    }

    private fun parse(o: JSONObject): GitHubPullRequest {
        val head = o.optJSONObject("head")
        val base = o.optJSONObject("base")
        val user = o.optJSONObject("user")
        return GitHubPullRequest(
            number = o.getInt("number"),
            title = o.optString("title"),
            body = o.optString("body"),
            state = o.optString("state"),
            draft = o.optBoolean("draft", false),
            head = head?.optString("ref", "") ?: "",
            base = base?.optString("ref", "") ?: "",
            headSha = head?.optString("sha", "") ?: "",
            author = user?.optString("login", "Unknown") ?: "Unknown",
            createdAt = o.optString("created_at"),
            updatedAt = o.optString("updated_at"),
            merged = o.optBoolean("merged", false),
            additions = o.optInt("additions", 0),
            deletions = o.optInt("deletions", 0),
            changedFiles = o.optInt("changed_files", 0),
            htmlUrl = o.optString("html_url").ifBlank { null }
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
