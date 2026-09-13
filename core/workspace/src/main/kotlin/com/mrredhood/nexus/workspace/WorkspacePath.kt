package com.mrredhood.nexus.workspace

/**
 * Normalizes workspace-relative paths and rejects attempts to escape the root.
 */
object WorkspacePath {
    fun normalize(relativePath: String): String {
        require(relativePath.isNotBlank()) { "Path must not be blank" }
        val normalized = relativePath.replace('\\', '/').trimStart('/')
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        require(parts.none { it == ".." }) { "Path escapes the workspace root" }
        return parts.joinToString("/")
    }
}
