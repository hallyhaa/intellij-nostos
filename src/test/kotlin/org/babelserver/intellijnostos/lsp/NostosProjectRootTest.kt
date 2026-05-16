package org.babelserver.intellijnostos.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NostosProjectRootTest {

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
}
