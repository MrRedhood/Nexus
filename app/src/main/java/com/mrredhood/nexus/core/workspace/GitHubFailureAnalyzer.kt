package com.mrredhood.nexus.core.workspace

data class GitHubFailureFinding(
    val category: String,
    val message: String,
    val evidence: String,
    val lineNumber: Int?,
    val confidence: String = "high"
)

object GitHubFailureAnalyzer {
    private data class Rule(
        val category: String,
        val pattern: Regex,
        val message: String,
        val priority: Int
    )

    private val rules = listOf(
        Rule("Compilation", Regex("(?i)(unresolved reference|cannot find symbol|type mismatch|compilation failed|compile.*failed|does not exist)"), "A source compilation error was reported. Fix the exact symbol, type, declaration, or source location cited by the compiler.", 100),
        Rule("Android resources", Regex("(?i)(android resource linking failed|aapt2.*error|resource .* not found|manifest merger failed|duplicate resources)"), "Android resource or manifest processing failed. Inspect the exact resource or manifest location reported by AAPT2 or the manifest merger.", 95),
        Rule("Kotlin", Regex("(?i)(kotlinc|expecting .*|conflicting overloads|platform declaration clash|kotlin.*error)"), "The Kotlin compiler reported a language or declaration error. Use its cited file and line as the primary fix location.", 92),
        Rule("Java", Regex("(?i)(javac|java: .*error:|class .* is public, should be declared)"), "The Java compiler reported a source error. Use the exact compiler location and message rather than inferring a cause.", 90),
        Rule("Dependency", Regex("(?i)(could not resolve|failed to resolve|could not find .* dependency|failed to find|dependency.*not found|401 unauthorized|403 forbidden)"), "Dependency resolution failed. Check coordinates, repositories, credentials, and network access.", 85),
        Rule("Tests", Regex("(?i)(tests? failed|there were failing tests|assertionerror|test.*failure)"), "A test failure was reported. Inspect the failing test and assertion output before changing production code.", 80),
        Rule("Lint", Regex("(?i)(lint.*error|lint.*failed|detekt.*failed|ktlint.*failed|checkstyle.*failed)"), "A static-analysis or formatting check failed. Fix the reported finding at its cited file and line.", 75),
        Rule("Gradle", Regex("(?i)(execution failed for task|gradle task .* failed|build.gradle|settings.gradle|gradle.*exception)"), "Gradle reported a build or configuration failure. Inspect the first concrete error associated with the failed task.", 60),
        Rule("Permissions", Regex("(?i)(permission denied|access denied|resource not accessible)"), "The build encountered an access or permission failure. Verify repository, package, signing, or workflow permissions.", 55),
        Rule("Network", Regex("(?i)(connection timed out|connection refused|could not connect|network is unreachable|temporary failure in name resolution)"), "The job encountered a network failure. Verify the remote service and retry before treating it as a source-code defect.", 50),
        Rule("Timeout", Regex("(?i)(timed out|timeout|exceeded.*time limit)"), "The CI job timed out. Inspect the longest-running step and recent log activity before changing code.", 45)
    )

    fun analyze(log: String, maxFindings: Int = 8): List<GitHubFailureFinding> {
        if (log.isBlank() || maxFindings <= 0) return emptyList()
        val lines = log.lines()
        val matches = mutableListOf<GitHubFailureFinding>()
        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val rule = rules.firstOrNull { it.pattern.containsMatchIn(line) } ?: continue
            matches += GitHubFailureFinding(rule.category, rule.message, line.take(500), index + 1)
        }
        return matches
            .distinctBy { "${it.category}|${it.evidence}" }
            .sortedWith(compareByDescending<GitHubFailureFinding> { rules.first { it.category == it.category }.priority }.thenBy { it.lineNumber ?: Int.MAX_VALUE })
            .take(maxFindings)
            .map { it.copy(confidence = if (it.category == "Gradle") "medium" else "high") }
    }
}