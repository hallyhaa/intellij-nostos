package org.babelserver.intellijnostos.lsp

import org.babelserver.intellijnostos.wizard.NostosProjectScaffold
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NostosMissingManifestNotifierTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun writesManifestWhenAbsent() {
        val manifest = File(tempDir, NostosProjectRoot.MANIFEST_NAME)
        val wrote = NostosMissingManifestNotifier.createManifestIfAbsent(manifest, "trivium")

        assertTrue(wrote)
        assertTrue(manifest.exists())
    }

    @Test
    fun writtenManifestMatchesTheScaffoldContent() {
        val manifest = File(tempDir, NostosProjectRoot.MANIFEST_NAME)
        NostosMissingManifestNotifier.createManifestIfAbsent(manifest, "trivium")

        assertEquals(NostosProjectScaffold.nostosTomlContent("trivium"), manifest.readText())
    }

    @Test
    fun usesTheGivenProjectNameInTheManifest() {
        val manifest = File(tempDir, NostosProjectRoot.MANIFEST_NAME)
        NostosMissingManifestNotifier.createManifestIfAbsent(manifest, "trivium")

        assertTrue(manifest.readText().contains("name = \"trivium\""))
    }

    @Test
    fun deriveProjectNameUsesTheGitRootDirectoryName() {
        // Layout: <tempDir>/trivium-client/.git and a nested nostos/client dir.
        val repo = File(tempDir, "trivium-client").apply { mkdirs() }
        File(repo, ".git").mkdirs()
        val target = File(repo, "nostos/client").apply { mkdirs() }

        // Even though the manifest goes in "client", the name is the repo's.
        assertEquals("trivium-client", NostosMissingManifestNotifier.deriveProjectName(target, "fallback"))
    }

    @Test
    fun deriveProjectNameHandlesAGitFileNotJustADirectory() {
        // Worktrees and submodules store .git as a file, not a directory.
        val repo = File(tempDir, "worktree-repo").apply { mkdirs() }
        File(repo, ".git").writeText("gitdir: /somewhere/else\n")
        val target = File(repo, "src").apply { mkdirs() }

        assertEquals("worktree-repo", NostosMissingManifestNotifier.deriveProjectName(target, "fallback"))
    }

    @Test
    fun deriveProjectNameFallsBackWhenNoGitRoot() {
        val target = File(tempDir, "loose/dir").apply { mkdirs() }
        assertEquals("fallback", NostosMissingManifestNotifier.deriveProjectName(target, "fallback"))
    }

    @Test
    fun doesNotOverwriteAnExistingManifest() {
        val manifest = File(tempDir, NostosProjectRoot.MANIFEST_NAME).apply {
            writeText("[project]\nname = \"hand-written\"\n")
        }

        val wrote = NostosMissingManifestNotifier.createManifestIfAbsent(manifest, "trivium")

        assertFalse(wrote)
        // The user's existing file must survive untouched.
        assertEquals("[project]\nname = \"hand-written\"\n", manifest.readText())
    }
}
