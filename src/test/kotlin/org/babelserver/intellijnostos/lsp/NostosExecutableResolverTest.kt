package org.babelserver.intellijnostos.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class NostosExecutableResolverTest {

    /** Builds a resolver whose filesystem view is exactly [executables]. */
    private fun resolver(
        effective: String,
        detected: String? = null,
        executables: Set<String> = emptySet(),
    ) = NostosExecutableResolver(
        effectiveNostosPath = { effective },
        detectNostos = { detected },
        isExecutable = { it.path in executables },
    )

    @Test
    fun foundWhenNostosLspSitsBesideNostos() {
        val result = resolver(
            effective = "/opt/nostos/bin/nostos",
            executables = setOf("/opt/nostos/bin/nostos", "/opt/nostos/bin/nostos-lsp"),
        ).resolve()
        assertEquals(LspLookup.Found("/opt/nostos/bin/nostos-lsp"), result)
    }

    @Test
    fun lspMissingWhenNostosExistsButNostosLspDoesNot() {
        val result = resolver(
            effective = "/opt/nostos/bin/nostos",
            executables = setOf("/opt/nostos/bin/nostos"),
        ).resolve()
        assertEquals(LspLookup.LspMissing("/opt/nostos/bin"), result)
    }

    @Test
    fun nostosMissingWhenNothingIsExecutableAndDetectionFails() {
        val result = resolver(
            effective = "nostos",
            detected = null,
            executables = emptySet(),
        ).resolve()
        assertEquals(LspLookup.NostosMissing, result)
    }

    @Test
    fun fallsBackToDetectionWhenEffectivePathIsNotExecutable() {
        val result = resolver(
            effective = "nostos",
            detected = "/usr/local/bin/nostos",
            executables = setOf("/usr/local/bin/nostos", "/usr/local/bin/nostos-lsp"),
        ).resolve()
        assertEquals(LspLookup.Found("/usr/local/bin/nostos-lsp"), result)
    }

    @Test
    fun detectionPathCanAlsoYieldLspMissing() {
        val result = resolver(
            effective = "nostos",
            detected = "/usr/local/bin/nostos",
            executables = setOf("/usr/local/bin/nostos"),
        ).resolve()
        assertEquals(LspLookup.LspMissing("/usr/local/bin"), result)
    }

    @Test
    fun nostosMissingWhenConfiguredPathIsBrokenAndDetectionFails() {
        val result = resolver(
            effective = "/no/longer/here/nostos",
            detected = null,
            executables = emptySet(),
        ).resolve()
        assertEquals(LspLookup.NostosMissing, result)
    }
}
