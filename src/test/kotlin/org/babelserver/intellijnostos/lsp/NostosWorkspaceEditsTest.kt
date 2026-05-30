package org.babelserver.intellijnostos.lsp

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Unit coverage for the shared LSP position→offset conversion used by every
 * feature that maps server ranges onto IDEA documents. A live server is not
 * involved; this guards the boundary arithmetic only.
 */
class NostosWorkspaceEditsTest : BasePlatformTestCase() {

    fun testOffsetWithinLine() {
        val doc = myFixture.configureByText("a.nos", "abcde\nfghij").viewProvider.document!!
        assertEquals(2, NostosWorkspaceEdits.lspPositionToOffset(doc, 0, 2))
        // line 1 starts at offset 6 ("abcde\n")
        assertEquals(6 + 3, NostosWorkspaceEdits.lspPositionToOffset(doc, 1, 3))
    }

    fun testCharacterPastLineEndClampsToLineEnd() {
        val doc = myFixture.configureByText("a.nos", "ab\ncd").viewProvider.document!!
        // line 0 is "ab"; asking for character 99 must clamp to the line end (offset 2)
        assertEquals(2, NostosWorkspaceEdits.lspPositionToOffset(doc, 0, 99))
    }

    fun testNegativeAndOutOfRangeLines() {
        val doc = myFixture.configureByText("a.nos", "ab\ncd").viewProvider.document!!
        assertEquals(-1, NostosWorkspaceEdits.lspPositionToOffset(doc, -1, 0))
        assertEquals(-1, NostosWorkspaceEdits.lspPositionToOffset(doc, 99, 0))
    }
}
