package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Real GitHub Copilot cloud-agent client. Authentication is the user's stored GitHub token. */
data class CopilotAgentTask(
    val id: String,
    val name: String,
    val state: String,
    val htmlUrl: String?,
    val createdAt: String,
    val updatedAt: String,
    val pullRequestNumber: Int? = null,
    val sessions: List<CopilotAgentSession> = emptyList()
)

data class CopilotAgentSession(
    val id: String,
    val state: String,
    val prompt: String,
    val headRef: String?,
    val baseRef: String?,
    val model: String?,
    val createdAt: String,
    val updatedAt: String,
    val completedAt: String?
)

class GitHubCopilotAgentService {
    suspend fun startTask(
        repository: String,
        token: String,
        prompt: String,
        baseRef: String,
        headRef: String? = null,
        model: String? = null,
        createPullRequest: Boolean = false
    ): CopilotAgentTask = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "Prompt is required." }
        require(repository.matches(Regex("[^/\\s]+/[^/\\s]+"))) { "Invalid GitHub repository." }
        val body = JSONObject().apply {
            put("prompt", prompt.trim())
            put("base_ref", baseRef.ifBlank { "main" })
            headRef?.takeIf { it.isNotBlank() }?.let { put("head_ref", it) }
            model?.takeIf { it.isNotBlank() && it != AUTO_MODEL }?.let { put("model", it) }
            put("create_pull_request", createPullRequest)
        }
        parseTask(apiRequest("POST", "/agents/repos/$repository/tasks", token, body), repository)
    }

    suspend fun listTasks(repository: String, token: String): List<CopilotAgentTask> = withContext(Dispatchers.IO) {
        val root = JSONObject(apiRequest("GET", "/agents/repos/$repository/tasks?per_page=100&sort=updated_at&direction=desc", token))
        val tasks = root.optJSONArray("tasks") ?: JSONArray()
        (0 until tasks.length()).map { parseTask(tasks.getJSONObject(it), repository) }
    }

    suspend fun getTask(repository: String, token: String, taskId: String): CopilotAgentTask = withContext(Dispatchers.IO) {
        parseTask(apiRequest("GET", "/agents/repos/$repository/tasks/${encode(taskId)}", token), repository)
    }

    private fun parseTask(jsonText: String, repository: String): CopilotAgentTask =
        parseTask(JSONObject(jsonText), repository)

    private fun parseTask(o: JSONObject, repository: String): CopilotAgentTask {
        val artifacts = o.optJSONArray("artifacts") ?: JSONArray()
        var pullRequest: Int? = null
        for (i in 0 until artifacts.length()) {
            val artifact = artifacts.optJSONObject(i) ?: continue
            if (artifact.optString("type") == "pull") {
                pullRequest = artifact.optJSONObject("data")?.optInt("id")?.takeIf { it > 0 }
            }
        }
        val sessionsJson = o.optJSONArray("sessions") ?: JSONArray()
        val sessions = (0 until sessionsJson.length()).map { i ->
            val s = sessionsJson.getJSONObject(i)
            CopilotAgentSession(
                id = s.optString("id"),
                state = s.optString("state"),
                prompt = s.optString("prompt", o.optString("name")),
                headRef = s.optString("head_ref").ifBlank { null },
                baseRef = s.optString("base_ref").ifBlank { null },
                model = s.optString("model").ifBlank { null },
                createdAt = s.optString("created_at"),
                updatedAt = s.optString("updated_at"),
                completedAt = s.optString("completed_at").ifBlank { null }
            )
        }
        return CopilotAgentTask(
            id = o.optString("id"),
            name = o.optString("name", "Copilot task"),
            state = o.optString("state", "unknown"),
            htmlUrl = o.optString("html_url").ifBlank { null },
            createdAt = o.optString("created_at"),
            updatedAt = o.optString("updated_at"),
            pullRequestNumber = pullRequest,
            sessions = sessions
        )
    }

    private fun apiRequest(method: String, path: String, token: String, body: JSONObject? = null): String {
        require(token.isNotBlank()) { "GitHub token is not configured. Add it in Settings > GitHub." }
        val connection = (URL("https://api.github.com$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            setRequestProperty("User-Agent", "Nexus-Android")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("message") }.getOrNull().orEmpty()
                throw IllegalStateException(
                    when {
                        status == 403 && message.isNotBlank() -> "GitHub denied Copilot access: $message"
                        status == 403 -> "GitHub denied Copilot access. The token needs Agent tasks read/write permission and the account needs an eligible Copilot plan."
                        status == 404 -> "Copilot cloud-agent endpoint or repository was not found. Check the repository and Copilot availability."
                        status == 422 && message.isNotBlank() -> "GitHub rejected the Copilot task: $message"
                        message.isNotBlank() -> "GitHub API error ($status): $message"
                        else -> "GitHub API error ($status)."
                    }
                )
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    companion object {
        const val AUTO_MODEL = "Auto"
        val SUPPORTED_MODELS = listOf(
            AUTO_MODEL,
            "gpt-5.4",
            "gpt-5.3-codex",
            "gpt-5.2-codex",
            "claude-sonnet-4.6",
            "claude-opus-4.6",
            "claude-sonnet-4.5",
            "claude-opus-4.5"
        )
    }
}
