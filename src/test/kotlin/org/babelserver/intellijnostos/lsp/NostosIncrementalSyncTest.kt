package org.babelserver.intellijnostos.lsp

import org.eclipse.lsp4j.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The incremental-sync range arithmetic: given an edit's start position and the
 * text it removed, where does the removed range end (in the pre-edit document)?
 */
class NostosIncrementalSyncTest {

    private fun end(line: Int, char: Int, removed: String): Position =
        NostosLspServerManager.changeEndPosition(Position(line, char), removed)

    @Test
    fun pureInsertionHasZeroWidthRange() {
        // Nothing removed -> end == start.
        assertEquals(Position(2, 5), end(2, 5, ""))
    }

    @Test
    fun singleLineRemovalAdvancesColumn() {
        // Removed "abc" at (2,5) -> ends at (2,8).
        assertEquals(Position(2, 8), end(2, 5, "abc"))
    }

    @Test
    fun multiLineRemovalMovesDownAndResetsColumn() {
        // Removed "ab\ncd" starting at (1,3): one newline -> end line 2, and the
        // column is the length after the last newline ("cd" -> 2).
        assertEquals(Position(2, 2), end(1, 3, "ab\ncd"))
    }

    @Test
    fun removalEndingInNewlineLandsAtColumnZero() {
        // "ab\n" -> last newline is the final char, so end column is 0 on the
        // next line.
        assertEquals(Position(6, 0), end(5, 4, "ab\n"))
    }

    @Test
    fun removalSpanningMultipleNewlines() {
        // "x\ny\nz" has two newlines -> down two lines, final segment "z" len 1.
        assertEquals(Position(12, 1), end(10, 7, "x\ny\nz"))
    }
}
