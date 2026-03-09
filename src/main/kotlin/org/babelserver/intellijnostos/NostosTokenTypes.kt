package org.babelserver.intellijnostos

import com.intellij.psi.tree.IElementType

object NostosTokenTypes {
    @JvmField val COMMENT = IElementType("COMMENT", NostosLanguage)
    @JvmField val BLOCK_COMMENT = IElementType("BLOCK_COMMENT", NostosLanguage)
    @JvmField val STRING = IElementType("STRING", NostosLanguage)
    @JvmField val CHAR = IElementType("CHAR", NostosLanguage)
    @JvmField val NUMBER = IElementType("NUMBER", NostosLanguage)
    @JvmField val IDENTIFIER = IElementType("IDENTIFIER", NostosLanguage)
    @JvmField val TYPE_NAME = IElementType("TYPE_NAME", NostosLanguage)
    @JvmField val FUNCTION_NAME = IElementType("FUNCTION_NAME", NostosLanguage)
    @JvmField val OPERATOR = IElementType("OPERATOR", NostosLanguage)
    @JvmField val LPAREN = IElementType("LPAREN", NostosLanguage)
    @JvmField val RPAREN = IElementType("RPAREN", NostosLanguage)
    @JvmField val LBRACKET = IElementType("LBRACKET", NostosLanguage)
    @JvmField val RBRACKET = IElementType("RBRACKET", NostosLanguage)
    @JvmField val LBRACE = IElementType("LBRACE", NostosLanguage)
    @JvmField val RBRACE = IElementType("RBRACE", NostosLanguage)
    @JvmField val INTERPOLATION_START = IElementType("INTERPOLATION_START", NostosLanguage)
    @JvmField val INTERPOLATION_END = IElementType("INTERPOLATION_END", NostosLanguage)
    @JvmField val KEYWORD = IElementType("KEYWORD", NostosLanguage)
}
