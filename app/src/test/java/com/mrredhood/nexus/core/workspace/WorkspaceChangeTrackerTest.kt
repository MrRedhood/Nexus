package com.mrredhood.nexus.core.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceChangeTrackerTest {
    @Test
    fun lineDiffCountsInsertionAndDeletion() {
        val before = listOf("one", "two", "three")
        val after = listOf("one", "changed", "three", "four")
        val method = WorkspaceChangeTracker::class.java.getDeclaredMethod("lineDiffCounts", List::class.java, List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(WorkspaceChangeTracker::class.java.getDeclaredConstructor(WorkspaceFileSystem::class.java).newInstance(null), before, after) as Pair<Int, Int>
        assertEquals(2, result.first)
        assertEquals(1, result.second)
    }

    @Test
    fun summaryClassifiesCreatedModifiedAndDeleted() {
        val trackerClass = WorkspaceChangeTracker::class.java
        val method = trackerClass.getDeclaredMethod("buildSummary", Map::class.java, Map::class.java)
        method.isAccessible = true
        val tracker = trackerClass.getDeclaredConstructor(WorkspaceFileSystem::class.java).newInstance(null)
        val before = mapOf("old.kt" to "a\nb\n", "changed.kt" to "a\nb\n", "same.kt" to "x\n")
        val after = mapOf("new.kt" to "x\ny\n", "changed.kt" to "a\nc\nd\n", "same.kt" to "x\n")
        val summary = method.invoke(tracker, before, after) as WorkspaceChangeSummary
        assertEquals(1, summary.created)
        assertEquals(1, summary.modified)
        assertEquals(1, summary.deleted)
        assertEquals(3, summary.additions)
        assertEquals(3, summary.deletions)
    }
}
