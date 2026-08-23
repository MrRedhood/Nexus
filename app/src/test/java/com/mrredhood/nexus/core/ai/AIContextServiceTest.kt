package com.mrredhood.nexus.core.ai

import com.mrredhood.nexus.core.workspace.WorkspaceIndexEntry
import com.mrredhood.nexus.core.workspace.WorkspaceRetrievalResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AIContextServiceTest {
    private val service = AIContextService()

    @Test
    fun assemblesSourcesInPriorityOrder() {
        val snapshot = service.assemble(
            AIContextRequest(
                userMessage = "Fix the crash",
                selection = context(AIContextSource.SELECTION, "selected", "bad code"),
                currentFile = context(AIContextSource.CURRENT_FILE, "Main.kt", "fun main() {}"),
                gitDiff = context(AIContextSource.GIT_DIFF, "diff", "+fix"),
                memory = context(AIContextSource.MEMORY, "memory", "Use MVVM")
            )
        )

        assertEquals(AIContextSource.USER_MESSAGE, snapshot.items[0].source)
        assertEquals(AIContextSource.SELECTION, snapshot.items[1].source)
        assertEquals(AIContextSource.CURRENT_FILE, snapshot.items[2].source)
        assertTrue(snapshot.items.any { it.source == AIContextSource.GIT_DIFF })
        assertTrue(snapshot.items.any { it.source == AIContextSource.MEMORY })
    }

    @Test
    fun disablesOptionalSources() {
        val snapshot = service.assemble(
            AIContextRequest(
                userMessage = "Explain",
                currentFile = context(AIContextSource.CURRENT_FILE, "A.kt", "code"),
                gitDiff = context(AIContextSource.GIT_DIFF, "diff", "change"),
                memory = context(AIContextSource.MEMORY, "memory", "memory")
            ),
            AIContextOptions(includeCurrentFile = false, includeGitDiff = false, includeMemory = false)
        )

        assertFalse(snapshot.items.any { it.source == AIContextSource.CURRENT_FILE })
        assertFalse(snapshot.items.any { it.source == AIContextSource.GIT_DIFF })
        assertFalse(snapshot.items.any { it.source == AIContextSource.MEMORY })
    }

    @Test
    fun respectsRelatedFileLimit() {
        val related = (1..5).map { number ->
            val entry = WorkspaceIndexEntry("File$number.kt", "File$number.kt", 10, 0, "kotlin", emptyList())
            WorkspaceRetrievalResult(entry, 100 - number, listOf("query terms")) to
                context(AIContextSource.RELATED_FILE, "File$number.kt", "content")
        }

        val snapshot = service.assemble(
            AIContextRequest("Explain", relatedFiles = related),
            AIContextOptions(maxRelatedFiles = 2)
        )

        assertEquals(2, snapshot.items.count { it.source == AIContextSource.RELATED_FILE })
    }

    @Test
    fun enforcesTokenBudgetAndReportsDroppedItems() {
        val snapshot = service.assemble(
            AIContextRequest(
                userMessage = "Explain",
                currentFile = context(AIContextSource.CURRENT_FILE, "Main.kt", "x".repeat(1000)),
                gitDiff = context(AIContextSource.GIT_DIFF, "diff", "y".repeat(1000))
            ),
            AIContextOptions(maxContextTokens = 100)
        )

        assertTrue(snapshot.estimatedTokens <= 100)
        assertTrue(snapshot.truncated)
        assertTrue(snapshot.droppedItems.isNotEmpty() || snapshot.items.any { it.reason == "truncated to fit context budget" })
    }

    @Test
    fun appliesFileSizeLimit() {
        val snapshot = service.assemble(
            AIContextRequest("Explain", currentFile = context(AIContextSource.CURRENT_FILE, "Main.kt", "a".repeat(100))),
            AIContextOptions(maxFileSizeChars = 20)
        )

        val file = snapshot.items.first { it.source == AIContextSource.CURRENT_FILE }
        assertTrue(file.content.length <= 60)
        assertTrue(file.reason!!.contains("file size limit"))
    }

    private fun context(source: AIContextSource, label: String, content: String) =
        AIContextItem(source, label, path = label, content = content, estimatedTokens = AIContextService.estimateTokens(content))
}
