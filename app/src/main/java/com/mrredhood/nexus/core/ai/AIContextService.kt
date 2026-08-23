package com.mrredhood.nexus.core.ai

/**
 * Assembles a bounded, inspectable AI context from explicit and retrieved
 * sources. It does not read files itself; callers provide file contents after
 * retrieval so this layer remains deterministic and easy to test.
 */
class AIContextService {
    fun assemble(request: AIContextRequest, options: AIContextOptions = AIContextOptions()): AIContextSnapshot {
        val candidates = buildCandidates(request, options)
        val budget = options.maxContextTokens
        var used = 0
        val included = mutableListOf<AIContextItem>()
        val dropped = mutableListOf<AIContextItem>()

        for (item in candidates) {
            if (item.content.isEmpty()) continue
            val cost = item.estimatedTokens
            if (used + cost <= budget) {
                included += item
                used += cost
                continue
            }

            val remaining = budget - used
            if (remaining > 0) {
                val truncatedContent = truncateToTokens(item.content, remaining)
                if (truncatedContent.isNotEmpty()) {
                    val truncated = item.copy(
                        content = truncatedContent,
                        estimatedTokens = estimateTokens(truncatedContent),
                        reason = "${item.reason ?: "context source"}; truncated to fit context budget"
                    )
                    if (used + truncated.estimatedTokens <= budget) {
                        included += truncated
                        used += truncated.estimatedTokens
                        continue
                    }
                }
            }

            dropped += item.copy(included = false, reason = "context budget exceeded")
        }

        return AIContextSnapshot(
            items = included,
            estimatedTokens = used,
            tokenLimit = budget,
            truncated = dropped.isNotEmpty() || included.any { it.reason?.contains("truncated to fit context budget") == true },
            droppedItems = dropped
        )
    }

    private fun buildCandidates(request: AIContextRequest, options: AIContextOptions): List<AIContextItem> {
        val result = mutableListOf<AIContextItem>()
        result += item(AIContextSource.USER_MESSAGE, "user request", request.userMessage, "highest priority")

        if (options.includeSelection) request.selection?.let {
            result += bounded(it, options, "explicit selection")
        }

        if (options.includeCurrentFile && request.currentFile != null) {
            result += bounded(request.currentFile, options, "open file")
        }

        request.referencedFiles.forEach { result += bounded(it, options, "explicitly referenced") }

        if (options.automaticContext != AutomaticContextMode.NEVER) {
            request.relatedFiles
                .sortedByDescending { it.first.score }
                .take(options.maxRelatedFiles)
                .forEach { (retrieval, context) ->
                    result += bounded(context, options, retrieval.reasons.joinToString(", "))
                        .copy(source = AIContextSource.RELATED_FILE)
                }
        }

        if (options.includeGitDiff) request.gitDiff?.let { result += it.copy(reason = "current changes") }
        if (options.includeTerminalOutput) request.terminalOutput?.let { result += it.copy(reason = "recent terminal output") }
        if (options.includeWorkspaceSummary) request.workspaceSummary?.let { result += it.copy(reason = "workspace intelligence") }
        if (options.includeMemory) request.memory?.let { result += it.copy(reason = "stored memory") }

        return result
    }

    private fun bounded(item: AIContextItem, options: AIContextOptions, reason: String): AIContextItem {
        if (item.content.length <= options.maxFileSizeChars) return item.copy(reason = reason)
        val suffix = "\n…[file content truncated]"
        val content = item.content.take((options.maxFileSizeChars - suffix.length).coerceAtLeast(0)) + suffix
        return item.copy(content = content, estimatedTokens = estimateTokens(content), reason = "$reason; file size limit")
    }

    private fun item(source: AIContextSource, label: String, content: String, reason: String): AIContextItem =
        AIContextItem(source, label, content = content, estimatedTokens = estimateTokens(content), reason = reason)

    private fun truncateToTokens(content: String, tokens: Int): String {
        if (tokens <= 0) return ""
        val suffix = "\n…[context truncated]"
        val maxChars = tokens * 4
        if (content.length <= maxChars) return content
        val contentChars = (maxChars - suffix.length).coerceAtLeast(0)
        return if (contentChars == 0) "" else content.take(contentChars) + suffix
    }

    companion object {
        fun estimateTokens(content: String): Int = if (content.isEmpty()) 0 else (content.length + 3) / 4
    }
}
