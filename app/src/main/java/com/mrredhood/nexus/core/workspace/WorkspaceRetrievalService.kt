package com.mrredhood.nexus.core.workspace

/** A ranked workspace candidate selected for AI context. */
data class WorkspaceRetrievalResult(
    val entry: WorkspaceIndexEntry,
    val score: Int,
    val reasons: List<String>
)

data class WorkspaceRetrievalOptions(
    val query: String,
    val limit: Int = 10,
    val language: String? = null,
    val currentFile: String? = null,
    val relatedPaths: Set<String> = emptySet()
) {
    init {
        require(limit in 1..100) { "limit must be between 1 and 100" }
    }
}

/**
 * Fast, metadata-only retrieval over WorkspaceIndex. It never reads files and
 * therefore stays cheap enough to run for every AI request on Android.
 */
class WorkspaceRetrievalService {
    fun retrieve(index: WorkspaceIndex, options: WorkspaceRetrievalOptions): List<WorkspaceRetrievalResult> {
        val query = options.query.trim().lowercase()
        val terms = query.split(Regex("\\s+"))
            .map { it.trim('.', '/', '\\', ':', '_', '-') }
            .filter { it.length >= 2 }
            .distinct()

        return index.files.asSequence()
            .filter { options.language == null || it.language.equals(options.language, true) }
            .mapNotNull { entry -> score(entry, terms, query, options) }
            .sortedWith(compareByDescending<WorkspaceRetrievalResult> { it.score }.thenBy { it.entry.path.length }.thenBy { it.entry.path })
            .take(options.limit)
            .toList()
    }

    private fun score(
        entry: WorkspaceIndexEntry,
        terms: List<String>,
        query: String,
        options: WorkspaceRetrievalOptions
    ): WorkspaceRetrievalResult? {
        val path = entry.path.lowercase()
        val name = entry.name.lowercase()
        val symbols = entry.symbols.map { it.lowercase() }
        var score = 0
        val reasons = mutableListOf<String>()

        if (query.isNotEmpty() && name == query) {
            score += 100
            reasons += "exact filename"
        } else if (query.isNotEmpty() && path == query) {
            score += 95
            reasons += "exact path"
        }

        if (query.isNotEmpty() && name.contains(query)) {
            score += 60
            reasons += "filename match"
        }
        if (query.isNotEmpty() && path.contains(query)) {
            score += 45
            reasons += "path match"
        }

        var matchedTerms = 0
        for (term in terms) {
            val symbolMatch = symbols.any { it == term || it.contains(term) }
            val nameMatch = name.contains(term)
            val pathMatch = path.contains(term)
            if (nameMatch) score += 20
            if (pathMatch) score += 10
            if (symbolMatch) score += 35
            if (nameMatch || pathMatch || symbolMatch) matchedTerms++
        }
        if (symbolMatchCount(symbols, terms) > 0) reasons += "symbol match"
        if (matchedTerms > 0) reasons += "query terms"

        if (options.currentFile != null && path == options.currentFile.replace('\\', '/').lowercase()) {
            score += 80
            reasons += "current file"
        }
        if (options.relatedPaths.any { path == it.replace('\\', '/').lowercase() }) {
            score += 55
            reasons += "related file"
        }

        return if (score > 0) WorkspaceRetrievalResult(entry, score, reasons.distinct()) else null
    }

    private fun symbolMatchCount(symbols: List<String>, terms: List<String>): Int =
        terms.count { term -> symbols.any { it == term || it.contains(term) } }
}
