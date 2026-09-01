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

    @Test
    fun routingFollowsTheDeclaredLevel() {
        assertEquals(StderrRouting.DEBUG, stderrRouting("[2026-09-01T06:37:59Z TRACE nostos_lsp] noise"))
        assertEquals(StderrRouting.DEBUG, stderrRouting("[2026-09-01T06:37:59Z DEBUG nostos_lsp] details"))
        assertEquals(StderrRouting.INFO, stderrRouting("[2026-09-01T06:37:59Z INFO  nostos_lsp::server] initialized()"))
        assertEquals(StderrRouting.WARN, stderrRouting("[2026-09-01T06:37:59Z WARN  nostos_lsp::server] Engine not ready"))
        assertEquals(StderrRouting.WARN, stderrRouting("[2026-09-01T06:37:59Z ERROR nostos_lsp] boom"))
    }

    @Test
    fun enginesInformalProgressPrintsAreInfo() {
        assertEquals(
            StderrRouting.INFO,
            stderrRouting("LSP: Removing old version before update: models.cell_col"),
        )
    }

    @Test
    fun unclassifiedLinesStayVisibleAsWarnings() {
        assertEquals(StderrRouting.WARN, stderrRouting("thread 'main' panicked at src/main.rs:10:5:"))
        assertEquals(StderrRouting.WARN, stderrRouting("stack backtrace:"))
        assertEquals(StderrRouting.WARN, stderrRouting(""))
        // "LSP: " must be a prefix, not a substring, to count as routine.
        assertEquals(StderrRouting.WARN, stderrRouting("note: LSP: is not a prefix here"))
    }
}
