package org.babelserver.intellijnostos.lsp

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextEdit

/**
 * Applies LSP [TextEdit]s to [text]. Positions are interpreted against the
 * original text (as the protocol specifies), so the edits are applied
 * back-to-front, which keeps every earlier offset valid; LSP guarantees that
 * edits do not overlap. Positions beyond the end of a line or of the document
 * are clamped, which also covers nostos-lsp's whole-document edit whose end
 * position points one line past the last.
 */
internal fun applyTextEdits(text: String, edits: List<TextEdit>): String {
    if (edits.isEmpty()) return text

    val lineStarts = buildList {
        add(0)
        text.forEachIndexed { index, c -> if (c == '\n') add(index + 1) }
    }

    fun offsetOf(position: Position?): Int {
        if (position == null || position.line >= lineStarts.size) return text.length
        if (position.line < 0) return 0
        val lineStart = lineStarts[position.line]
        val lineEnd =
            if (position.line + 1 < lineStarts.size) lineStarts[position.line + 1] - 1 // before the newline
            else text.length
        return (lineStart + position.character.coerceAtLeast(0)).coerceAtMost(lineEnd)
    }

    val builder = StringBuilder(text)
    val backToFront = edits.sortedWith(
        compareByDescending<TextEdit> { it.range?.start?.line ?: 0 }
            .thenByDescending { it.range?.start?.character ?: 0 },
    )
    for (edit in backToFront) {
        val start = offsetOf(edit.range?.start)
        val end = offsetOf(edit.range?.end).coerceAtLeast(start)
        builder.replace(start, end, edit.newText ?: "")
    }
    return builder.toString()
}
