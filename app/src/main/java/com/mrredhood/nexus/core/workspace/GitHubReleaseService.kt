package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GitHubReleaseAsset(val id: Long, val name: String, val sizeBytes: Long, val contentType: String, val downloadUrl: String, val createdAt: String, val updatedAt: String)

data class GitHubRelease(val id: Long, val tagName: String, val targetCommitish: String, val name: String, val body: String, val draft: Boolean, val prerelease: Boolean, val createdAt: String, val publishedAt: String?, val htmlUrl: String, val assets: List<GitHubReleaseAsset>)

class GitHubReleaseService {
    suspend fun list(repository: String, token: String, includeDrafts: Boolean = true): List<GitHubRelease> = withContext(Dispatchers.IO) {
        val root = request("GET", "/repos/$repository/releases?per_page=100", token, null)
        val releases = (0 until root.length()).map { parseRelease(root.getJSONObject(it)) }
        if (includeDrafts) releases else releases.filterNot { it.draft }
    }

    suspend fun get(repository: String, releaseId: Long, token: String): GitHubRelease = withContext(Dispatchers.IO) {
        parseRelease(request("GET", "/repos/$repository/releases/$releaseId", token, null))
    }

    suspend fun create(repository: String, token: String, tagName: String, name: String, body: String, targetCommitish: String, draft: Boolean, prerelease: Boolean): GitHubRelease = withContext(Dispatchers.IO) {
        require(tagName.isNotBlank()) { "Tag name is required." }
        require(targetCommitish.isNotBlank()) { "Target commit or branch is required." }
        parseRelease(request("POST", "/repos/$repository/releases", token, JSONObject().apply {
            put("tag_name", tagName.trim())
            put("name", name.trim())
            put("body", body)
            put("target_commitish", targetCommitish.trim())
            put("draft", draft)
            put("prerelease", prerelease)
        }))
    }

    suspend fun update(repository: String, releaseId: Long, token: String, tagName: String, name: String, body: String, targetCommitish: String, draft: Boolean, prerelease: Boolean): GitHubRelease = withContext(Dispatchers.IO) {
        parseRelease(request("PATCH", "/repos/$repository/releases/$releaseId", token, JSONObject().apply {
            put("tag_name", tagName.trim())
            put("name", name.trim())
            put("body", body)
            put("target_commitish", targetCommitish.trim())
            put("draft", draft)
            put("prerelease", prerelease)
        }))
    }

    suspend fun publish(repository: String, releaseId: Long, token: String): GitHubRelease = withContext(Dispatchers.IO) {
        val release = get(repository, releaseId, token)
        update(repository, release.id, token, release.tagName, release.name, release.body, release.targetCommitish, false, release.prerelease)
    }

    suspend fun delete(repository: String, releaseId: Long, token: String) = withContext(Dispatchers.IO) {
        request("DELETE", "/repos/$repository/releases/$releaseId", token, null)
        Unit
    }

    suspend fun uploadAsset(repository: String, releaseId: Long, token: String, file: File, contentType: String): GitHubReleaseAsset = withContext(Dispatchers.IO) {
        require(file.isFile) { "Release asset file does not exist." }
        require(file.length() <= 2L * 1024L * 1024L * 1024L) { "GitHub release assets must be 2 GiB or smaller." }
        val name = URLEncoder.encode(file.name, Charsets.UTF_8.name()).replace("+", "%20")
        val connection = connection("https://uploads.github.com/repos/$repository/releases/$releaseId/assets?name=$name", "POST", token).apply {
            setRequestProperty("Content-Type", contentType.ifBlank { "application/octet-stream" })
            setFixedLengthStreamingMode(file.length())
            doOutput = true
        }
        try {
            file.inputStream().use { input -> connection.outputStream.use { output -> input.copyTo(output) } }
            return@withContext parseAsset(JSONObject(readResponse(connection)))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRelease(o: JSONObject): GitHubRelease {
        val assetsJson = o.optJSONArray("assets") ?: JSONArray()
        return GitHubRelease(
            id = o.getLong("id"), tagName = o.optString("tag_name"), targetCommitish = o.optString("target_commitish"),
            name = o.optString("name").ifBlank { o.optString("tag_name") }, body = o.optString("body"),
            draft = o.optBoolean("draft"), prerelease = o.optBoolean("prerelease"), createdAt = o.optString("created_at"),
            publishedAt = o.optString("published_at").ifBlank { null }, htmlUrl = o.optString("html_url"),
            assets = (0 until assetsJson.length()).map { parseAsset(assetsJson.getJSONObject(it)) }
        )
    }

    private fun parseAsset(o: JSONObject) = GitHubReleaseAsset(
        id = o.getLong("id"), name = o.optString("name"), sizeBytes = o.optLong("size", 0L),
        contentType = o.optString("content_type", "application/octet-stream"), downloadUrl = o.optString("browser_download_url"),
        createdAt = o.optString("created_at"), updatedAt = o.optString("updated_at")
    )

    private fun request(method: String, path: String, token: String, body: JSONObject?): JSONObject {
        val connection = connection("https://api.github.com$path", method, token).apply {
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        body?.toString()?.toByteArray(Charsets.UTF_8)?.let { bytes -> connection.outputStream.use { it.write(bytes) } }
        try {
            val response = readResponse(connection)
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun connection(url: String, method: String, token: String): HttpURLConnection {
        require(token.isNotBlank()) { "GitHub token is not configured. Add it in Settings > GitHub." }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Nexus-Android")
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
            error("GitHub Releases API $code${if (message.isNotBlank()) ": $message" else ""}")
        }
        return body
    }
}
