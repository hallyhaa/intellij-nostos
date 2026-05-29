package org.babelserver.intellijnostos.lsp

import java.io.File

/** Outcome of locating the nostos-lsp executable. */
internal sealed interface LspLookup {
    /** nostos-lsp was found and is executable. */
    data class Found(val path: String) : LspLookup

    /** A nostos binary exists, but no nostos-lsp executable beside it. */
    data class LspMissing(val nostosDir: String) : LspLookup

    /** No nostos installation could be found at all. */
    data object NostosMissing : LspLookup
}

/**
 * Locates the nostos-lsp executable.
 *
 * Filesystem access and settings lookup are injected so the resolution logic
 * can be tested deterministically, independent of what is installed on the
 * machine running the tests.
 *
 * @param effectiveNostosPath the configured or auto-detected path to the nostos binary
 * @param detectNostos a fresh scan of well-known locations for the nostos binary
 * @param isExecutable whether a given file exists and is executable
 */
internal class NostosExecutableResolver(
    private val effectiveNostosPath: () -> String,
    private val detectNostos: () -> String?,
    private val isExecutable: (File) -> Boolean,
) {

    fun resolve(): LspLookup {
        val nostosFile = File(effectiveNostosPath())
        if (isExecutable(nostosFile)) {
            return lspBeside(nostosFile)
        }
        // effectiveNostosPath() may have returned the literal "nostos" fallback,
        // or a configured path that no longer exists — try fresh detection.
        val detected = detectNostos()
        if (detected != null) {
            return lspBeside(File(detected))
        }
        return LspLookup.NostosMissing
    }

    /** Checks for a nostos-lsp executable in the same directory as the nostos binary. */
    private fun lspBeside(nostosFile: File): LspLookup {
        val lspFile = File(nostosFile.parentFile, "nostos-lsp")
        return if (isExecutable(lspFile)) {
            LspLookup.Found(lspFile.absolutePath)
        } else {
            LspLookup.LspMissing(nostosFile.parentFile?.absolutePath ?: nostosFile.absolutePath)
        }
    }
}
