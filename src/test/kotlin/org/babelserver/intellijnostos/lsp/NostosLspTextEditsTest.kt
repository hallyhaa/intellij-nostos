package org.babelserver.intellijnostos.lsp

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NostosLspTextEditsTest {

    private fun edit(startLine: Int, startChar: Int, endLine: Int, endChar: Int, newText: String) =
        TextEdit(Range(Position(startLine, startChar), Position(endLine, endChar)), newText)

    @Test
    fun noEditsMeansUnchangedText() {
        assertEquals("fn main() = 1\n", applyTextEdits("fn main() = 1\n", emptyList()))
    }

    @Test
    fun replacesWithinOneLine() {
        assertEquals("fn main() = 2", applyTextEdits("fn main() = 1", listOf(edit(0, 12, 0, 13, "2"))))
    }

    @Test
    fun insertsWhenRangeIsEmpty() {
        assertEquals("ab XYZ cd", applyTextEdits("ab cd", listOf(edit(0, 3, 0, 3, "XYZ "))))
    }

    @Test
    fun appliesMultipleEditsAgainstOriginalPositions() {
        // Both positions refer to the original text; back-to-front application
        // must keep them valid.
        val edits = listOf(
            edit(0, 0, 0, 1, "X"), // a -> X
            edit(1, 2, 1, 3, "Y"), // f -> Y
        )
        assertEquals("Xbc\ndeY\n", applyTextEdits("abc\ndef\n", edits))
    }

    @Test
    fun wholeDocumentReplacementWithNostosLspsEndPosition() {
        // nostos-lsp's full-document edit ends at {line = number of lines,
        // character = length of last line} — one line past the last for a
        // trailing-newline document. Clamping must swallow the whole text.
        val original = "fn main()   =   1\nfn other() = 2\n"
        val formatted = "fn main() = 1\nfn other() = 2\n"
        val edits = listOf(edit(0, 0, 2, 14, formatted))
        assertEquals(formatted, applyTextEdits(original, edits))
    }

    @Test
    fun positionsBeyondLineOrDocumentAreClamped() {
        // Character past the line end clamps to just before the newline.
        assertEquals("aX\nb", applyTextEdits("a\nb", listOf(edit(0, 1, 0, 99, "X"))))
        // Line past the document clamps to the end of the text.
        assertEquals("a\nX", applyTextEdits("a\nb", listOf(edit(1, 0, 99, 0, "X"))))
    }
}
