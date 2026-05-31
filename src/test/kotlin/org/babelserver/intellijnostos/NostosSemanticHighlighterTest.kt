package org.babelserver.intellijnostos

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Unit coverage for the LSP semantic-tokens relative-encoding decode. A live
 * server is not involved; this exercises the delta arithmetic and the
 * out-of-bounds guards directly via [NostosSemanticHighlighter.decodeTokens].
 */
class NostosSemanticHighlighterTest : BasePlatformTestCase() {

    private val highlighter = NostosSemanticHighlighter()

    fun testDecodesRelativePositionsToAbsoluteOffsets() {
        // "abcde\nfghij": line 0 at offset 0, line 1 at offset 6.
        val doc = myFixture.configureByText("a.nos", "abcde\nfghij").viewProvider.document!!
        // token A: line 0, char 0, len 3, type 2 (function)
        // token B: line +1, char 1, len 4, type 1 (type)
        val data = listOf(0, 0, 3, 2, 0, 1, 1, 4, 1, 0)

        val tokens = highlighter.decodeTokens(data, doc)

        assertEquals(2, tokens.size)
        assertEquals(0, tokens[0].startOffset)
        assertEquals(3, tokens[0].length)
        assertEquals(7, tokens[1].startOffset) // line 1 starts at 6, +1 char
        assertEquals(4, tokens[1].length)
    }

    fun testAccumulatesCharDeltaWithinSameLine() {
        val doc = myFixture.configureByText("a.nos", "aaaaaaaaaa").viewProvider.document!!
        // two tokens on the same line: char 0 then +4 -> char 4
        val data = listOf(0, 0, 2, 2, 0, 0, 4, 2, 2, 0)
        val tokens = highlighter.decodeTokens(data, doc)
        assertEquals(2, tokens.size)
        assertEquals(0, tokens[0].startOffset)
        assertEquals(4, tokens[1].startOffset)
    }

    fun testSkipsTokenRunningPastDocumentEnd() {
        val doc = myFixture.configureByText("a.nos", "ab").viewProvider.document!!
        // length 5 at offset 0 -> endOffset 5 > textLength 2 -> skipped
        assertTrue(highlighter.decodeTokens(listOf(0, 0, 5, 2, 0), doc).isEmpty())
    }

    fun testSkipsUnmappedTokenType() {
        val doc = myFixture.configureByText("a.nos", "abcde").viewProvider.document!!
        // type 3 (variable) is deliberately not mapped -> no token emitted
        assertTrue(highlighter.decodeTokens(listOf(0, 0, 3, 3, 0), doc).isEmpty())
    }

    fun testIgnoresTruncatedTrailingTuple() {
        val doc = myFixture.configureByText("a.nos", "abcde").viewProvider.document!!
        // one full 5-tuple plus a 3-int tail that is not a complete token
        val tokens = highlighter.decodeTokens(listOf(0, 0, 3, 2, 0, 0, 3, 2), doc)
        assertEquals(1, tokens.size)
    }

    fun testEmptyDataYieldsNoTokens() {
        val doc = myFixture.configureByText("a.nos", "abcde").viewProvider.document!!
        assertTrue(highlighter.decodeTokens(emptyList(), doc).isEmpty())
    }
}
