package org.babelserver.intellijnostos.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NostosLspStderrLevelTest {

    @Test
    fun parsesLevelsFromEnvLoggerLines() {
        assertEquals("INFO", stderrLevel("[2026-08-30T13:30:43Z INFO  nostos_lsp::server] initialized() notification received"))
        assertEquals("WARN", stderrLevel("[2026-08-30T13:30:25Z WARN  nostos_lsp::server] Engine not ready after 10s, skipping check_file"))
        assertEquals("ERROR", stderrLevel("[2026-08-30T13:30:25Z ERROR nostos_lsp] boom"))
        assertEquals("DEBUG", stderrLevel("[2026-08-30T13:30:25Z DEBUG nostos_lsp] details"))
        assertEquals("TRACE", stderrLevel("[2026-08-30T13:30:25Z TRACE nostos_lsp] noise"))
    }

    @Test
    fun linesWithoutALevelHaveNone() {
        // Panic/crash output must fall through to the WARN default.
        assertNull(stderrLevel("thread 'main' panicked at src/main.rs:10:5:"))
        assertNull(stderrLevel("stack backtrace:"))
        assertNull(stderrLevel(""))
        // A level mentioned elsewhere in the line is not a level prefix.
        assertNull(stderrLevel("note: INFO is not a prefix here"))
    }
}
