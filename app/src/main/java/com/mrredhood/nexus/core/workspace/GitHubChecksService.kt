package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Real GitHub Checks API integration. Uses the user's stored GitHub token and repository permissions. */
data class GitHubCheckRun(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val sha: String,
    val branch: String?,
    val htmlUrl: String?,
    val detailsUrl: String?,
    val startedAt: String?,
    val completedAt: String?,
    val summary: String?,
    val text: String?,
    val appName: String?
)

data class GitHubCheckAnnotation(
    val path: String,
    val startLine: Int?,
    val endLine: Int?,
    val annotationLevel: String?,
    val message: String?,
    val title: String?,
    val rawDetails: String?
)

class GitHubChecksService {
    suspend fun checkRuns(repository: String, ref: String, token: String, limit: Int = 100): List<GitHubCheckRun> = withContext(Dispatchers.IO) {
        require(repository.isNotBlank()) { "Connect a GitHub repository to this project first." }
        require(ref.isNotBlank()) { "Branch or commit is required." }
        val root = apiJson("GET", "/repos/$repository/commits/${encode(ref)}/check-runs?per_page=${limit.coerceIn(1, 100)}", token)
        val runs = root.optJSONArray("check_runs") ?: JSONArray()
        (0 until runs.length()).map { parseRun(runs.getJSONObject(it)) }
    }

    suspend fun annotations(repository: String, checkRunId: Long, token: String, limit: Int = 100): List<GitHubCheckAnnotation> = withContext(Dispatchers.IO) {
        val array = apiArray("GET", "/repos/$repository/check-runs/$checkRunId/annotations?per_page=${limit.coerceIn(1, 100)}", token)
        (0 until array.length()).map { parseAnnotation(array.getJSONObject(it)) }
    }

    private fun parseRun(o: JSONObject): GitHubCheckRun {
        val app = o.optJSONObject("app")
        return GitHubCheckRun(
            id = o.getLong("id"),
            name = o.optString("name", "Check"),
            status = o.optString("status", "unknown"),
            conclusion = o.optString("conclusion").ifBlank { null },
            sha = o.optString("head_sha"),
            branch = o.optString("check_suite", "").let { o.optString("head_branch").ifBlank { null } },
            htmlUrl = o.optString("html_url").ifBlank { null },
            detailsUrl = o.optString("details_url").ifBlank { null },
            startedAt = o.optString("started_at").ifBlank { null },
            completedAt = o.optString("completed_at").ifBlank { null },
            summary = o.optJSONObject("output")?.optString("summary").ifBlank { null },
            text = o.optJSONObject("output")?.optString("text").ifBlank { null },
            appName = app?.optString("name").ifBlank { null }
        )
    }

    private fun parseAnnotation(o: JSONObject) = GitHubCheckAnnotation(
        path = o.optString("path"),
        startLine = if (o.has("start_line") && !o.isNull("start_line")) o.optInt("start_line") else null,
        endLine = if (o.has("end_line") && !o.isNull("end_line")) o.optInt("end_line") else null,
        annotationLevel = o.optString("annotation_level").ifBlank { null },
        message = o.optString("message").ifBlank { null },
        title = o.optString("title").ifBlank { null },
        rawDetails = o.optString("raw_details").ifBlank { null }
    )

    private fun apiJson(method: String, path: String, token: String): JSONObject = JSONObject(apiRequest(method, path, token))
    private fun apiArray(method: String, path: String, token: String): JSONArray = JSONArray(apiRequest(method, path, token))

    private fun apiRequest(method: String, path: String, token: String): String {
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
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(response).optString("message") }.getOrNull().orEmpty()
            error("GitHub Checks API $status${if (message.isNotBlank()) ": $message" else ""}")
        }
        return response
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
