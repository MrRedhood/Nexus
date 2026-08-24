package com.mrredhood.nexus.core.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceChangeTrackerTest {
    @Test
    fun summaryCountsCreatedModifiedAndDeletedFiles() {
        val summary = WorkspaceChangeSummary(
            changes = listOf(
                WorkspaceChange("new.kt", WorkspaceChangeStatus.CREATED, 4, 0),
                WorkspaceChange("changed.kt", WorkspaceChangeStatus.MODIFIED, 3, 2),
                WorkspaceChange("old.kt", WorkspaceChangeStatus.DELETED, 0, 5)
            )
        )
        assertEquals(1, summary.created)
        assertEquals(1, summary.modified)
        assertEquals(1, summary.deleted)
        assertEquals(7, summary.additions)
        assertEquals(7, summary.deletions)
    }
}
