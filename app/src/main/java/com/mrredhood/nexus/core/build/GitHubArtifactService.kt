package com.mrredhood.nexus.core.build

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/** Reads GitHub Actions APK artifacts and materializes an APK locally for installation. */
class GitHubArtifactService(private val context: Context) {
    data class Artifact(
        val id: Long,
        val name: String,
        val sizeBytes: Long,
        val createdAt: String,
        val expiresAt: String?,
        val expired: Boolean,
        val runId: Long,
        val commitSha: String,
        val branch: String,
        val htmlUrl: String
    ) {
        val variant: String
            get() = if (name.contains("release", ignoreCase = true)) "release" else "debug"
    }

    data class InstalledApk(val file: File, val uri: Uri)

    fun listApkArtifacts(repository: String, token: String): List<Artifact> {
        require(repository.isNotBlank()) { "GitHub repository is not configured." }
        require(token.isNotBlank()) { "Add a GitHub token in Settings > GitHub first." }
        val json = get("https://api.github.com/repos/$repository/actions/artifacts?per_page=50", token)
        val artifacts = JSONArray(json).let { root ->
            (0 until root.length()).mapNotNull { index ->
                val item = root.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name")
                if (!name.startsWith("nexus-debug-apk-") && !name.startsWith("nexus-release-apk-")) return@mapNotNull null
                val run = item.optJSONObject("workflow_run")
                Artifact(
                    id = item.optLong("id"),
                    name = name,
                    sizeBytes = item.optLong("size_in_bytes"),
                    createdAt = item.optString("created_at"),
                    expiresAt = item.optString("expires_at").ifBlank { null },
                    expired = item.optBoolean("expired", false),
                    runId = run?.optLong("id") ?: 0L,
                    commitSha = run?.optString("head_sha").orEmpty(),
                    branch = run?.optString("head_branch").orEmpty(),
                    htmlUrl = run?.optString("html_url").orEmpty()
                )
            }
        }
        return artifacts.sortedByDescending { parseDate(it.createdAt) }
    }

    fun downloadAndExtract(repository: String, token: String, artifact: Artifact): InstalledApk {
        val dir = File(context.filesDir, "build-artifacts/${artifact.id}").apply { mkdirs() }
        val archive = File(dir, "${artifact.id}.zip")
        download("https://api.github.com/repos/$repository/actions/artifacts/${artifact.id}/zip", token, archive)
        var apk: File? = null
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                    val output = File(dir, entry.name.substringAfterLast('/').ifBlank { "nexus-${artifact.variant}.apk" })
                    FileOutputStream(output).use { out -> zip.copyTo(out) }
                    apk = output
                    break
                }
            }
        }
        archive.delete()
        val file = apk ?: throw IllegalStateException("The GitHub artifact does not contain an APK.")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return InstalledApk(file, uri)
    }

    private fun get(url: String, token: String): String = request(url, token, null)

    private fun download(url: String, token: String, destination: File) {
        request(url, token, destination)
    }

    private fun request(urlString: String, token: String, destination: File?): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("GitHub request failed ($code)${if (body.isBlank()) "" else ": ${body.take(180)}"}")
            }
            if (destination != null) {
                destination.parentFile?.mkdirs()
                connection.inputStream.use { input -> FileOutputStream(destination).use { output -> input.copyTo(output) } }
                return ""
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDate(value: String): Date {
        return runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(value) ?: Date(0) }.getOrDefault(Date(0))
    }
}
