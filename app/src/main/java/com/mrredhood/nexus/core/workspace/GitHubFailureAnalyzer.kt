package com.mrredhood.nexus.core.workspace

data class GitHubFailureFinding(
    val category: String,
    val message: String,
    val evidence: String,
    val lineNumber: Int?
)

object GitHubFailureAnalyzer {
    private data class Rule(
        val category: String,
        val pattern: Regex,
        val message: String
    )

    private val rules = listOf(
        Rule("Compilation", Regex("(?i)(compilation failed|compile.*failed|unresolved reference|cannot find symbol|does not exist|type mismatch)"), "A source compilation error was reported. Open the cited source location and fix the reported symbol, type, or declaration."),
        Rule("Dependency", Regex("(?i)(could not resolve|failed to resolve|could not find .* dependency|failed to find|dependency.*not found|401 unauthorized|403 forbidden)"), "Dependency resolution failed. Check the dependency coordinates, repository configuration, credentials, or network access."),
        Rule("Gradle", Regex("(?i)(gradle task .* failed|execution failed for task|build.gradle|settings.gradle|gradle.*exception)"), "Gradle reported a build configuration or task failure. Inspect the first concrete Gradle error below the task failure."),
        Rule("Android", Regex("(?i)(manifest merger failed|resource .* not found|aapt2.*error|android resource linking failed|duplicate resources)"), "Android resource or manifest processing failed. Inspect the referenced resource, manifest entry, or duplicate declaration."),
        Rule("Kotlin", Regex("(?i)(kotlin.*error|kotlinc|expecting .*|conflicting overloads|platform declaration clash)"), "The Kotlin compiler reported a language or declaration error. Use the compiler's cited file and line as the primary fix location."),
        Rule("Java", Regex("(?i)(javac|java: .*error:|error: cannot find symbol|class .* is public, should be declared)"), "The Java compiler reported a source error. Use the exact compiler location and message rather than inferring a cause."),
        Rule("Tests", Regex("(?i)(tests? failed|there were failing tests|assertionerror|test.*failure)"), "A test failure was reported. Inspect the failing test name and assertion output before changing production code."),
        Rule("Lint", Regex("(?i)(lint.*error|lint.*failed|detekt.*failed|ktlint.*failed|checkstyle.*failed)"), "A static-analysis or formatting check failed. Fix the reported finding at the cited file and line."),
        Rule("Timeout", Regex("(?i)(timed out|timeout|exceeded.*time limit)"), "The CI job timed out. Check the longest-running step and recent log activity before changing code."),
        Rule("Permissions", Regex("(?i)(permission denied|access denied|resource not accessible)"), "The build encountered an access or permission failure. Verify repository, package, signing, or workflow permissions."),
        Rule("Network", Regex("(?i)(connection timed out|connection refused|could not connect|network is unreachable|temporary failure in name resolution)"), "The job encountered a network failure. Verify the remote service and retry before treating it as a source-code defect.")
    )

    fun analyze(log: String, maxFindings: Int = 8): List<GitHubFailureFinding> {
        if (log.isBlank()) return emptyList()
        val lines = log.lines()
        val findings = mutableListOf<GitHubFailureFinding>()
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            for (rule in rules) {
                if (rule.pattern.containsMatchIn(trimmed)) {
                    findings += GitHubFailureFinding(
                        category = rule.category,
                        message = rule.message,
                        evidence = trimmed.take(500),
                        lineNumber = index + 1
                    )
                    break
                }
            }
            if (findings.size >= maxFindings) break
        }
        return findings.distinctBy { "${it.category}|${it.evidence}" }
    }
}