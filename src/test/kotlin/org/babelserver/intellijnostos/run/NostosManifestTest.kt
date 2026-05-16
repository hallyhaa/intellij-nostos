package org.babelserver.intellijnostos.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NostosManifestTest {

    @Test
    fun parsesNoBinsFromAManifestWithoutThem() {
        val toml = """
            [project]
            name = "demo"
            version = "0.1.0"
        """.trimIndent()
        assertTrue(NostosManifest.parseBins(toml).isEmpty())
    }

    @Test
    fun parsesASingleBinWithAllFields() {
        val toml = """
            [[bin]]
            name = "server"
            entry = "server.main"
            default = true
        """.trimIndent()
        assertEquals(
            listOf(NostosBin("server", "server.main", isDefault = true)),
            NostosManifest.parseBins(toml),
        )
    }

    @Test
    fun parsesMultipleBinsAmongOtherTables() {
        val toml = """
            [project]
            name = "demo"

            [[bin]]
            name = "server"
            entry = "server.main"
            default = true

            [extensions]
            glam = { git = "https://example.com/glam" }

            [[bin]]
            name = "cli"
            entry = "cli.main"
        """.trimIndent()
        assertEquals(
            listOf(
                NostosBin("server", "server.main", isDefault = true),
                NostosBin("cli", "cli.main", isDefault = false),
            ),
            NostosManifest.parseBins(toml),
        )
    }

    @Test
    fun ignoresCommentsIncludingFullLineAndTrailing() {
        val toml = """
            # the project's entry points
            [[bin]]
            name = "server"  # the long-running one
            entry = "server.main"
        """.trimIndent()
        assertEquals(
            listOf(NostosBin("server", "server.main", isDefault = false)),
            NostosManifest.parseBins(toml),
        )
    }

    @Test
    fun keepsHashCharactersInsideQuotedValues() {
        val toml = """
            [[bin]]
            name = "ta#g"
            entry = "main.main"
        """.trimIndent()
        assertEquals("ta#g", NostosManifest.parseBins(toml).single().name)
    }

    @Test
    fun skipsBinTablesWithoutAName() {
        val toml = """
            [[bin]]
            entry = "orphan.main"

            [[bin]]
            name = "real"
            entry = "real.main"
        """.trimIndent()
        assertEquals(listOf(NostosBin("real", "real.main", isDefault = false)), NostosManifest.parseBins(toml))
    }

    @Test
    fun toleratesWhitespaceInTableHeaders() {
        val toml = """
            [[ bin ]]
            name = "server"
        """.trimIndent()
        assertEquals("server", NostosManifest.parseBins(toml).single().name)
    }

    @Test
    fun treatsAbsentDefaultAsFalse() {
        val toml = """
            [[bin]]
            name = "server"
            entry = "server.main"
        """.trimIndent()
        assertEquals(false, NostosManifest.parseBins(toml).single().isDefault)
    }
}
