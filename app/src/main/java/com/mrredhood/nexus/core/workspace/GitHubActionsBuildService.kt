package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** GitHub Actions transport used by Nexus cloud builds. */
data class BuildRun(
    val id: Long,
    val status: String,
    val conclusion: String?,
    val branch: String,
    val commitSha: String,
    val url: String,
    val createdAt: String,
    val updatedAt: String
) {
    val isFinished: Boolean get() = status == "completed"
    val isSuccessful: Boolean get() = conclusion == "success"
}

data class BuildArtifact(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val createdAt: String
)

class GitHubActionsBuildService {
    suspend fun dispatch(repository: String, token: String, branch: String, variant: String) = withContext(Dispatchers.IO) {
        require(repository.matches(Regex("[^/]+/[^/]+"))) { "GitHub repository must be owner/name." }
        require(branch.isNotBlank()) { "Branch is required." }
        require(variant == "debug" || variant == "release") { "Unsupported build variant." }
        request("POST", "/repos/$repository/actions/workflows/android-ci.yml/dispatches", token, JSONObject().apply {
            put("ref", branch)
            put("inputs", JSONObject().put("build_variant", variant))
        })
        Unit
    }

    suspend fun latestRuns(repository: String, token: String, branch: String = "main", limit: Int = 10): List<BuildRun> = withContext(Dispatchers.IO) {
        val query = "?branch=${encode(branch)}&per_page=${limit.coerceIn(1, 20)}"
        val json = request("GET", "/repos/$repository/actions/workflows/android-ci.yml/runs$query", token, null)
        val runs = json.optJSONArray("workflow_runs") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until runs.length()) {
                val item = runs.getJSONObject(i)
                add(item.toBuildRun())
            }
        }
    }

    suspend fun artifacts(repository: String, token: String, runId: Long): List<BuildArtifact> = withContext(Dispatchers.IO) {
        val json = request("GET", "/repos/$repository/actions/runs/$runId/artifacts?per_page=20", token, null)
        val artifacts = json.optJSONArray("artifacts") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until artifacts.length()) {
                val item = artifacts.getJSONObject(i)
                if (!item.optBoolean("expired", false)) {
                    add(BuildArtifact(
                        id = item.getLong("id"),
                        name = item.optString("name"),
                        sizeBytes = item.optLong("size_in_bytes"),
                        downloadUrl = item.optString("archive_download_url"),
                        createdAt = item.optString("created_at")
                    ))
                }
            }
        }
    }

    private fun JSONObject.toBuildRun() = BuildRun(
        id = getLong("id"),
        status = optString("status", "unknown"),
        conclusion = optString("conclusion").ifBlank { null },
        branch = optString("head_branch"),
        commitSha = optString("head_sha"),
        url = optString("html_url"),
        createdAt = optString("created_at"),
        updatedAt = optString("updated_at")
    )

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
        body?.toString()?.toByteArray(Charsets.UTF_8)?.let { bytes ->
            connection.outputStream.use { it.write(bytes) }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(response).optString("message") }.getOrNull().orEmpty()
            error("GitHub Actions API $status${if (message.isNotBlank()) ": $message" else ""}")
        }
        return if (response.isBlank()) JSONObject() else JSONObject(response)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
