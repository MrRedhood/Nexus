package com.mrredhood.nexus.core.workspace

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.net.URLEncoder

data class GitHubRemoteFile(val path: String, val sha: String, val size: Long = 0L)
data class GitHubSyncStatus(val branch: String, val remoteCommit: String, val changed: List<String>, val added: List<String>, val deleted: List<String>, val unchanged: Int)
data class GitHubCommitResult(val commitSha: String, val treeSha: String, val message: String)
data class GitBranch(val name: String, val sha: String, val current: Boolean)
data class GitCommit(val sha: String, val message: String, val author: String, val timestamp: String, val filesChanged: Int)
data class GitDiff(val path: String, val before: String, val after: String, val addedLines: Int, val removedLines: Int)

/** GitHub Git Database transport. Nexus does not bundle a native Git executable. */
class GitHubRepositoryService(private val fileSystem: WorkspaceFileSystem) {
    suspend fun status(repository: String, branch: String, token: String, workspace: Workspace): GitHubSyncStatus = withContext(Dispatchers.IO) {
        val remote = loadRemoteTree(repository, branch, token)
        val local = collectLocalFiles(workspace)
        val comparable = remote.files.filterKeys { it in remote.contents }
        val changed = mutableListOf<String>(); val added = mutableListOf<String>()
        local.forEach { (path, content) ->
            val r = comparable[path]
            if (r == null) added += path else if (gitBlobSha(content) != r.sha) changed += path
        }
        val deleted = comparable.keys.filter { it !in local.keys }.sorted()
        val unchanged = local.count { (p, c) -> comparable[p]?.sha == gitBlobSha(c) }
        GitHubSyncStatus(branch, remote.commitSha, changed.sorted(), added.sorted(), deleted, unchanged)
    }

    suspend fun fetch(repository: String, branch: String, token: String, workspace: Workspace): Int = withContext(Dispatchers.IO) {
        val remote = loadRemoteTree(repository, branch, token); var count = 0
        remote.contents.keys.sorted().forEach { path -> fileSystem.write(workspace, path, remote.contents.getValue(path), mimeTypeFor(path)); count++ }
        count
    }

    suspend fun diff(repository: String, branch: String, token: String, workspace: Workspace): List<GitDiff> = withContext(Dispatchers.IO) {
        val remote = loadRemoteTree(repository, branch, token); val local = collectLocalFiles(workspace); val out = mutableListOf<GitDiff>()
        (local.keys + remote.contents.keys).distinct().sorted().forEach { path ->
            val before = remote.contents[path].orEmpty(); val after = local[path].orEmpty()
            if (before != after) {
                val (a, r) = lineStats(before, after); out += GitDiff(path, before, after, a, r)
            }
        }; out
    }

    suspend fun commitAndPush(repository: String, branch: String, token: String, workspace: Workspace, message: String, staged: Set<String>? = null): GitHubCommitResult = withContext(Dispatchers.IO) {
        require(message.isNotBlank()) { "Commit message is required" }
        val remote = loadRemoteTree(repository, branch, token); val local = collectLocalFiles(workspace)
        val allowed = staged ?: (local.keys + remote.contents.keys).toSet()
        val base = remote.contents.toMutableMap(); val treeEntries = mutableListOf<JSONObject>()
        allowed.sorted().forEach { path ->
            if (path.startsWith(".git/")) return@forEach
            val content = local[path]
            if (content != null) treeEntries += JSONObject().apply { put("path", path); put("mode", "100644"); put("type", "blob"); put("content", content) }
            else if (remote.files.containsKey(path)) treeEntries += JSONObject().apply { put("path", path); put("mode", "100644"); put("type", "blob"); put("sha", JSONObject.NULL) }
        }
        val unchangedEntries = (base.keys - allowed).mapNotNull { p -> remote.files[p]?.let { JSONObject().apply { put("path", p); put("mode", "100644"); put("type", "blob"); put("sha", it.sha) } } }
        treeEntries += unchangedEntries
        val tree = apiPost("/repos/$repository/git/trees", token, JSONObject().apply { put("base_tree", remote.treeSha); put("tree", JSONArray(treeEntries)) })
        val treeSha = tree.getString("sha")
        val commit = apiPost("/repos/$repository/git/commits", token, JSONObject().apply { put("message", message.trim()); put("tree", treeSha); put("parents", JSONArray().put(remote.commitSha)) })
        val commitSha = commit.getString("sha")
        apiPatch("/repos/$repository/git/refs/heads/${encode(branch)}", token, JSONObject().put("sha", commitSha))
        GitHubCommitResult(commitSha, treeSha, message.trim())
    }

    suspend fun branches(repository: String, current: String, token: String): List<GitBranch> = withContext(Dispatchers.IO) {
        val arr = apiGetArray("/repos/$repository/branches?per_page=100", token); (0 until arr.length()).map { i -> val o=arr.getJSONObject(i); GitBranch(o.getString("name"), o.getJSONObject("commit").getString("sha"), o.getString("name")==current) }.sortedBy { it.name }
    }

    suspend fun createBranch(repository: String, name: String, fromSha: String, token: String) = withContext(Dispatchers.IO) {
        require(name.matches(Regex("[A-Za-z0-9._/-]+"))) { "Invalid branch name" }
        apiPost("/repos/$repository/git/refs", token, JSONObject().apply { put("ref", "refs/heads/$name"); put("sha", fromSha) })
    }

    suspend fun checkout(repository: String, branch: String, token: String): String = withContext(Dispatchers.IO) { apiGet("/repos/$repository/git/ref/heads/${encode(branch)}", token).getJSONObject("object").getString("sha") }

    suspend fun log(repository: String, branch: String, token: String, limit: Int = 30): List<GitCommit> = withContext(Dispatchers.IO) {
        val arr = apiGetArray("/repos/$repository/commits?sha=${encode(branch)}&per_page=${limit.coerceIn(1,100)}", token)
        (0 until arr.length()).map { i -> val o=arr.getJSONObject(i); val c=o.getJSONObject("commit"); GitCommit(o.getString("sha"), c.getString("message").lineSequence().first(), c.getJSONObject("author").optString("name","Unknown"), c.getJSONObject("author").optString("date",""), o.optInt("stats",0)) }
    }

    private suspend fun loadRemoteTree(repository: String, branch: String, token: String): RemoteTree {
        val ref=apiGet("/repos/$repository/git/ref/heads/${encode(branch)}",token); val commitSha=ref.getJSONObject("object").getString("sha")
        val commit=apiGet("/repos/$repository/git/commits/$commitSha",token); val treeSha=commit.getJSONObject("tree").getString("sha")
        val tree=apiGet("/repos/$repository/git/trees/$treeSha?recursive=1",token); val files=linkedMapOf<String,GitHubRemoteFile>(); val contents=linkedMapOf<String,String>(); val items=tree.optJSONArray("tree")?:JSONArray()
        for(i in 0 until items.length()){ val item=items.getJSONObject(i); if(item.optString("type")!="blob") continue; val path=item.getString("path"); if(path.startsWith(".git/")) continue; val sha=item.getString("sha"); val size=item.optLong("size",0); files[path]=GitHubRemoteFile(path,sha,size); if(size<=WorkspaceFileSystem.MAX_EDITABLE_FILE_BYTES){ val blob=apiGet("/repos/$repository/git/blobs/$sha",token); if(blob.optString("encoding")=="base64"){ val bytes=Base64.decode(blob.getString("content").replace("\n",""),Base64.DEFAULT); val text=bytes.toString(Charsets.UTF_8); if(text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) contents[path]=text } } }
        return RemoteTree(commitSha,treeSha,files,contents)
    }

    private suspend fun collectLocalFiles(workspace: Workspace): Map<String,String> { val result=linkedMapOf<String,String>(); suspend fun visit(dir:String){ fileSystem.list(workspace,dir).forEach{ e-> if(e.name==".git") return@forEach; if(e.type==EntryType.DIRECTORY) visit(e.relativePath) else if(e.sizeBytes<=WorkspaceFileSystem.MAX_EDITABLE_FILE_BYTES) runCatching{fileSystem.read(workspace,e.relativePath).content}.onSuccess{result[e.relativePath]=it} } }; visit(""); return result }
    private fun gitBlobSha(content:String):String{val b=content.toByteArray(Charsets.UTF_8); val h="blob ${b.size}\u0000".toByteArray(Charsets.UTF_8); return MessageDigest.getInstance("SHA-1").digest(h+b).joinToString(""){ "%02x".format(it) }}
    private fun lineStats(before:String,after:String):Pair<Int,Int>{ val a=before.lines(); val b=after.lines(); var add=0;var rem=0; val n=maxOf(a.size,b.size); for(i in 0 until n){val x=a.getOrNull(i);val y=b.getOrNull(i);if(x!=y){if(y!=null)add++;if(x!=null)rem++}};return add to rem }
    private fun apiGet(path:String,token:String)=request("GET",path,token,null)
    private fun apiGetArray(path:String,token:String):JSONArray{val s=requestText("GET",path,token,null);return JSONArray(s)}
    private fun apiPost(path:String,token:String,body:JSONObject)=request("POST",path,token,body)
    private fun apiPatch(path:String,token:String,body:JSONObject)=request("PATCH",path,token,body)
    private fun request(method:String,path:String,token:String,body:JSONObject?):JSONObject=JSONObject(requestText(method,path,token,body))
    private fun requestText(method:String,path:String,token:String,body:JSONObject?):String{require(token.isNotBlank()){ "GitHub token is not configured. Add it in Settings > GitHub." }; val c=(URL("https://api.github.com$path").openConnection() as HttpURLConnection).apply{requestMethod=method;connectTimeout=15000;readTimeout=30000;setRequestProperty("Accept","application/vnd.github+json");setRequestProperty("Authorization","Bearer $token");setRequestProperty("X-GitHub-Api-Version","2022-11-28");setRequestProperty("User-Agent","Nexus-Android");if(body!=null){doOutput=true;setRequestProperty("Content-Type","application/json; charset=utf-8")}};body?.toString()?.toByteArray(Charsets.UTF_8)?.let{c.outputStream.use{s->s.write(it)}};val status=c.responseCode;val stream=if(status in 200..299)c.inputStream else c.errorStream;val response=stream?.bufferedReader()?.use{it.readText()}.orEmpty();c.disconnect();if(status !in 200..299){val m=runCatching{JSONObject(response).optString("message")}.getOrNull().orEmpty();error("GitHub API $status${if(m.isNotBlank())": $m" else ""}")};return response}
    private fun encode(v:String)=URLEncoder.encode(v,Charsets.UTF_8.name()).replace("+","%20")
    private fun mimeTypeFor(path:String)=when(path.substringAfterLast('.',"").lowercase()){"json"->"application/json";"xml"->"application/xml";"html","htm"->"text/html";"css"->"text/css";else->"text/plain"}
    private data class RemoteTree(val commitSha:String,val treeSha:String,val files:Map<String,GitHubRemoteFile>,val contents:Map<String,String>)
}
