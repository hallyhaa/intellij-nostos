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

class NostosSyntaxHighlighter : SyntaxHighlighterBase() {

    companion object {
        val COMMENT_KEYS = arrayOf(
            TextAttributesKey.createTextAttributesKey(
                "NOSTOS_COMMENT",
                DefaultLanguageHighlighterColors.LINE_COMMENT
            )
        )
        val BLOCK_COMMENT_KEYS = arrayOf(
            TextAttributesKey.createTextAttributesKey(
                "NOSTOS_BLOCK_COMMENT",
                DefaultLanguageHighlighterColors.BLOCK_COMMENT
            )
        )
        val STRING_KEYS = arrayOf(
            TextAttributesKey.createTextAttributesKey(
                "NOSTOS_STRING",
                DefaultLanguageHighlighterColors.STRING
            )
        )
        val NUMBER_KEYS = arrayOf(
            TextAttributesKey.createTextAttributesKey(
                "NOSTOS_NUMBER",
                DefaultLanguageHighlighterColors.NUMBER
            )
        )
        val KEYWORD_KEYS = arrayOf(
            TextAttributesKey.createTextAttributesKey(
                "NOSTOS_KEYWORD",
                DefaultLanguageHighlighterColors.KEYWORD
            )
        )
        val IDENTIFIER_KEYS = arrayOf(
            TextAttributesKey.createTextAttributesKey(
                "NOSTOS_IDENTIFIER",
                DefaultLanguageHighlighterColors.IDENTIFIER
            )
        )
        val TYPE_NAME_KEYS = arrayOf(
            TextAttributesKey.createTextAttributesKey(
                "NOSTOS_TYPE_NAME",
                DefaultLanguageHighlighterColors.CLASS_NAME
            )
        )
        val FUNCTION_NAME_KEYS = arrayOf(
            TextAttributesKey.createTextAttributesKey(
                "NOSTOS_FUNCTION_NAME",
                DefaultLanguageHighlighterColors.FUNCTION_CALL
            )
        )
        val OPERATOR_KEYS = arrayOf(
            TextAttributesKey.createTextAttributesKey(
                "NOSTOS_OPERATOR",
                DefaultLanguageHighlighterColors.OPERATION_SIGN
            )
        )
        val CHAR_KEYS = arrayOf(
            TextAttributesKey.createTextAttributesKey(
                "NOSTOS_CHAR",
                DefaultLanguageHighlighterColors.STRING
            )
        )
        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }

    override fun getHighlightingLexer(): Lexer = NostosLexer()

    override fun getTokenHighlights(
        tokenType: IElementType?
    ): Array<TextAttributesKey> = when (tokenType) {
        NostosTokenTypes.COMMENT -> COMMENT_KEYS
        NostosTokenTypes.BLOCK_COMMENT -> BLOCK_COMMENT_KEYS
        NostosTokenTypes.STRING -> STRING_KEYS
        NostosTokenTypes.CHAR -> CHAR_KEYS
        NostosTokenTypes.NUMBER -> NUMBER_KEYS
        NostosTokenTypes.KEYWORD -> KEYWORD_KEYS
        NostosTokenTypes.IDENTIFIER -> IDENTIFIER_KEYS
        NostosTokenTypes.TYPE_NAME -> TYPE_NAME_KEYS
        NostosTokenTypes.FUNCTION_NAME -> FUNCTION_NAME_KEYS
        NostosTokenTypes.OPERATOR -> OPERATOR_KEYS
        else -> EMPTY_KEYS
    }
}

class NostosSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(
        project: Project?,
        virtualFile: VirtualFile?
    ): SyntaxHighlighter = NostosSyntaxHighlighter()
}
