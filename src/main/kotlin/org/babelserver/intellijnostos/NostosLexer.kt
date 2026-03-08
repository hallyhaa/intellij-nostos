package org.babelserver.intellijnostos

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

class NostosLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset = 0
    private var endOffset = 0
    private var position = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(
        buffer: CharSequence,
        startOffset: Int,
        endOffset: Int,
        initialState: Int
    ) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.position = startOffset
        advance()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        if (position >= endOffset) {
            tokenType = null
            return
        }

        tokenStart = position
        val c = buffer[position]

        when {
            c == '#' && position + 1 < endOffset
                && buffer[position + 1] == '*' -> lexBlockComment()
            c == '#' -> lexLineComment()
            c == '"' -> lexString()
            c == '\'' -> lexChar()
            c.isDigit() -> lexNumber()
            c.isLetter() || c == '_' -> lexIdentifierOrKeyword()
            c.isWhitespace() -> lexWhitespace()
            else -> lexOperator()
        }
    }

    private fun lexLineComment() {
        while (position < endOffset && buffer[position] != '\n') position++
        tokenEnd = position
        tokenType = NostosTokenTypes.COMMENT
    }

    private fun lexBlockComment() {
        position += 2
        while (position + 1 < endOffset) {
            if (buffer[position] == '*' && buffer[position + 1] == '#') {
                position += 2
                break
            }
            position++
        }
        tokenEnd = position
        tokenType = NostosTokenTypes.BLOCK_COMMENT
    }

    private fun lexString() {
        position++
        while (position < endOffset && buffer[position] != '"') {
            if (buffer[position] == '\\') position++
            position++
        }
        if (position < endOffset) position++
        tokenEnd = position
        tokenType = NostosTokenTypes.STRING
    }

    private fun lexChar() {
        position++
        if (position < endOffset && buffer[position] == '\\') position++
        if (position < endOffset) position++
        if (position < endOffset && buffer[position] == '\'') position++
        tokenEnd = position
        tokenType = NostosTokenTypes.CHAR
    }

    private fun lexNumber() {
        if (tryLexPrefixedNumber()) return
        skipDigitsAndUnderscores()
        tryLexFractionalPart()
        tryLexExponent()
        if (position < endOffset && buffer[position] == 'd') position++
        tokenEnd = position
        tokenType = NostosTokenTypes.NUMBER
    }

    private fun tryLexPrefixedNumber(): Boolean {
        if (buffer[position] != '0' || position + 1 >= endOffset) return false
        val next = buffer[position + 1]
        if (next !in "xXbB") return false
        position += 2
        while (position < endOffset &&
            (buffer[position].isLetterOrDigit() || buffer[position] == '_')
        ) position++
        tokenEnd = position
        tokenType = NostosTokenTypes.NUMBER
        return true
    }

    private fun skipDigitsAndUnderscores() {
        while (position < endOffset &&
            (buffer[position].isDigit() || buffer[position] == '_')
        ) position++
    }

    private fun tryLexFractionalPart() {
        if (position >= endOffset || buffer[position] != '.') return
        if (position + 1 >= endOffset || !buffer[position + 1].isDigit()) return
        position++
        skipDigitsAndUnderscores()
    }

    private fun tryLexExponent() {
        if (position >= endOffset || buffer[position] !in "eE") return
        position++
        if (position < endOffset && buffer[position] in "+-") position++
        while (position < endOffset && buffer[position].isDigit()) position++
    }

    private fun lexIdentifierOrKeyword() {
        while (position < endOffset &&
            (buffer[position].isLetterOrDigit() || buffer[position] == '_')
        ) position++
        tokenEnd = position
        val text = buffer.subSequence(tokenStart, tokenEnd).toString()
        tokenType = NostosTokenTypes.KEYWORDS[text]
            ?: when {
                buffer[tokenStart].isUpperCase() -> NostosTokenTypes.TYPE_NAME
                nextNonWhitespaceIs('(') -> NostosTokenTypes.FUNCTION_NAME
                else -> NostosTokenTypes.IDENTIFIER
            }
    }

    private fun nextNonWhitespaceIs(expected: Char): Boolean {
        var i = position
        while (i < endOffset && buffer[i] == ' ') i++
        return i < endOffset && buffer[i] == expected
    }

    private fun lexWhitespace() {
        while (position < endOffset && buffer[position].isWhitespace())
            position++
        tokenEnd = position
        tokenType = com.intellij.psi.TokenType.WHITE_SPACE
    }

    private fun lexOperator() {
        position++
        if (position < endOffset) {
            val twoChar = buffer.subSequence(tokenStart, position + 1)
                .toString()
            if (twoChar in setOf(
                    "++", "::", "->", "<-", "<=", ">=",
                    "==", "!=", "&&", "||", "**", "+="
                )
            ) {
                position++
            }
        }
        tokenEnd = position
        tokenType = NostosTokenTypes.OPERATOR
    }
}
