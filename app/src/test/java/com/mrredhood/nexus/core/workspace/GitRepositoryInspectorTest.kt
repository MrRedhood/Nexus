package com.mrredhood.nexus.core.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitRepositoryInspectorTest {
    @Test
    fun parsesBranchHead() {
        val (branch, commit) = GitTextParser.parseHead("ref: refs/heads/main\n")
        assertEquals("main", branch)
        assertNull(commit)
    }

    @Test
    fun parsesDetachedHead() {
        val (branch, commit) = GitTextParser.parseHead("4f3a91c2e8d0aa11\n")
        assertNull(branch)
        assertEquals("4f3a91c2e8d0aa11", commit)
    }

    @Test
    fun parsesOriginRemote() {
        val config = """
            [core]
                repositoryformatversion = 0
            [remote "origin"]
                url = https://github.com/example/project.git
                fetch = +refs/heads/*:refs/remotes/origin/*
        """.trimIndent()

        val (name, url) = GitTextParser.parseRemoteConfig(config)
        assertEquals("origin", name)
        assertEquals("https://github.com/example/project.git", url)
    }

    @Test
    fun prefersOriginButFallsBackToFirstRemote() {
        val config = """
            [remote "upstream"]
                url = https://github.com/example/upstream.git
            [remote "backup"]
                url = https://github.com/example/backup.git
        """.trimIndent()

        val (name, url) = GitTextParser.parseRemoteConfig(config)
        assertEquals("upstream", name)
        assertEquals("https://github.com/example/upstream.git", url)
        assertTrue(name != "origin")
    }
}
