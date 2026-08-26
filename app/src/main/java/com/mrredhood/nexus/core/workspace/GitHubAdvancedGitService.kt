package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Real Git operations that are exposed by GitHub's Git/REST APIs. */
class GitHubAdvancedGitService {
    suspend fun mergeBranches(repository: String, base: String, head: String, token: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("base", base).put("head", head)
        val response = request("POST", "/repos/$repository/merges", token, body)
        JSONObject(response).optString("sha").ifBlank { response }
    }

    suspend fun resetBranch(repository: String, branch: String, targetSha: String, token: String, force: Boolean = false) = withContext(Dispatchers.IO) {
        require(targetSha.matches(Regex("[0-9a-fA-F]{7,64}"))) { "Enter a valid commit SHA." }
        request("PATCH", "/repos/$repository/git/refs/heads/${encode(branch)}", token, JSONObject().put("sha", targetSha).put("force", force))
        Unit
    }

    suspend fun cherryPick(repository: String, commitSha: String, branch: String, token: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        require(commitSha.matches(Regex("[0-9a-fA-F]{7,64}"))) { "Enter a valid commit SHA." }
        val response = request("POST", "/repos/$repository/commits/${encode(commitSha)}/cherry-pick", token, JSONObject().put("mainline", 0).put("branch", branch))
        val json = JSONObject(response)
        val status = json.optString("status")
        val sha = json.optString("sha")
        (status == "completed" || (status.isBlank() && sha.isNotBlank())) to (sha.ifBlank { response })
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun request(method: String, path: String, token: String, body: JSONObject? = null): String {
        require(token.isNotBlank()) { "GitHub token is not configured." }
        val connection = (URL("https://api.github.com$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Nexus-Android")
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8") }
        }
        body?.toString()?.toByteArray(Charsets.UTF_8)?.let { bytes -> connection.outputStream.use { it.write(bytes) } }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(response).optString("message") }.getOrDefault(response)
            throw IllegalStateException("GitHub $status: ${message.ifBlank { "operation failed" }}")
        }
        return response
    }
}
