package com.mrredhood.nexus.core.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Real Git operations exposed by GitHub's Git/REST APIs. */
data class GitMergeConflict(
    val path: String,
    val baseContent: String?,
    val headContent: String?,
    val ancestorContent: String?,
    val binary: Boolean
)

data class GitMergePreview(
    val baseBranch: String,
    val headBranch: String,
    val baseSha: String,
    val headSha: String,
    val mergeBaseSha: String,
    val conflicts: List<GitMergeConflict>
)

class GitHubAdvancedGitService {
    suspend fun mergeBranches(repository: String, base: String, head: String, token: String, message: String? = null): String = withContext(Dispatchers.IO) {
        require(base.isNotBlank() && head.isNotBlank() && base != head) { "Choose two different branches." }
        val body = JSONObject().put("base", base).put("head", head)
        if (!message.isNullOrBlank()) body.put("commit_message", message.trim())
        try {
            val response = request("POST", "/repos/$repository/merges", token, body)
            JSONObject(response).optString("sha").ifBlank { response }
        } catch (error: IllegalStateException) {
            if (error.message?.startsWith("GitHub 409:") == true) {
                throw IllegalStateException("Merge conflict between $base and $head. Use Merge conflicts to inspect and resolve the conflicting files.")
            }
            throw error
        }
    }

    suspend fun previewMergeConflicts(repository: String, base: String, head: String, token: String): GitMergePreview = withContext(Dispatchers.IO) {
        require(base.isNotBlank() && head.isNotBlank() && base != head) { "Choose two different branches." }
        val compare = JSONObject(request("GET", "/repos/$repository/compare/${encode(base)}...${encode(head)}", token))
        val baseSha = compare.getJSONObject("base_commit").getString("sha")
        val headSha = compare.getJSONObject("merge_base_commit").let { mergeBase ->
            compare.optString("merge_base_commit").ifBlank { head }
        }
        val actualHeadSha = JSONObject(request("GET", "/repos/$repository/git/ref/heads/${encode(head)}", token)).getJSONObject("object").getString("sha")
        val actualBaseSha = JSONObject(request("GET", "/repos/$repository/git/ref/heads/${encode(base)}", token)).getJSONObject("object").getString("sha")
        val mergeBaseSha = compare.getJSONObject("merge_base_commit").getString("sha")
        val baseTree = commitTree(repository, actualBaseSha, token)
        val headTree = commitTree(repository, actualHeadSha, token)
        val ancestorTree = commitTree(repository, mergeBaseSha, token)
        val paths = (baseTree.keys + headTree.keys + ancestorTree.keys).toSortedSet()
        val conflicts = paths.mapNotNull { path ->
            val baseEntry = baseTree[path]
            val headEntry = headTree[path]
            val ancestorEntry = ancestorTree[path]
            val baseShaForPath = baseEntry?.sha
            val headShaForPath = headEntry?.sha
            val ancestorShaForPath = ancestorEntry?.sha
            if (baseShaForPath == headShaForPath) return@mapNotNull null
            if (ancestorShaForPath == baseShaForPath || ancestorShaForPath == headShaForPath) return@mapNotNull null
            val binary = listOf(baseEntry, headEntry, ancestorEntry).any { it?.type != null && it.type != "blob" } ||
                listOf(baseEntry, headEntry, ancestorEntry).mapNotNull { it?.size }.any { it > MAX_TEXT_BYTES }
            GitMergeConflict(
                path = path,
                baseContent = if (binary) null else blobContent(repository, baseShaForPath, token),
                headContent = if (binary) null else blobContent(repository, headShaForPath, token),
                ancestorContent = if (binary) null else blobContent(repository, ancestorShaForPath, token),
                binary = binary
            )
        }
        GitMergePreview(base, head, actualBaseSha, actualHeadSha, mergeBaseSha, conflicts)
    }

    suspend fun resolveMergeConflicts(
        repository: String,
        preview: GitMergePreview,
        resolutions: Map<String, String>,
        token: String,
        message: String = "Merge ${preview.headBranch} into ${preview.baseBranch}"
    ): String = withContext(Dispatchers.IO) {
        require(preview.baseBranch != preview.headBranch) { "Choose two different branches." }
        val currentBaseSha = JSONObject(request("GET", "/repos/$repository/git/ref/heads/${encode(preview.baseBranch)}", token)).getJSONObject("object").getString("sha")
        require(currentBaseSha == preview.baseSha) { "The base branch changed while resolving conflicts. Refresh the conflict preview and try again." }
        val baseTree = commitTree(repository, preview.baseSha, token)
        val headTree = commitTree(repository, preview.headSha, token)
        val ancestorTree = commitTree(repository, preview.mergeBaseSha, token)
        val entries = JSONArray()
        val paths = (baseTree.keys + headTree.keys + ancestorTree.keys).toSortedSet()
        paths.forEach { path ->
            val baseEntry = baseTree[path]
            val headEntry = headTree[path]
            val ancestorEntry = ancestorTree[path]
            val baseSha = baseEntry?.sha
            val headSha = headEntry?.sha
            val ancestorSha = ancestorEntry?.sha
            val resolved = when {
                baseSha == headSha -> baseEntry
                ancestorSha == baseSha -> headEntry
                ancestorSha == headSha -> baseEntry
                else -> {
                    require(resolutions.containsKey(path)) { "Unresolved merge conflict: $path" }
                    val content = resolutions[path]
                    require(content.isNotEmpty() || baseSha == null || headSha == null) { "Empty resolution is not allowed for existing file $path." }
                    JSONObject().apply {
                        put("path", path)
                        put("mode", baseEntry?.mode ?: headEntry?.mode ?: "100644")
                        put("type", "blob")
                        put("content", content)
                    }
                }
            }
            if (resolved != null) {
                if (resolved.has("content")) entries.put(resolved)
                else entries.put(JSONObject().apply {
                    put("path", path)
                    put("mode", resolved.mode)
                    put("type", resolved.type)
                    put("sha", resolved.sha)
                })
            } else if (baseSha != null || headSha != null) {
                entries.put(JSONObject().apply {
                    put("path", path)
                    put("mode", baseEntry?.mode ?: headEntry?.mode ?: "100644")
                    put("type", "blob")
                    put("sha", headSha ?: JSONObject.NULL)
                })
            }
        }
        val baseCommit = JSONObject(request("GET", "/repos/$repository/git/commits/${preview.baseSha}", token))
        val tree = JSONObject(request("POST", "/repos/$repository/git/trees", token, JSONObject().put("base_tree", baseCommit.getJSONObject("tree").getString("sha")).put("tree", entries)))
        val commit = JSONObject(request("POST", "/repos/$repository/git/commits", token, JSONObject().apply {
            put("message", message.trim())
            put("tree", tree.getString("sha"))
            put("parents", JSONArray().put(preview.baseSha).put(preview.headSha))
        }))
        val commitSha = commit.getString("sha")
        request("PATCH", "/repos/$repository/git/refs/heads/${encode(preview.baseBranch)}", token, JSONObject().put("sha", commitSha))
        commitSha
    }

    suspend fun resetBranch(repository: String, branch: String, targetSha: String, token: String, force: Boolean = false) = withContext(Dispatchers.IO) {
        require(targetSha.matches(Regex("[0-9a-fA-F]{7,64}"))) { "Enter a valid commit SHA." }
        require(branch.isNotBlank()) { "Branch is required." }
        request("PATCH", "/repos/$repository/git/refs/heads/${encode(branch)}", token, JSONObject().put("sha", targetSha).put("force", force))
        Unit
    }

    suspend fun cherryPick(repository: String, commitSha: String, branch: String, token: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        require(commitSha.matches(Regex("[0-9a-fA-F]{7,64}"))) { "Enter a valid commit SHA." }
        require(branch.isNotBlank()) { "Branch is required." }
        val response = request("POST", "/repos/$repository/commits/${encode(commitSha)}/cherry-pick", token, JSONObject().put("branch", branch))
        val json = JSONObject(response)
        val status = json.optString("status")
        val sha = json.optString("sha")
        (status == "completed" || (status.isBlank() && sha.isNotBlank())) to (sha.ifBlank { response })
    }

    suspend fun createStashBranch(repository: String, branch: String, token: String, name: String): String = withContext(Dispatchers.IO) {
        require(name.matches(Regex("nexus/stash/[A-Za-z0-9._-]+"))) { "Invalid stash name. Use nexus/stash/<name>." }
        val baseSha = JSONObject(request("GET", "/repos/$repository/git/ref/heads/${encode(branch)}", token))
            .getJSONObject("object").getString("sha")
        request("POST", "/repos/$repository/git/refs", token, JSONObject().put("ref", "refs/heads/$name").put("sha", baseSha))
        name
    }

    suspend fun dropStash(repository: String, stashBranch: String, token: String) = withContext(Dispatchers.IO) {
        require(stashBranch.matches(Regex("nexus/stash/[A-Za-z0-9._-]+"))) { "Only Nexus stash branches can be dropped." }
        request("DELETE", "/repos/$repository/git/refs/heads/${encode(stashBranch)}", token)
    }

    private data class TreeEntry(val sha: String?, val mode: String, val type: String, val size: Long?)
    private companion object { const val MAX_TEXT_BYTES = 512L * 1024L }

    private suspend fun commitTree(repository: String, commitSha: String, token: String): Map<String, TreeEntry> {
        val commit = JSONObject(request("GET", "/repos/$repository/git/commits/$commitSha", token))
        val treeSha = commit.getJSONObject("tree").getString("sha")
        val tree = JSONObject(request("GET", "/repos/$repository/git/trees/$treeSha?recursive=1", token))
        val result = linkedMapOf<String, TreeEntry>()
        val items = tree.optJSONArray("tree") ?: JSONArray()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            result[item.getString("path")] = TreeEntry(item.optString("sha").ifBlank { null }, item.optString("mode", "100644"), item.optString("type", "blob"), if (item.has("size")) item.optLong("size") else null)
        }
        return result
    }

    private suspend fun blobContent(repository: String, sha: String?, token: String): String? {
        if (sha.isNullOrBlank()) return null
        val blob = JSONObject(request("GET", "/repos/$repository/git/blobs/$sha", token))
        if (blob.optString("encoding") != "base64") return null
        return android.util.Base64.decode(blob.getString("content").replace("\n", ""), android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
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
