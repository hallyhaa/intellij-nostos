package org.babelserver.intellijnostos.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NostosProjectRootTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun fallsBackToBasePathWhenNoManifestExists() {
        val root = NostosProjectRoot.choose(emptyList(), "/home/dev/proj")
        assertEquals("/home/dev/proj", root)
    }

    @Test
    fun returnsNullWhenNoManifestAndNoBasePath() {
        val root = NostosProjectRoot.choose(emptyList(), null)
        assertNull(root)
    }

    @Test
    fun usesBasePathWhenManifestSitsDirectlyInIt() {
        val root = NostosProjectRoot.choose(listOf("/home/dev/proj/nostos.toml"), "/home/dev/proj")
        assertEquals("/home/dev/proj", root)
    }

    @Test
    fun usesManifestDirectoryWhenManifestIsInSubdirectory() {
        val root = NostosProjectRoot.choose(listOf("/home/dev/proj/app/nostos.toml"), "/home/dev/proj")
        assertEquals("/home/dev/proj/app", root)
    }

    @Test
    fun prefersManifestInBasePathOverSubdirectoryManifests() {
        val root = NostosProjectRoot.choose(
            listOf("/home/dev/proj/app/nostos.toml", "/home/dev/proj/nostos.toml"),
            "/home/dev/proj",
        )
        assertEquals("/home/dev/proj", root)
    }

    @Test
    fun picksShallowestManifestWhenNoneSitInBasePath() {
        val root = NostosProjectRoot.choose(
            listOf("/home/dev/proj/a/b/nostos.toml", "/home/dev/proj/app/nostos.toml"),
            "/home/dev/proj",
        )
        assertEquals("/home/dev/proj/app", root)
    }

    @Test
    fun breaksDepthTiesDeterministicallyByPath() {
        val root = NostosProjectRoot.choose(
            listOf("/home/dev/proj/zeta/nostos.toml", "/home/dev/proj/alpha/nostos.toml"),
            "/home/dev/proj",
        )
        assertEquals("/home/dev/proj/alpha", root)
    }

    @Test
    fun findManifestsLocatesAManifestAtTheRoot() {
        val manifest = File(tempDir, "nostos.toml").apply { writeText("") }
        assertEquals(listOf(manifest.absolutePath), NostosProjectRoot.findManifests(tempDir))
    }

    @Test
    fun findManifestsLocatesAManifestInASubdirectory() {
        val src = File(tempDir, "src").apply { mkdirs() }
        val manifest = File(src, "nostos.toml").apply { writeText("") }
        assertEquals(listOf(manifest.absolutePath), NostosProjectRoot.findManifests(tempDir))
    }

    @Test
    fun findManifestsSkipsDotDirectories() {
        val hidden = File(tempDir, ".git").apply { mkdirs() }
        File(hidden, "nostos.toml").writeText("")
        assertTrue(NostosProjectRoot.findManifests(tempDir).isEmpty())
    }

    @Test
    fun findManifestsRespectsTheDepthLimit() {
        val deep = File(tempDir, "a/b/c/d").apply { mkdirs() }
        File(deep, "nostos.toml").writeText("")
        assertTrue(NostosProjectRoot.findManifests(tempDir, maxDepth = 2).isEmpty())
    }
}
