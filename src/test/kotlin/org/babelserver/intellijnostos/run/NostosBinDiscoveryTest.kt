package org.babelserver.intellijnostos.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NostosBinDiscoveryTest {

    @TempDir
    lateinit var projectDir: File

    private fun manifest(content: String) {
        File(projectDir, "nostos.toml").writeText(content)
    }

    private fun touch(relativePath: String): File {
        val file = File(projectDir, relativePath)
        file.parentFile.mkdirs()
        file.writeText("")
        return file
    }

    @Test
    fun findsTheProjectRootFromANestedFile() {
        manifest("[project]\nname = \"demo\"\n")
        val file = touch("sub/app.nos")
        assertEquals(projectDir.canonicalFile, NostosBinDiscovery.projectRoot(file)?.canonicalFile)
    }

    @Test
    fun returnsNullWhenNoManifestExistsAbove() {
        val file = touch("loose.nos")
        assertNull(NostosBinDiscovery.projectRoot(file))
    }

    @Test
    fun readsBinsForAFileInTheProject() {
        manifest(
            """
            [[bin]]
            name = "server"
            entry = "server.main"
            default = true
            """.trimIndent()
        )
        val bins = NostosBinDiscovery.binsFor(touch("main.nos"))
        assertEquals(listOf(NostosBin("server", "server.main", isDefault = true)), bins)
    }

    @Test
    fun defaultBinPrefersTheDefaultFlagThenASoleBin() {
        assertEquals(
            "b",
            NostosBinDiscovery.defaultBin(
                listOf(NostosBin("a", "a.main", false), NostosBin("b", "b.main", true))
            )?.name,
        )
        assertEquals(
            "only",
            NostosBinDiscovery.defaultBin(listOf(NostosBin("only", "only.main", false)))?.name,
        )
        assertNull(
            NostosBinDiscovery.defaultBin(
                listOf(NostosBin("a", "a.main", false), NostosBin("b", "b.main", false))
            )
        )
        assertNull(NostosBinDiscovery.defaultBin(emptyList()))
    }

    @Test
    fun derivesModuleNameFromPathUnderTheProjectRoot() {
        manifest("[project]\n")
        assertEquals("server", NostosBinDiscovery.moduleOf(touch("server.nos")))
        assertEquals("utils.math", NostosBinDiscovery.moduleOf(touch("utils/math.nos")))
    }

    @Test
    fun binsInModuleMatchesOnlyEntriesHostedInTheGivenFile() {
        manifest(
            """
            [[bin]]
            name = "server"
            entry = "server.main"

            [[bin]]
            name = "cli"
            entry = "cli.main"
            """.trimIndent()
        )
        assertEquals(listOf("server"), NostosBinDiscovery.binsInModule(touch("server.nos")).map { it.name })
    }
}
