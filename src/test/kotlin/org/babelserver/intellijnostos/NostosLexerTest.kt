package org.babelserver.intellijnostos

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.babelserver.intellijnostos.psi.NostosTypes
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
        assertEquals(NostosTypes.COMMENT, type)
        assertEquals("# a comment", text)
    }

    @Test
    fun lineCommentEmpty() {
        val (type, _) = singleToken("#")
        assertEquals(NostosTypes.COMMENT, type)
    }

    @Test
    fun lineCommentStopsAtNewline() {
        val tokens = tokenizeAll("# comment\ncode")
        assertEquals(NostosTypes.COMMENT, tokens[0].first)
        assertEquals("# comment", tokens[0].second)
        assertEquals(TokenType.WHITE_SPACE, tokens[1].first)
        assertEquals(NostosTypes.IDENTIFIER, tokens[2].first)
        assertEquals("code", tokens[2].second)
    }

    @Test
    fun documentationComment() {
        val (type, text) = singleToken("## doc comment")
        assertEquals(NostosTypes.COMMENT, type)
        assertEquals("## doc comment", text)
    }

    // ==================== Block comments ====================

    @Test
    fun blockComment() {
        val (type, text) = singleToken("#* block *#")
        assertEquals(NostosTypes.BLOCK_COMMENT, type)
        assertEquals("#* block *#", text)
    }

    @Test
    fun blockCommentMultiline() {
        val input = "#* line1\nline2\nline3 *#"
        val (type, text) = singleToken(input)
        assertEquals(NostosTypes.BLOCK_COMMENT, type)
        assertEquals(input, text)
    }

    @Test
    fun blockCommentWithHashInside() {
        val (type, text) = singleToken("#* has # inside *#")
        assertEquals(NostosTypes.BLOCK_COMMENT, type)
        assertEquals("#* has # inside *#", text)
    }

    @Test
    fun blockCommentUnclosed() {
        val (type, _) = singleToken("#* unclosed")
        assertEquals(NostosTypes.BLOCK_COMMENT, type)
    }

    // ==================== Strings ====================

    @Test
    fun simpleString() {
        val (type, text) = singleToken("\"hello\"")
        assertEquals(NostosTypes.STRING, type)
        assertEquals("\"hello\"", text)
    }

    @Test
    fun emptyString() {
        val (type, text) = singleToken("\"\"")
        assertEquals(NostosTypes.STRING, type)
        assertEquals("\"\"", text)
    }

    @Test
    fun stringWithEscapes() {
        val (type, text) = singleToken("\"hello\\nworld\"")
        assertEquals(NostosTypes.STRING, type)
        assertEquals("\"hello\\nworld\"", text)
    }

    @Test
    fun stringWithEscapedQuote() {
        val (type, text) = singleToken("\"say \\\"hi\\\"\"")
        assertEquals(NostosTypes.STRING, type)
        assertEquals("\"say \\\"hi\\\"\"", text)
    }

    @Test
    fun stringWithEscapedBackslash() {
        val (type, text) = singleToken("\"path\\\\dir\"")
        assertEquals(NostosTypes.STRING, type)
        assertEquals("\"path\\\\dir\"", text)
    }

    @Test
    fun stringWithInterpolation() {
        val tokens = tokenize("\"value: \${x + 1}\"")
        assertEquals(NostosTypes.STRING, tokens[0].first)            // "value:
        assertEquals("\"value: ", tokens[0].second)
        assertEquals(NostosTypes.INTERPOLATION_START, tokens[1].first)  // ${
        assertEquals("\${", tokens[1].second)
        assertEquals(NostosTypes.IDENTIFIER, tokens[2].first)        // x
        assertEquals(NostosTypes.PLUS, tokens[3].first)              // +
        assertEquals(NostosTypes.NUMBER, tokens[4].first)            // 1
        assertEquals(NostosTypes.INTERPOLATION_END, tokens[5].first)   // }
        assertEquals(NostosTypes.STRING, tokens[6].first)            // "
        assertEquals("\"", tokens[6].second)
    }

    @Test
    fun stringInterpolationSimpleVar() {
        val tokens = tokenize("\"hello \${name}!\"")
        assertEquals(NostosTypes.STRING, tokens[0].first)
        assertEquals("\"hello ", tokens[0].second)
        assertEquals(NostosTypes.INTERPOLATION_START, tokens[1].first)
        assertEquals(NostosTypes.IDENTIFIER, tokens[2].first)
        assertEquals("name", tokens[2].second)
        assertEquals(NostosTypes.INTERPOLATION_END, tokens[3].first)
        assertEquals(NostosTypes.STRING, tokens[4].first)
        assertEquals("!\"", tokens[4].second)
    }

    @Test
    fun stringInterpolationWithFunctionCall() {
        val tokens = tokenize("\"result: \${f(x)}\"")
        assertEquals(NostosTypes.STRING, tokens[0].first)
        assertEquals(NostosTypes.INTERPOLATION_START, tokens[1].first)
        assertEquals(NostosTypes.FUNCTION_NAME, tokens[2].first)
        assertEquals("f", tokens[2].second)
        assertEquals(NostosTypes.LPAREN, tokens[3].first)
        assertEquals(NostosTypes.IDENTIFIER, tokens[4].first)
        assertEquals(NostosTypes.RPAREN, tokens[5].first)
        assertEquals(NostosTypes.INTERPOLATION_END, tokens[6].first)
        assertEquals(NostosTypes.STRING, tokens[7].first)
    }

    @Test
    fun stringInterpolationWithMapLiteral() {
        // Braces inside interpolation should not close it prematurely
        val tokens = tokenize("\"\${ %{a: 1} }\"")
        assertEquals(NostosTypes.STRING, tokens[0].first)
        assertEquals(NostosTypes.INTERPOLATION_START, tokens[1].first)
        assertEquals(NostosTypes.PERCENT_LBRACE, tokens[2].first)       // %{
        assertEquals(NostosTypes.IDENTIFIER, tokens[3].first)           // a
        assertEquals(NostosTypes.COLON, tokens[4].first)                // :
        assertEquals(NostosTypes.NUMBER, tokens[5].first)               // 1
        assertEquals(NostosTypes.RBRACE, tokens[6].first)               // }
        assertEquals(NostosTypes.INTERPOLATION_END, tokens[7].first)    // }
        assertEquals(NostosTypes.STRING, tokens[8].first)               // "
    }

    @Test
    fun stringDollarWithoutBrace() {
        // $ not followed by { is just string content
        val (type, text) = singleToken("\"\$x\"")
        assertEquals(NostosTypes.STRING, type)
        assertEquals("\"\$x\"", text)
    }

    @Test
    fun stringEscapedDollar() {
        // \$ is an escape sequence, should not trigger interpolation
        val (type, text) = singleToken("\"\\\${x}\"")
        assertEquals(NostosTypes.STRING, type)
        assertEquals("\"\\\${x}\"", text)
    }

    @Test
    fun unclosedString() {
        val (type, _) = singleToken("\"unclosed")
        assertEquals(NostosTypes.STRING, type)
    }

    // ==================== Single-quoted strings ====================
    // Single-quoted strings produce multiple STRING tokens from the flex lexer,
    // but MergingLexerAdapter merges consecutive STRING tokens into one.

    @Test
    fun simpleChar() {
        val (type, text) = singleToken("'a'")
        assertEquals(NostosTypes.STRING, type)
        assertEquals("'a'", text)
    }

    @Test
    fun escapedChar() {
        val (type, text) = singleToken("'\\n'")
        assertEquals(NostosTypes.STRING, type)
        assertEquals("'\\n'", text)
    }

    @Test
    fun multiCharSingleQuotedString() {
        val (type, text) = singleToken("'hello world'")
        assertEquals(NostosTypes.STRING, type)
        assertEquals("'hello world'", text)
    }

    // ==================== Numbers ====================

    @Test
    fun integer() {
        val (type, text) = singleToken("42")
        assertEquals(NostosTypes.NUMBER, type)
        assertEquals("42", text)
    }

    @Test
    fun zero() {
        val (type, text) = singleToken("0")
        assertEquals(NostosTypes.NUMBER, type)
        assertEquals("0", text)
    }

    @Test
    fun `negative number is two tokens`() {
        val tokens = tokenize("-17")
        assertEquals(2, tokens.size)
        assertEquals(NostosTypes.MINUS, tokens[0].first)
        assertEquals("-", tokens[0].second)
        assertEquals(NostosTypes.NUMBER, tokens[1].first)
        assertEquals("17", tokens[1].second)
    }

    @Test
    fun integerWithUnderscores() {
        val (type, text) = singleToken("1_000_000")
        assertEquals(NostosTypes.NUMBER, type)
        assertEquals("1_000_000", text)
    }

    @Test
    fun hexInteger() {
        val (type, text) = singleToken("0xFF")
        assertEquals(NostosTypes.NUMBER, type)
        assertEquals("0xFF", text)
    }

    @Test
    fun hexIntegerUpperCase() {
        val (type, text) = singleToken("0XAB")
        assertEquals(NostosTypes.NUMBER, type)
        assertEquals("0XAB", text)
    }

    @Test
    fun binaryInteger() {
        val (type, text) = singleToken("0b1010")
        assertEquals(NostosTypes.NUMBER, type)
        assertEquals("0b1010", text)
    }

    @Test
    fun binaryIntegerUpperCase() {
        val (type, text) = singleToken("0B1100")
        assertEquals(NostosTypes.NUMBER, type)
        assertEquals("0B1100", text)
    }

    @Test
    fun floatNumber() {
        val (type, text) = singleToken("3.14")
        assertEquals(NostosTypes.NUMBER, type)
        assertEquals("3.14", text)
    }

    @Test
    fun floatWithExponent() {
        val (type, text) = singleToken("1.2e10")
        assertEquals(NostosTypes.NUMBER, type)
        assertEquals("1.2e10", text)
    }

    @Test
    fun floatWithNegativeExponent() {
        val (type, text) = singleToken("1.2e-10")
        assertEquals(NostosTypes.NUMBER, type)
        assertEquals("1.2e-10", text)
    }

    @Test
    fun floatWithPositiveExponent() {
        val (type, text) = singleToken("5E+3")
        assertEquals(NostosTypes.NUMBER, type)
        assertEquals("5E+3", text)
    }

    @Test
    fun decimalNumber() {
        val (type, text) = singleToken("0.1d")
        assertEquals(NostosTypes.NUMBER, type)
        assertEquals("0.1d", text)
    }

    @Test
    fun integerFollowedByDotIdentifier() {
        // 42.foo should be number "42" then operator "." then identifier "foo"
        val tokens = tokenize("42.foo")
        assertEquals(NostosTypes.NUMBER, tokens[0].first)
        assertEquals("42", tokens[0].second)
    }

    // ==================== Keywords ====================

    @Test
    fun allKeywords() {
        val expectedKeywords = mapOf(
            "fn" to NostosTypes.FN,
            "if" to NostosTypes.IF,
            "then" to NostosTypes.THEN,
            "else" to NostosTypes.ELSE,
            "match" to NostosTypes.MATCH,
            "with" to NostosTypes.WITH,
            "type" to NostosTypes.TYPE_KW,
            "trait" to NostosTypes.TRAIT,
            "end" to NostosTypes.END,
            "use" to NostosTypes.USE,
            "pub" to NostosTypes.PUB,
            "private" to NostosTypes.PRIVATE,
            "module" to NostosTypes.MODULE_KW,
            "import" to NostosTypes.IMPORT,
            "var" to NostosTypes.VAR,
            "mvar" to NostosTypes.MVAR,
            "const" to NostosTypes.CONST,
            "for" to NostosTypes.FOR,
            "to" to NostosTypes.TO,
            "in" to NostosTypes.IN,
            "while" to NostosTypes.WHILE,
            "do" to NostosTypes.DO,
            "break" to NostosTypes.BREAK,
            "continue" to NostosTypes.CONTINUE,
            "return" to NostosTypes.RETURN,
            "spawn" to NostosTypes.SPAWN,
            "spawn_link" to NostosTypes.SPAWN_LINK,
            "spawn_monitor" to NostosTypes.SPAWN_MONITOR,
            "receive" to NostosTypes.RECEIVE,
            "after" to NostosTypes.AFTER,
            "try" to NostosTypes.TRY,
            "catch" to NostosTypes.CATCH,
            "finally" to NostosTypes.FINALLY,
            "throw" to NostosTypes.THROW,
            "panic" to NostosTypes.PANIC,
            "when" to NostosTypes.WHEN,
            "true" to NostosTypes.TRUE,
            "false" to NostosTypes.FALSE,
            "self" to NostosTypes.SELF,
            "Self" to NostosTypes.SELF_TYPE,
            "reactive" to NostosTypes.REACTIVE,
            "deriving" to NostosTypes.DERIVING,
            "where" to NostosTypes.WHERE,
            "forall" to NostosTypes.FORALL,
            "extern" to NostosTypes.EXTERN,
            "test" to NostosTypes.TEST,
            "quote" to NostosTypes.QUOTE,
            "from" to NostosTypes.FROM,
            "as" to NostosTypes.AS,
        )
        for ((kw, expectedType) in expectedKeywords) {
            val (type, text) = singleToken(kw)
            assertEquals(expectedType, type, "Expected $expectedType for: $kw")
            assertEquals(kw, text)
        }
    }

    @Test
    fun keywordPrefix() {
        // "ifx" should be an identifier, not keyword "if" + "x"
        val (type, text) = singleToken("ifx")
        assertEquals(NostosTypes.IDENTIFIER, type)
        assertEquals("ifx", text)
    }

    @Test
    fun keywordSuffix() {
        val (type, text) = singleToken("types")
        assertEquals(NostosTypes.IDENTIFIER, type)
        assertEquals("types", text)
    }

    // ==================== Identifiers ====================

    @Test
    fun simpleIdentifier() {
        val (type, text) = singleToken("foo")
        assertEquals(NostosTypes.IDENTIFIER, type)
        assertEquals("foo", text)
    }

    @Test
    fun identifierWithUnderscore() {
        val (type, text) = singleToken("my_var")
        assertEquals(NostosTypes.IDENTIFIER, type)
        assertEquals("my_var", text)
    }

    @Test
    fun identifierStartingWithUnderscore() {
        val (type, text) = singleToken("_unused")
        assertEquals(NostosTypes.IDENTIFIER, type)
        assertEquals("_unused", text)
    }

    @Test
    fun identifierWithDigits() {
        val (type, text) = singleToken("x2")
        assertEquals(NostosTypes.IDENTIFIER, type)
        assertEquals("x2", text)
    }

    @Test
    fun singleUnderscore() {
        val (type, text) = singleToken("_")
        assertEquals(NostosTypes.UNDERSCORE, type)
        assertEquals("_", text)
    }

    // ==================== Type names ====================

    @Test
    fun typeName() {
        val (type, text) = singleToken("Point")
        assertEquals(NostosTypes.TYPE_NAME, type)
        assertEquals("Point", text)
    }

    @Test
    fun typeNameSingleLetter() {
        val (type, text) = singleToken("T")
        assertEquals(NostosTypes.TYPE_NAME, type)
        assertEquals("T", text)
    }

    @Test
    fun typeNameWithDigits() {
        val (type, text) = singleToken("Vec3")
        assertEquals(NostosTypes.TYPE_NAME, type)
        assertEquals("Vec3", text)
    }

    @Test
    fun typeNameComplex() {
        val (type, _) = singleToken("Int64Array")
        assertEquals(NostosTypes.TYPE_NAME, type)
    }

    @Test
    fun selfTypeIsKeywordNotType() {
        val (type, _) = singleToken("Self")
        assertEquals(NostosTypes.SELF_TYPE, type)
    }

    // ==================== Function names ====================

    @Test
    fun functionCall() {
        val tokens = tokenize("foo(x)")
        assertEquals(NostosTypes.FUNCTION_NAME, tokens[0].first)
        assertEquals("foo", tokens[0].second)
    }

    @Test
    fun functionCallWithSpace() {
        val tokens = tokenize("foo (x)")
        assertEquals(NostosTypes.FUNCTION_NAME, tokens[0].first)
        assertEquals("foo", tokens[0].second)
    }

    @Test
    fun functionCallMultipleSpaces() {
        val tokens = tokenize("foo   (x)")
        assertEquals(NostosTypes.FUNCTION_NAME, tokens[0].first)
        assertEquals("foo", tokens[0].second)
    }

    @Test
    fun identifierNotFollowedByParen() {
        val tokens = tokenize("foo + bar")
        assertEquals(NostosTypes.IDENTIFIER, tokens[0].first)
        assertEquals("foo", tokens[0].second)
    }

    @Test
    fun identifierAtEndOfInput() {
        val (type, _) = singleToken("foo")
        assertEquals(NostosTypes.IDENTIFIER, type)
    }

    @Test
    fun typeNameWithParenIsStillType() {
        // Point(x, y) -- "Point" starts with uppercase, so TYPE_NAME
        val tokens = tokenize("Point(x)")
        assertEquals(NostosTypes.TYPE_NAME, tokens[0].first)
    }

    @Test
    fun keywordFollowedByParenIsKeyword() {
        val tokens = tokenize("if(x)")
        assertEquals(NostosTypes.IF, tokens[0].first)
    }

    // ==================== Operators ====================

    @Test
    fun singleCharOperators() {
        val ops = mapOf(
            "+" to NostosTypes.PLUS,
            "-" to NostosTypes.MINUS,
            "*" to NostosTypes.STAR,
            "/" to NostosTypes.SLASH,
            "%" to NostosTypes.PERCENT,
            "=" to NostosTypes.EQ,
            "<" to NostosTypes.LT,
            ">" to NostosTypes.GT,
            "!" to NostosTypes.BANG,
            "." to NostosTypes.DOT,
            "|" to NostosTypes.PIPE,
            "," to NostosTypes.COMMA,
            ";" to NostosTypes.SEMICOLON,
            ":" to NostosTypes.COLON,
            "@" to NostosTypes.AT,
            "~" to NostosTypes.TILDE,
            "^" to NostosTypes.CARET,
            "&" to NostosTypes.AMP,
            "?" to NostosTypes.QUESTION,
        )
        for ((op, expectedType) in ops) {
            val (type, text) = singleToken(op)
            assertEquals(expectedType, type, "Expected $expectedType for: $op")
            assertEquals(op, text)
        }
    }

    @Test
    fun twoCharOperators() {
        val ops = mapOf(
            "++" to NostosTypes.PLUS_PLUS,
            "::" to NostosTypes.COLON_COLON,
            "->" to NostosTypes.ARROW,
            "<-" to NostosTypes.SEND_OP,
            "<=" to NostosTypes.LE,
            ">=" to NostosTypes.GE,
            "==" to NostosTypes.EQ_EQ,
            "!=" to NostosTypes.BANG_EQ,
            "&&" to NostosTypes.AMP_AMP,
            "||" to NostosTypes.PIPE_PIPE,
            "**" to NostosTypes.STAR_STAR,
            "+=" to NostosTypes.PLUS_EQ,
            "=>" to NostosTypes.FAT_ARROW,
            "|>" to NostosTypes.PIPE_GT,
            "-=" to NostosTypes.MINUS_EQ,
            "*=" to NostosTypes.STAR_EQ,
            "/=" to NostosTypes.SLASH_EQ,
            ".." to NostosTypes.DOT_DOT,
        )
        for ((op, expectedType) in ops) {
            val (type, text) = singleToken(op)
            assertEquals(expectedType, type, "Expected $expectedType for: $op")
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
        assertEquals(NostosTypes.LPAREN, tokens[0].first)
        assertEquals(NostosTypes.RPAREN, tokens[1].first)
        assertEquals(NostosTypes.LBRACKET, tokens[2].first)
        assertEquals(NostosTypes.RBRACKET, tokens[3].first)
        assertEquals(NostosTypes.LBRACE, tokens[4].first)
        assertEquals(NostosTypes.RBRACE, tokens[5].first)
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
        assertEquals(NostosTypes.FUNCTION_NAME, tokens[0].first) // double
        assertEquals(NostosTypes.LPAREN, tokens[1].first)        // (
        assertEquals(NostosTypes.IDENTIFIER, tokens[2].first)    // x
        assertEquals(NostosTypes.RPAREN, tokens[3].first)        // )
        assertEquals(NostosTypes.EQ, tokens[4].first)            // =
        assertEquals(NostosTypes.IDENTIFIER, tokens[5].first)    // x
        assertEquals(NostosTypes.STAR, tokens[6].first)          // *
        assertEquals(NostosTypes.NUMBER, tokens[7].first)        // 2
    }

    @Test
    fun typeDefinition() {
        val tokens = tokenize("type Point = { x: Float, y: Float }")
        assertEquals(NostosTypes.TYPE_KW, tokens[0].first)    // type
        assertEquals(NostosTypes.TYPE_NAME, tokens[1].first)   // Point
    }

    @Test
    fun traitDefinition() {
        // trait Animal\n    speak(self) -> String\nend
        val tokens = tokenize("trait Animal\n    speak(self) -> String\nend")
        assertEquals(NostosTypes.TRAIT, tokens[0].first)           // trait
        assertEquals("trait", tokens[0].second)
        assertEquals(NostosTypes.TYPE_NAME, tokens[1].first)       // Animal
        assertEquals("Animal", tokens[1].second)
        assertEquals(NostosTypes.FUNCTION_NAME, tokens[2].first)   // speak
        assertEquals("speak", tokens[2].second)
        assertEquals(NostosTypes.END, tokens.last().first)         // end
        assertEquals("end", tokens.last().second)
    }

    @Test
    fun patternMatchingFunction() {
        val tokens = tokenize("fib(0) = 0")
        assertEquals(NostosTypes.FUNCTION_NAME, tokens[0].first)
        assertEquals("fib", tokens[0].second)
    }

    @Test
    fun lambdaExpression() {
        val tokens = tokenize("x => x * 2")
        assertEquals(NostosTypes.IDENTIFIER, tokens[0].first) // x
        assertEquals(NostosTypes.FAT_ARROW, tokens[1].first)  // =>
        assertEquals("=>", tokens[1].second)
    }

    @Test
    fun listLiteral() {
        // [1, 2, 3] without whitespace: [, 1, ,, 2, ,, 3, ]
        val tokens = tokenize("[1, 2, 3]")
        assertEquals(NostosTypes.LBRACKET, tokens[0].first)  // [
        assertEquals(NostosTypes.NUMBER, tokens[1].first)     // 1
        assertEquals(NostosTypes.COMMA, tokens[2].first)      // ,
        assertEquals(NostosTypes.NUMBER, tokens[3].first)     // 2
        assertEquals(NostosTypes.COMMA, tokens[4].first)      // ,
        assertEquals(NostosTypes.NUMBER, tokens[5].first)     // 3
        assertEquals(NostosTypes.RBRACKET, tokens[6].first)   // ]
    }

    @Test
    fun mapLiteral() {
        val tokens = tokenize("%{\"key\": 42}")
        assertEquals(NostosTypes.PERCENT_LBRACE, tokens[0].first)  // %{
        assertEquals(NostosTypes.STRING, tokens[1].first)           // "key"
    }

    @Test
    fun setLiteral() {
        val tokens = tokenize("#{1, 2}")
        assertEquals(NostosTypes.HASH_LBRACE, tokens[0].first) // #{
        assertEquals(NostosTypes.NUMBER, tokens[1].first)       // 1
        assertEquals(NostosTypes.COMMA, tokens[2].first)        // ,
        assertEquals(NostosTypes.NUMBER, tokens[3].first)       // 2
        assertEquals(NostosTypes.RBRACE, tokens[4].first)       // }
    }

    @Test
    fun messageSend() {
        val tokens = tokenize("pid <- msg")
        assertEquals(NostosTypes.IDENTIFIER, tokens[0].first)  // pid
        assertEquals(NostosTypes.SEND_OP, tokens[1].first)     // <-
        assertEquals("<-", tokens[1].second)
        assertEquals(NostosTypes.IDENTIFIER, tokens[2].first)  // msg
    }

    @Test
    fun methodChain() {
        // list.map(f).filter(p) -> list, ., map, (, f, ), ., filter, (, p, )
        val tokens = tokenize("list.map(f).filter(p)")
        assertEquals(NostosTypes.IDENTIFIER, tokens[0].first)    // list
        assertEquals(NostosTypes.DOT, tokens[1].first)           // .
        assertEquals(NostosTypes.FUNCTION_NAME, tokens[2].first) // map
        assertEquals(NostosTypes.LPAREN, tokens[3].first)        // (
        assertEquals(NostosTypes.IDENTIFIER, tokens[4].first)    // f
        assertEquals(NostosTypes.RPAREN, tokens[5].first)        // )
        assertEquals(NostosTypes.DOT, tokens[6].first)           // .
        assertEquals(NostosTypes.FUNCTION_NAME, tokens[7].first) // filter
    }

    @Test
    fun commentFollowedByCode() {
        val tokens = tokenize("# comment\nx = 5")
        assertEquals(NostosTypes.COMMENT, tokens[0].first)
        assertEquals("# comment", tokens[0].second)
        assertEquals(NostosTypes.IDENTIFIER, tokens[1].first)
        assertEquals("x", tokens[1].second)
    }

    @Test
    fun stringFollowedByOperator() {
        val tokens = tokenize("\"hello\" ++ \"world\"")
        assertEquals(NostosTypes.STRING, tokens[0].first)
        assertEquals(NostosTypes.PLUS_PLUS, tokens[1].first)
        assertEquals("++", tokens[1].second)
        assertEquals(NostosTypes.STRING, tokens[2].first)
    }

    @Test
    fun consOperator() {
        val tokens = tokenize("[h | t]")
        assertEquals(NostosTypes.LBRACKET, tokens[0].first)   // [
        assertEquals(NostosTypes.IDENTIFIER, tokens[1].first)  // h
        assertEquals(NostosTypes.PIPE, tokens[2].first)        // |
        assertEquals(NostosTypes.IDENTIFIER, tokens[3].first)  // t
        assertEquals(NostosTypes.RBRACKET, tokens[4].first)    // ]
    }

    @Test
    fun receiveBlock() {
        val input = "receive {\n    Ping(sender) -> sender <- Pong\n}"
        val tokens = tokenize(input)
        assertEquals(NostosTypes.RECEIVE, tokens[0].first)     // receive
        assertEquals("receive", tokens[0].second)
        assertEquals(NostosTypes.LBRACE, tokens[1].first)      // {
        assertEquals(NostosTypes.TYPE_NAME, tokens[2].first)   // Ping
        assertEquals("Ping", tokens[2].second)
    }

    @Test
    fun spawnBlock() {
        val tokens = tokenize("spawn { worker() }")
        assertEquals(NostosTypes.SPAWN, tokens[0].first)          // spawn
        assertEquals(NostosTypes.LBRACE, tokens[1].first)         // {
        assertEquals(NostosTypes.FUNCTION_NAME, tokens[2].first)  // worker
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
                assertEquals(NostosTypes.IDENTIFIER, lexer.tokenType)
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
        assertEquals(NostosTypes.BLOCK_COMMENT, lexer.tokenType)
        lexer.advance()

        // After the comment closes, we should get whitespace then "done" as IDENTIFIER
        if (lexer.tokenType == TokenType.WHITE_SPACE) lexer.advance()
        assertEquals(NostosTypes.IDENTIFIER, lexer.tokenType)
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
        assertEquals(NostosTypes.BLOCK_COMMENT, tokens[0].first)
        assertEquals("#* outer #* inner *#", tokens[0].second)
        assertEquals(NostosTypes.IDENTIFIER, tokens[1].first)
        assertEquals("code_after", tokens[1].second)
    }

    // ==================== New token types ====================

    @Test
    fun backslash() {
        val (type, text) = singleToken("\\")
        assertEquals(NostosTypes.BACKSLASH, type)
        assertEquals("\\", text)
    }

    @Test
    fun underscoreAsWildcard() {
        // Single underscore is a wildcard/discard pattern
        val tokens = tokenize("match x with _ -> 0")
        assertEquals(NostosTypes.MATCH, tokens[0].first)
        assertEquals(NostosTypes.IDENTIFIER, tokens[1].first)  // x
        assertEquals(NostosTypes.WITH, tokens[2].first)
        assertEquals(NostosTypes.UNDERSCORE, tokens[3].first)  // _
        assertEquals(NostosTypes.ARROW, tokens[4].first)
        assertEquals(NostosTypes.NUMBER, tokens[5].first)
    }

    @Test
    fun dotDotRange() {
        val tokens = tokenize("1..10")
        assertEquals(NostosTypes.NUMBER, tokens[0].first)    // 1
        assertEquals(NostosTypes.DOT_DOT, tokens[1].first)   // ..
        assertEquals(NostosTypes.NUMBER, tokens[2].first)     // 10
    }

    @Test
    fun hashLbraceSetLiteral() {
        val (type, text) = singleToken("#{")
        assertEquals(NostosTypes.HASH_LBRACE, type)
        assertEquals("#{", text)
    }

    @Test
    fun percentLbraceMapLiteral() {
        val (type, text) = singleToken("%{")
        assertEquals(NostosTypes.PERCENT_LBRACE, type)
        assertEquals("%{", text)
    }

    @Test
    fun fnKeyword() {
        val (type, text) = singleToken("fn")
        assertEquals(NostosTypes.FN, type)
        assertEquals("fn", text)
    }

    @Test
    fun inKeyword() {
        val (type, text) = singleToken("in")
        assertEquals(NostosTypes.IN, type)
        assertEquals("in", text)
    }

    @Test
    fun asKeyword() {
        val (type, text) = singleToken("as")
        assertEquals(NostosTypes.AS, type)
        assertEquals("as", text)
    }
}
