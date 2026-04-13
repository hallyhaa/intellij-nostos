package org.babelserver.intellijnostos

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.babelserver.intellijnostos.psi.NostosTypes

class NostosSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = NostosLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        when {
            tokenType == null -> EMPTY_KEYS
            KEYWORD_TOKENS.contains(tokenType) -> KEYWORD_KEYS
            tokenType == NostosTypes.COMMENT || tokenType == NostosTypes.BLOCK_COMMENT -> COMMENT_KEYS
            tokenType == NostosTypes.STRING -> STRING_KEYS
            tokenType == NostosTypes.NUMBER -> NUMBER_KEYS
            tokenType == NostosTypes.IDENTIFIER -> IDENTIFIER_KEYS
            tokenType == NostosTypes.TYPE_NAME -> TYPE_NAME_KEYS
            tokenType == NostosTypes.FUNCTION_NAME -> FUNCTION_NAME_KEYS
            OPERATOR_TOKENS.contains(tokenType) -> OPERATOR_KEYS
            tokenType == NostosTypes.LPAREN || tokenType == NostosTypes.RPAREN -> PAREN_KEYS
            tokenType == NostosTypes.LBRACKET || tokenType == NostosTypes.RBRACKET -> BRACKET_KEYS
            tokenType == NostosTypes.LBRACE || tokenType == NostosTypes.RBRACE -> BRACE_KEYS
            tokenType == NostosTypes.INTERPOLATION_START || tokenType == NostosTypes.INTERPOLATION_END -> INTERPOLATION_KEYS
            else -> EMPTY_KEYS
        }

    companion object {
        val KEYWORD_TOKENS: TokenSet = TokenSet.create(
            NostosTypes.FN, NostosTypes.IF, NostosTypes.THEN, NostosTypes.ELSE,
            NostosTypes.MATCH, NostosTypes.WITH, NostosTypes.TYPE_KW, NostosTypes.TRAIT,
            NostosTypes.END, NostosTypes.USE, NostosTypes.PUB, NostosTypes.PRIVATE,
            NostosTypes.MODULE_KW, NostosTypes.IMPORT, NostosTypes.VAR, NostosTypes.MVAR,
            NostosTypes.CONST, NostosTypes.FOR, NostosTypes.TO, NostosTypes.IN,
            NostosTypes.WHILE, NostosTypes.DO, NostosTypes.BREAK, NostosTypes.CONTINUE,
            NostosTypes.RETURN, NostosTypes.SPAWN, NostosTypes.SPAWN_LINK,
            NostosTypes.SPAWN_MONITOR, NostosTypes.RECEIVE, NostosTypes.AFTER,
            NostosTypes.TRY, NostosTypes.CATCH, NostosTypes.FINALLY, NostosTypes.THROW,
            NostosTypes.PANIC, NostosTypes.WHEN, NostosTypes.TRUE, NostosTypes.FALSE,
            NostosTypes.SELF, NostosTypes.SELF_TYPE, NostosTypes.REACTIVE,
            NostosTypes.DERIVING, NostosTypes.WHERE, NostosTypes.FORALL,
            NostosTypes.EXTERN, NostosTypes.TEST, NostosTypes.QUOTE, NostosTypes.FROM,
            NostosTypes.AS, NostosTypes.IMPL, NostosTypes.TEMPLATE
        )

        val OPERATOR_TOKENS: TokenSet = TokenSet.create(
            NostosTypes.PLUS, NostosTypes.MINUS, NostosTypes.STAR, NostosTypes.SLASH,
            NostosTypes.PERCENT, NostosTypes.EQ, NostosTypes.LT, NostosTypes.GT,
            NostosTypes.BANG, NostosTypes.DOT, NostosTypes.PIPE, NostosTypes.COMMA,
            NostosTypes.SEMICOLON, NostosTypes.COLON, NostosTypes.AT, NostosTypes.TILDE,
            NostosTypes.CARET, NostosTypes.AMP, NostosTypes.QUESTION,
            NostosTypes.PLUS_PLUS, NostosTypes.COLON_COLON, NostosTypes.ARROW,
            NostosTypes.SEND_OP, NostosTypes.LE, NostosTypes.GE, NostosTypes.EQ_EQ,
            NostosTypes.BANG_EQ, NostosTypes.AMP_AMP, NostosTypes.PIPE_PIPE,
            NostosTypes.STAR_STAR, NostosTypes.PLUS_EQ, NostosTypes.FAT_ARROW,
            NostosTypes.PIPE_GT, NostosTypes.MINUS_EQ, NostosTypes.STAR_EQ,
            NostosTypes.SLASH_EQ, NostosTypes.DOT_DOT, NostosTypes.BACKSLASH,
            NostosTypes.HASH_LBRACE, NostosTypes.PERCENT_LBRACE
        )

        private val COMMENT_KEYS = arrayOf(TextAttributesKey.createTextAttributesKey("NOSTOS_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT))
        private val KEYWORD_KEYS = arrayOf(TextAttributesKey.createTextAttributesKey("NOSTOS_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD))
        private val STRING_KEYS = arrayOf(TextAttributesKey.createTextAttributesKey("NOSTOS_STRING", DefaultLanguageHighlighterColors.STRING))
        private val NUMBER_KEYS = arrayOf(TextAttributesKey.createTextAttributesKey("NOSTOS_NUMBER", DefaultLanguageHighlighterColors.NUMBER))
        private val IDENTIFIER_KEYS = arrayOf(TextAttributesKey.createTextAttributesKey("NOSTOS_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER))
        private val TYPE_NAME_KEYS = arrayOf(TextAttributesKey.createTextAttributesKey("NOSTOS_TYPE_NAME", DefaultLanguageHighlighterColors.CLASS_NAME))
        private val FUNCTION_NAME_KEYS = arrayOf(TextAttributesKey.createTextAttributesKey("NOSTOS_FUNCTION_NAME", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION))
        private val OPERATOR_KEYS = arrayOf(TextAttributesKey.createTextAttributesKey("NOSTOS_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN))
        private val PAREN_KEYS = arrayOf(TextAttributesKey.createTextAttributesKey("NOSTOS_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES))
        private val BRACKET_KEYS = arrayOf(TextAttributesKey.createTextAttributesKey("NOSTOS_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS))
        private val BRACE_KEYS = arrayOf(TextAttributesKey.createTextAttributesKey("NOSTOS_BRACES", DefaultLanguageHighlighterColors.BRACES))
        private val INTERPOLATION_KEYS = arrayOf(TextAttributesKey.createTextAttributesKey("NOSTOS_INTERPOLATION", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE))
        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()

        // Semantic token attributes (used by NostosSemanticHighlighter)
        val SEMANTIC_VARIABLE = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
        val SEMANTIC_PARAMETER = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER)
        val SEMANTIC_PROPERTY = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_PROPERTY", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        val SEMANTIC_FUNCTION_DECL = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_FUNCTION_DECL", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
        val SEMANTIC_FUNCTION_CALL = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
        val SEMANTIC_TYPE = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME)
        val SEMANTIC_NAMESPACE = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_NAMESPACE", DefaultLanguageHighlighterColors.CLASS_NAME)
        val SEMANTIC_ENUM_MEMBER = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_ENUM_MEMBER", DefaultLanguageHighlighterColors.STATIC_FIELD)
        val SEMANTIC_METHOD = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_METHOD", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
        val SEMANTIC_STRUCT = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_STRUCT", DefaultLanguageHighlighterColors.CLASS_NAME)
        val SEMANTIC_ENUM = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_ENUM", DefaultLanguageHighlighterColors.CLASS_NAME)
        val SEMANTIC_INTERFACE = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_INTERFACE", DefaultLanguageHighlighterColors.INTERFACE_NAME)
        val SEMANTIC_TYPE_PARAMETER = TextAttributesKey.createTextAttributesKey("NOSTOS_SEMANTIC_TYPE_PARAMETER", DefaultLanguageHighlighterColors.CLASS_NAME)
    }
}

class NostosSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(
        project: Project?,
        virtualFile: VirtualFile?
    ): SyntaxHighlighter = NostosSyntaxHighlighter()
}
