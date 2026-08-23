package com.mrredhood.nexus.core.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceIndexTest {
    @Test
    fun findRanksExactFileNameFirst() {
        val index = WorkspaceIndex(
            workspaceId = "w",
            workspaceName = "Demo",
            files = listOf(
                WorkspaceIndexEntry("src/main/Foo.kt", "Foo.kt", 10, 1, "kotlin", listOf("Foo")),
                WorkspaceIndexEntry("src/Foo.kt", "Foo.kt", 10, 1, "kotlin", listOf("Bar")),
                WorkspaceIndexEntry("README.md", "README.md", 10, 1, "markdown", emptyList())
            ),
            directories = listOf("src", "src/main"),
            truncated = false
        )

        val results = index.find("Foo.kt")
        assertEquals(2, results.size)
        assertEquals("src/main/Foo.kt", results.first().path)
    }

    @Test
    fun contextReportsIndexStatistics() {
        val index = WorkspaceIndex(
            workspaceId = "w",
            workspaceName = "Demo",
            files = listOf(
                WorkspaceIndexEntry("src/Main.kt", "Main.kt", 100, 1, "kotlin", listOf("Main")),
                WorkspaceIndexEntry("README.md", "README.md", 50, 1, "markdown", emptyList())
            ),
            directories = listOf("src"),
            truncated = false
        )
        val intelligence = WorkspaceIntelligence(index, "Android/Gradle", listOf("kotlin" to 1, "markdown" to 1), listOf("README.md"))
        val context = intelligence.toContextText()

        assertTrue(context.contains("PROJECT TYPE: Android/Gradle"))
        assertTrue(context.contains("FILES: 2"))
        assertTrue(context.contains("SYMBOLS: 1"))
        assertTrue(context.contains("README.md"))
    }
}
