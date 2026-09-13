package com.mrredhood.nexus.workspace

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceServiceTest {
    @Test
    fun `paths cannot escape workspace`() {
        val root = Files.createTempDirectory("nexus-workspace")
        val service = WorkspaceService(root)
        assertThrows(IllegalArgumentException::class.java) { service.write("../outside.txt", "no") }
        assertThrows(IllegalArgumentException::class.java) { service.read("../../secret") }
    }

    @Test
    fun `write is readable and hash is stable`() {
        val root = Files.createTempDirectory("nexus-workspace")
        val service = WorkspaceService(root)
        service.write("src/Main.kt", "fun main() = Unit\n")
        assertEquals("fun main() = Unit\n", service.read("src/Main.kt"))
        assertEquals(64, service.sha256("src/Main.kt").length)
    }

    @Test
    fun `create refuses overwrite`() {
        val root = Files.createTempDirectory("nexus-workspace")
        val service = WorkspaceService(root)
        service.create("README.md", "one")
        assertThrows(IllegalArgumentException::class.java) { service.create("README.md", "two") }
        assertEquals("one", service.read("README.md"))
    }

    @Test
    fun `snapshot restores deleted and changed files`() {
        val root = Files.createTempDirectory("nexus-workspace")
        val snapshots = Files.createTempDirectory("nexus-snapshots")
        val service = WorkspaceService(root)
        val store = WorkspaceSnapshotStore(snapshots)

        service.write("keep.txt", "before")
        service.write("remove.txt", "present")
        val id = store.createSnapshot(root)

        service.write("keep.txt", "after")
        service.delete("remove.txt")
        service.write("new.txt", "should disappear")

        store.restoreSnapshot(id, root)
        assertEquals("before", service.read("keep.txt"))
        assertEquals("present", service.read("remove.txt"))
        assertFalse(service.exists("new.txt"))

        store.deleteSnapshot(id)
        assertTrue(Files.list(snapshots).use { !it.iterator().hasNext() })
    }
}
