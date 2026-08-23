package com.mrredhood.nexus.core.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceRetrievalServiceTest {
    private val index = WorkspaceIndex(
        workspaceId = "w",
        workspaceName = "Demo",
        files = listOf(
            WorkspaceIndexEntry("app/src/MainActivity.kt", "MainActivity.kt", 100, 1, "kotlin", listOf("MainActivity")),
            WorkspaceIndexEntry("app/src/WorkspaceManager.kt", "WorkspaceManager.kt", 100, 1, "kotlin", listOf("WorkspaceManager", "openWorkspace")),
            WorkspaceIndexEntry("app/src/ui/WorkspaceScreen.kt", "WorkspaceScreen.kt", 100, 1, "kotlin", listOf("WorkspaceScreen")),
            WorkspaceIndexEntry("README.md", "README.md", 50, 1, "markdown", emptyList())
        ),
        directories = listOf("app/src", "app/src/ui"),
        truncated = false
    )

    @Test
    fun exactFilenameRanksFirst() {
        val results = WorkspaceRetrievalService().retrieve(
            index,
            WorkspaceRetrievalOptions(query = "WorkspaceManager.kt", limit = 3)
        )

        assertEquals("app/src/WorkspaceManager.kt", results.first().entry.path)
        assertTrue(results.first().reasons.contains("exact filename"))
    }

    @Test
    fun symbolQueriesFindRelevantFiles() {
        val results = WorkspaceRetrievalService().retrieve(
            index,
            WorkspaceRetrievalOptions(query = "openWorkspace", limit = 3)
        )

        assertEquals("app/src/WorkspaceManager.kt", results.first().entry.path)
        assertTrue(results.first().reasons.contains("symbol match"))
    }

    @Test
    fun currentFileGetsUsefulBoost() {
        val results = WorkspaceRetrievalService().retrieve(
            index,
            WorkspaceRetrievalOptions(query = "Workspace", currentFile = "app/src/ui/WorkspaceScreen.kt", limit = 3)
        )

        assertEquals("app/src/ui/WorkspaceScreen.kt", results.first().entry.path)
        assertTrue(results.first().reasons.contains("current file"))
    }

    @Test
    fun languageFilterExcludesOtherLanguages() {
        val results = WorkspaceRetrievalService().retrieve(
            index,
            WorkspaceRetrievalOptions(query = "README", language = "kotlin", limit = 10)
        )

        assertTrue(results.all { it.entry.language == "kotlin" })
    }
}
