package org.babelserver.intellijnostos

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NostosLexerTest {

    private fun tokenizeAll(input: String): List<Pair<IElementType, String>> {
        val lexer = NostosLexerAdapter()
        val tokens = mutableListOf<Pair<IElementType, String>>()
        lexer.start(input, 0, input.length, 0)
        while (lexer.tokenType != null) {
            val type = lexer.tokenType!!
            val text = input.substring(lexer.tokenStart, lexer.tokenEnd)
            tokens.add(type to text)
            lexer.advance()
        }
        return tokens
    }

    private fun tokenize(input: String): List<Pair<IElementType, String>> =
        tokenizeAll(input).filter { it.first != TokenType.WHITE_SPACE }

    private fun tokenTypes(input: String): List<IElementType> =
        tokenize(input).map { it.first }

    private fun tokenTexts(input: String): List<String> =
        tokenize(input).map { it.second }

    private fun singleToken(input: String): Pair<IElementType, String> {
        val tokens = tokenizeAll(input)
        assertEquals(1, tokens.size, "Expected single token for: $input")
        return tokens[0]
    }

    // ==================== Empty input ====================

    @Test
    fun emptyInput() {
        val lexer = NostosLexerAdapter()
        lexer.start("", 0, 0, 0)
        assertNull(lexer.tokenType)
    }

    // ==================== Line comments ====================

    @Test
    fun lineComment() {
        val (type, text) = singleToken("# a comment")
        assertEquals(NostosTokenTypes.COMMENT, type)
        assertEquals("# a comment", text)
    }

    @Test
    fun lineCommentEmpty() {
        val (type, _) = singleToken("#")
        assertEquals(NostosTokenTypes.COMMENT, type)
    }

    @Test
    fun lineCommentStopsAtNewline() {
        val tokens = tokenizeAll("# comment\ncode")
        assertEquals(NostosTokenTypes.COMMENT, tokens[0].first)
        assertEquals("# comment", tokens[0].second)
        assertEquals(TokenType.WHITE_SPACE, tokens[1].first)
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[2].first)
        assertEquals("code", tokens[2].second)
    }

    @Test
    fun documentationComment() {
        val (type, text) = singleToken("## doc comment")
        assertEquals(NostosTokenTypes.COMMENT, type)
        assertEquals("## doc comment", text)
    }

    // ==================== Block comments ====================

    @Test
    fun blockComment() {
        val (type, text) = singleToken("#* block *#")
        assertEquals(NostosTokenTypes.BLOCK_COMMENT, type)
        assertEquals("#* block *#", text)
    }

    @Test
    fun blockCommentMultiline() {
        val input = "#* line1\nline2\nline3 *#"
        val (type, text) = singleToken(input)
        assertEquals(NostosTokenTypes.BLOCK_COMMENT, type)
        assertEquals(input, text)
    }

    @Test
    fun blockCommentWithHashInside() {
        val (type, text) = singleToken("#* has # inside *#")
        assertEquals(NostosTokenTypes.BLOCK_COMMENT, type)
        assertEquals("#* has # inside *#", text)
    }

    @Test
    fun blockCommentUnclosed() {
        val (type, _) = singleToken("#* unclosed")
        assertEquals(NostosTokenTypes.BLOCK_COMMENT, type)
    }

    // ==================== Strings ====================

    @Test
    fun simpleString() {
        val (type, text) = singleToken("\"hello\"")
        assertEquals(NostosTokenTypes.STRING, type)
        assertEquals("\"hello\"", text)
    }

    @Test
    fun emptyString() {
        val (type, text) = singleToken("\"\"")
        assertEquals(NostosTokenTypes.STRING, type)
        assertEquals("\"\"", text)
    }

    @Test
    fun stringWithEscapes() {
        val (type, text) = singleToken("\"hello\\nworld\"")
        assertEquals(NostosTokenTypes.STRING, type)
        assertEquals("\"hello\\nworld\"", text)
    }

    @Test
    fun stringWithEscapedQuote() {
        val (type, text) = singleToken("\"say \\\"hi\\\"\"")
        assertEquals(NostosTokenTypes.STRING, type)
        assertEquals("\"say \\\"hi\\\"\"", text)
    }

    @Test
    fun stringWithEscapedBackslash() {
        val (type, text) = singleToken("\"path\\\\dir\"")
        assertEquals(NostosTokenTypes.STRING, type)
        assertEquals("\"path\\\\dir\"", text)
    }

    @Test
    fun stringWithInterpolation() {
        val (type, text) = singleToken("\"value: \${x + 1}\"")
        assertEquals(NostosTokenTypes.STRING, type)
        assertEquals("\"value: \${x + 1}\"", text)
    }

    @Test
    fun unclosedString() {
        val (type, _) = singleToken("\"unclosed")
        assertEquals(NostosTokenTypes.STRING, type)
    }

    // ==================== Characters ====================

    @Test
    fun simpleChar() {
        val (type, text) = singleToken("'a'")
        assertEquals(NostosTokenTypes.CHAR, type)
        assertEquals("'a'", text)
    }

    @Test
    fun escapedChar() {
        val (type, text) = singleToken("'\\n'")
        assertEquals(NostosTokenTypes.CHAR, type)
        assertEquals("'\\n'", text)
    }

    // ==================== Numbers ====================

    @Test
    fun integer() {
        val (type, text) = singleToken("42")
        assertEquals(NostosTokenTypes.NUMBER, type)
        assertEquals("42", text)
    }

    @Test
    fun zero() {
        val (type, text) = singleToken("0")
        assertEquals(NostosTokenTypes.NUMBER, type)
        assertEquals("0", text)
    }

    @Test
    fun `negative number is two tokens`() {
        val tokens = tokenize("-17")
        assertEquals(2, tokens.size)
        assertEquals(NostosTokenTypes.OPERATOR, tokens[0].first)
        assertEquals("-", tokens[0].second)
        assertEquals(NostosTokenTypes.NUMBER, tokens[1].first)
        assertEquals("17", tokens[1].second)
    }

    @Test
    fun integerWithUnderscores() {
        val (type, text) = singleToken("1_000_000")
        assertEquals(NostosTokenTypes.NUMBER, type)
        assertEquals("1_000_000", text)
    }

    @Test
    fun hexInteger() {
        val (type, text) = singleToken("0xFF")
        assertEquals(NostosTokenTypes.NUMBER, type)
        assertEquals("0xFF", text)
    }

    @Test
    fun hexIntegerUpperCase() {
        val (type, text) = singleToken("0XAB")
        assertEquals(NostosTokenTypes.NUMBER, type)
        assertEquals("0XAB", text)
    }

    @Test
    fun binaryInteger() {
        val (type, text) = singleToken("0b1010")
        assertEquals(NostosTokenTypes.NUMBER, type)
        assertEquals("0b1010", text)
    }

    @Test
    fun binaryIntegerUpperCase() {
        val (type, text) = singleToken("0B1100")
        assertEquals(NostosTokenTypes.NUMBER, type)
        assertEquals("0B1100", text)
    }

    @Test
    fun floatNumber() {
        val (type, text) = singleToken("3.14")
        assertEquals(NostosTokenTypes.NUMBER, type)
        assertEquals("3.14", text)
    }

    @Test
    fun floatWithExponent() {
        val (type, text) = singleToken("1.2e10")
        assertEquals(NostosTokenTypes.NUMBER, type)
        assertEquals("1.2e10", text)
    }

    @Test
    fun floatWithNegativeExponent() {
        val (type, text) = singleToken("1.2e-10")
        assertEquals(NostosTokenTypes.NUMBER, type)
        assertEquals("1.2e-10", text)
    }

    @Test
    fun floatWithPositiveExponent() {
        val (type, text) = singleToken("5E+3")
        assertEquals(NostosTokenTypes.NUMBER, type)
        assertEquals("5E+3", text)
    }

    @Test
    fun decimalNumber() {
        val (type, text) = singleToken("0.1d")
        assertEquals(NostosTokenTypes.NUMBER, type)
        assertEquals("0.1d", text)
    }

    @Test
    fun integerFollowedByDotIdentifier() {
        // 42.foo should be number "42" then operator "." then identifier "foo"
        val tokens = tokenize("42.foo")
        assertEquals(NostosTokenTypes.NUMBER, tokens[0].first)
        assertEquals("42", tokens[0].second)
    }

    // ==================== Keywords ====================

    @Test
    fun allKeywords() {
        val expectedKeywords = listOf(
            "if", "then", "else", "match", "with", "type", "trait", "end",
            "use", "pub", "private", "module", "import", "var", "mvar",
            "const", "for", "to", "while", "do", "break", "continue",
            "return", "spawn", "spawn_link", "spawn_monitor", "receive",
            "after", "try", "catch", "finally", "throw", "panic", "when",
            "true", "false", "self", "Self", "reactive", "deriving",
            "where", "forall", "extern", "test", "quote", "from"
        )
        for (kw in expectedKeywords) {
            val (type, text) = singleToken(kw)
            assertEquals(NostosTokenTypes.KEYWORD, type, "Expected KEYWORD for: $kw")
            assertEquals(kw, text)
        }
    }

    @Test
    fun keywordPrefix() {
        // "ifx" should be an identifier, not keyword "if" + "x"
        val (type, text) = singleToken("ifx")
        assertEquals(NostosTokenTypes.IDENTIFIER, type)
        assertEquals("ifx", text)
    }

    @Test
    fun keywordSuffix() {
        val (type, text) = singleToken("types")
        assertEquals(NostosTokenTypes.IDENTIFIER, type)
        assertEquals("types", text)
    }

    // ==================== Identifiers ====================

    @Test
    fun simpleIdentifier() {
        val (type, text) = singleToken("foo")
        assertEquals(NostosTokenTypes.IDENTIFIER, type)
        assertEquals("foo", text)
    }

    @Test
    fun identifierWithUnderscore() {
        val (type, text) = singleToken("my_var")
        assertEquals(NostosTokenTypes.IDENTIFIER, type)
        assertEquals("my_var", text)
    }

    @Test
    fun identifierStartingWithUnderscore() {
        val (type, text) = singleToken("_unused")
        assertEquals(NostosTokenTypes.IDENTIFIER, type)
        assertEquals("_unused", text)
    }

    @Test
    fun identifierWithDigits() {
        val (type, text) = singleToken("x2")
        assertEquals(NostosTokenTypes.IDENTIFIER, type)
        assertEquals("x2", text)
    }

    @Test
    fun singleUnderscore() {
        val (type, text) = singleToken("_")
        assertEquals(NostosTokenTypes.IDENTIFIER, type)
        assertEquals("_", text)
    }

    // ==================== Type names ====================

    @Test
    fun typeName() {
        val (type, text) = singleToken("Point")
        assertEquals(NostosTokenTypes.TYPE_NAME, type)
        assertEquals("Point", text)
    }

    @Test
    fun typeNameSingleLetter() {
        val (type, text) = singleToken("T")
        assertEquals(NostosTokenTypes.TYPE_NAME, type)
        assertEquals("T", text)
    }

    @Test
    fun typeNameWithDigits() {
        val (type, text) = singleToken("Vec3")
        assertEquals(NostosTokenTypes.TYPE_NAME, type)
        assertEquals("Vec3", text)
    }

    @Test
    fun typeNameComplex() {
        val (type, _) = singleToken("Int64Array")
        assertEquals(NostosTokenTypes.TYPE_NAME, type)
    }

    @Test
    fun selfIsKeywordNotType() {
        val (type, _) = singleToken("Self")
        assertEquals(NostosTokenTypes.KEYWORD, type)
    }

    // ==================== Function names ====================

    @Test
    fun functionCall() {
        val tokens = tokenize("foo(x)")
        assertEquals(NostosTokenTypes.FUNCTION_NAME, tokens[0].first)
        assertEquals("foo", tokens[0].second)
    }

    @Test
    fun functionCallWithSpace() {
        val tokens = tokenize("foo (x)")
        assertEquals(NostosTokenTypes.FUNCTION_NAME, tokens[0].first)
        assertEquals("foo", tokens[0].second)
    }

    @Test
    fun functionCallMultipleSpaces() {
        val tokens = tokenize("foo   (x)")
        assertEquals(NostosTokenTypes.FUNCTION_NAME, tokens[0].first)
        assertEquals("foo", tokens[0].second)
    }

    @Test
    fun identifierNotFollowedByParen() {
        val tokens = tokenize("foo + bar")
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[0].first)
        assertEquals("foo", tokens[0].second)
    }

    @Test
    fun identifierAtEndOfInput() {
        val (type, _) = singleToken("foo")
        assertEquals(NostosTokenTypes.IDENTIFIER, type)
    }

    @Test
    fun typeNameWithParenIsStillType() {
        // Point(x, y) — "Point" starts with uppercase, so TYPE_NAME
        val tokens = tokenize("Point(x)")
        assertEquals(NostosTokenTypes.TYPE_NAME, tokens[0].first)
    }

    @Test
    fun keywordFollowedByParenIsKeyword() {
        val tokens = tokenize("if(x)")
        assertEquals(NostosTokenTypes.KEYWORD, tokens[0].first)
    }

    // ==================== Operators ====================

    @Test
    fun singleCharOperators() {
        val ops = listOf("+", "-", "*", "/", "%", "=", "<", ">", "!", ".", "|")
        for (op in ops) {
            val (type, text) = singleToken(op)
            assertEquals(NostosTokenTypes.OPERATOR, type, "Expected OPERATOR for: $op")
            assertEquals(op, text)
        }
    }

    @Test
    fun twoCharOperators() {
        val ops = listOf(
            "++", "::", "->", "<-", "<=", ">=",
            "==", "!=", "&&", "||", "**", "+="
        )
        for (op in ops) {
            val (type, text) = singleToken(op)
            assertEquals(NostosTokenTypes.OPERATOR, type, "Expected OPERATOR for: $op")
            assertEquals(op, text)
        }
    }

    @Test
    fun arrowNotConfusedWithLessThan() {
        val tokens = tokenize("<-")
        assertEquals(1, tokens.size)
        assertEquals("<-", tokens[0].second)
    }

    @Test
    fun parenBracketBrace() {
        val tokens = tokenize("()[]{}")
        assertEquals(6, tokens.size)
        tokens.forEach { assertEquals(NostosTokenTypes.OPERATOR, it.first) }
    }

    // ==================== Whitespace ====================

    @Test
    fun whitespaceSpaces() {
        val tokens = tokenizeAll("   ")
        assertEquals(1, tokens.size)
        assertEquals(TokenType.WHITE_SPACE, tokens[0].first)
        assertEquals("   ", tokens[0].second)
    }

    @Test
    fun whitespaceNewlines() {
        val tokens = tokenizeAll("\n\n")
        assertEquals(1, tokens.size)
        assertEquals(TokenType.WHITE_SPACE, tokens[0].first)
    }

    @Test
    fun whitespaceTab() {
        val tokens = tokenizeAll("\t")
        assertEquals(1, tokens.size)
        assertEquals(TokenType.WHITE_SPACE, tokens[0].first)
    }

    // ==================== Combined / integration ====================

    @Test
    fun functionDefinition() {
        val tokens = tokenize("double(x) = x * 2")
        assertEquals(NostosTokenTypes.FUNCTION_NAME, tokens[0].first) // double
        assertEquals(NostosTokenTypes.OPERATOR, tokens[1].first)      // (
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[2].first)    // x
        assertEquals(NostosTokenTypes.OPERATOR, tokens[3].first)      // )
        assertEquals(NostosTokenTypes.OPERATOR, tokens[4].first)      // =
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[5].first)    // x
        assertEquals(NostosTokenTypes.OPERATOR, tokens[6].first)      // *
        assertEquals(NostosTokenTypes.NUMBER, tokens[7].first)        // 2
    }

    @Test
    fun typeDefinition() {
        val tokens = tokenize("type Point = { x: Float, y: Float }")
        assertEquals(NostosTokenTypes.KEYWORD, tokens[0].first)    // type
        assertEquals(NostosTokenTypes.TYPE_NAME, tokens[1].first)  // Point
    }

    @Test
    fun traitDefinition() {
        // trait Animal\n    speak(self) -> String\nend
        val tokens = tokenize("trait Animal\n    speak(self) -> String\nend")
        assertEquals(NostosTokenTypes.KEYWORD, tokens[0].first)       // trait
        assertEquals("trait", tokens[0].second)
        assertEquals(NostosTokenTypes.TYPE_NAME, tokens[1].first)     // Animal
        assertEquals("Animal", tokens[1].second)
        assertEquals(NostosTokenTypes.FUNCTION_NAME, tokens[2].first) // speak
        assertEquals("speak", tokens[2].second)
        assertEquals(NostosTokenTypes.KEYWORD, tokens.last().first)   // end
        assertEquals("end", tokens.last().second)
    }

    @Test
    fun patternMatchingFunction() {
        val tokens = tokenize("fib(0) = 0")
        assertEquals(NostosTokenTypes.FUNCTION_NAME, tokens[0].first)
        assertEquals("fib", tokens[0].second)
    }

    @Test
    fun lambdaExpression() {
        val tokens = tokenize("x => x * 2")
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[0].first) // x
        assertEquals(NostosTokenTypes.OPERATOR, tokens[1].first)   // =>
        assertEquals("=>", tokens[1].second)
    }

    @Test
    fun listLiteral() {
        // [1, 2, 3] without whitespace: [, 1, ,, 2, ,, 3, ]
        val tokens = tokenize("[1, 2, 3]")
        assertEquals(NostosTokenTypes.OPERATOR, tokens[0].first)  // [
        assertEquals(NostosTokenTypes.NUMBER, tokens[1].first)    // 1
        assertEquals(NostosTokenTypes.OPERATOR, tokens[2].first)  // ,
        assertEquals(NostosTokenTypes.NUMBER, tokens[3].first)    // 2
        assertEquals(NostosTokenTypes.OPERATOR, tokens[4].first)  // ,
        assertEquals(NostosTokenTypes.NUMBER, tokens[5].first)    // 3
        assertEquals(NostosTokenTypes.OPERATOR, tokens[6].first)  // ]
    }

    @Test
    fun mapLiteral() {
        val tokens = tokenize("%{\"key\": 42}")
        assertEquals(NostosTokenTypes.OPERATOR, tokens[0].first)  // %
        assertEquals(NostosTokenTypes.OPERATOR, tokens[1].first)  // {
        assertEquals(NostosTokenTypes.STRING, tokens[2].first)    // "key"
    }

    @Test
    fun setLiteral() {
        val tokens = tokenize("#{1, 2}")
        assertEquals(NostosTokenTypes.COMMENT, tokens[0].first) // # starts comment
        // This is actually a known limitation — #{} looks like a comment
    }

    @Test
    fun messageSend() {
        val tokens = tokenize("pid <- msg")
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[0].first)  // pid
        assertEquals(NostosTokenTypes.OPERATOR, tokens[1].first)    // <-
        assertEquals("<-", tokens[1].second)
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[2].first)  // msg
    }

    @Test
    fun methodChain() {
        // list.map(f).filter(p) -> list, ., map, (, f, ), ., filter, (, p, )
        val tokens = tokenize("list.map(f).filter(p)")
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[0].first)    // list
        assertEquals(NostosTokenTypes.OPERATOR, tokens[1].first)      // .
        assertEquals(NostosTokenTypes.FUNCTION_NAME, tokens[2].first) // map
        assertEquals(NostosTokenTypes.OPERATOR, tokens[3].first)      // (
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[4].first)    // f
        assertEquals(NostosTokenTypes.OPERATOR, tokens[5].first)      // )
        assertEquals(NostosTokenTypes.OPERATOR, tokens[6].first)      // .
        assertEquals(NostosTokenTypes.FUNCTION_NAME, tokens[7].first) // filter
    }

    @Test
    fun commentFollowedByCode() {
        val tokens = tokenize("# comment\nx = 5")
        assertEquals(NostosTokenTypes.COMMENT, tokens[0].first)
        assertEquals("# comment", tokens[0].second)
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[1].first)
        assertEquals("x", tokens[1].second)
    }

    @Test
    fun stringFollowedByOperator() {
        val tokens = tokenize("\"hello\" ++ \"world\"")
        assertEquals(NostosTokenTypes.STRING, tokens[0].first)
        assertEquals(NostosTokenTypes.OPERATOR, tokens[1].first)
        assertEquals("++", tokens[1].second)
        assertEquals(NostosTokenTypes.STRING, tokens[2].first)
    }

    @Test
    fun consOperator() {
        val tokens = tokenize("[h | t]")
        assertEquals(NostosTokenTypes.OPERATOR, tokens[0].first)   // [
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[1].first) // h
        assertEquals(NostosTokenTypes.OPERATOR, tokens[2].first)   // |
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[3].first) // t
        assertEquals(NostosTokenTypes.OPERATOR, tokens[4].first)   // ]
    }

    @Test
    fun receiveBlock() {
        val input = "receive {\n    Ping(sender) -> sender <- Pong\n}"
        val tokens = tokenize(input)
        assertEquals(NostosTokenTypes.KEYWORD, tokens[0].first)     // receive
        assertEquals("receive", tokens[0].second)
        assertEquals(NostosTokenTypes.OPERATOR, tokens[1].first)    // {
        assertEquals(NostosTokenTypes.TYPE_NAME, tokens[2].first)   // Ping
        assertEquals("Ping", tokens[2].second)
    }

    @Test
    fun spawnBlock() {
        val tokens = tokenize("spawn { worker() }")
        assertEquals(NostosTokenTypes.KEYWORD, tokens[0].first)       // spawn
        assertEquals(NostosTokenTypes.OPERATOR, tokens[1].first)      // {
        assertEquals(NostosTokenTypes.FUNCTION_NAME, tokens[2].first) // worker
    }

    @Test
    fun fullCoverage() {
        // Lex a realistic snippet and verify no tokens are lost
        val input = """
            type Shape = Circle(Float) | Rectangle(Float, Float)
            area(Circle(r)) = 3.14 * r * r
            area(Rectangle(w, h)) = w * h
            main() = {
                s = Circle(5.0)
                println("Area: " ++ show(area(s)))
            }
        """.trimIndent()
        val tokens = tokenizeAll(input)
        // Every character should be covered
        val totalLength = tokens.sumOf { it.second.length }
        assertEquals(input.length, totalLength)
    }

    @Test
    fun noGaps() {
        // Verify lexer produces contiguous tokens with no gaps
        val input = "fib(n) = fib(n - 1) + fib(n - 2)"
        val lexer = NostosLexerAdapter()
        lexer.start(input, 0, input.length, 0)
        var expectedStart = 0
        while (lexer.tokenType != null) {
            assertEquals(
                expectedStart, lexer.tokenStart,
                "Gap before position $expectedStart"
            )
            expectedStart = lexer.tokenEnd
            lexer.advance()
        }
        assertEquals(input.length, expectedStart)
    }

    // ==================== State management (incremental re-lexing) ====================

    @Test
    fun stateIsZeroInNormalCode() {
        val lexer = NostosLexerAdapter()
        val input = "x = 42"
        lexer.start(input, 0, input.length, 0)
        while (lexer.tokenType != null) {
            assertEquals(0, lexer.state and 0xFFFF, "Expected YYINITIAL state for normal code")
            lexer.advance()
        }
    }

    @Test
    fun stateRestoredAfterBlockComment() {
        val lexer = NostosLexerAdapter()
        val input = "x #* comment *# y"
        lexer.start(input, 0, input.length, 0)

        // Walk all tokens to find "y" and check state there
        var foundY = false
        while (lexer.tokenType != null) {
            val text = input.substring(lexer.tokenStart, lexer.tokenEnd)
            if (text == "y") {
                foundY = true
                assertEquals(NostosTokenTypes.IDENTIFIER, lexer.tokenType)
                assertEquals(0, lexer.state and 0xFFFF, "State should be YYINITIAL after block comment")
            }
            lexer.advance()
        }
        assert(foundY) { "Should have found identifier 'y' after block comment" }
    }

    @Test
    fun reLexFromMiddleOfBlockComment() {
        // Simulate re-lexing from mid-comment by starting with block comment state
        val lexer = NostosLexerAdapter()
        val input = "still in comment *# done"

        // JFlex state S_BLOCK_COMMENT = 2
        lexer.start(input, 0, input.length, 2)

        // First token(s) should be BLOCK_COMMENT (the comment content + closing *#)
        assertEquals(NostosTokenTypes.BLOCK_COMMENT, lexer.tokenType)
        lexer.advance()

        // After the comment closes, we should get whitespace then "done" as IDENTIFIER
        if (lexer.tokenType == TokenType.WHITE_SPACE) lexer.advance()
        assertEquals(NostosTokenTypes.IDENTIFIER, lexer.tokenType)
        assertEquals("done", tokenTextAt(lexer))
    }

    private fun tokenTextAt(lexer: NostosLexerAdapter): String {
        return lexer.bufferSequence.subSequence(lexer.tokenStart, lexer.tokenEnd).toString()
    }

    // ==================== Non-nested block comments ====================

    @Test
    fun blockCommentWithInnerOpenMarker() {
        // Nostos does NOT support nested block comments.
        // #* in the middle is just text; the first *# closes the comment.
        val input = "#* outer #* inner *# code_after"
        val tokens = tokenize(input)
        assertEquals(NostosTokenTypes.BLOCK_COMMENT, tokens[0].first)
        assertEquals("#* outer #* inner *#", tokens[0].second)
        assertEquals(NostosTokenTypes.IDENTIFIER, tokens[1].first)
        assertEquals("code_after", tokens[1].second)
    }
}
