package org.babelserver.intellijnostos

import com.intellij.psi.tree.IElementType

object NostosTokenTypes {
    val COMMENT = IElementType("COMMENT", NostosLanguage)
    val BLOCK_COMMENT = IElementType("BLOCK_COMMENT", NostosLanguage)
    val STRING = IElementType("STRING", NostosLanguage)
    val CHAR = IElementType("CHAR", NostosLanguage)
    val NUMBER = IElementType("NUMBER", NostosLanguage)
    val IDENTIFIER = IElementType("IDENTIFIER", NostosLanguage)
    val TYPE_NAME = IElementType("TYPE_NAME", NostosLanguage)
    val FUNCTION_NAME = IElementType("FUNCTION_NAME", NostosLanguage)
    val OPERATOR = IElementType("OPERATOR", NostosLanguage)
    val KEYWORD = IElementType("KEYWORD", NostosLanguage)

    val KEYWORDS = mapOf(
        "if" to KEYWORD,
        "then" to KEYWORD,
        "else" to KEYWORD,
        "match" to KEYWORD,
        "with" to KEYWORD,
        "type" to KEYWORD,
        "trait" to KEYWORD,
        "end" to KEYWORD,
        "use" to KEYWORD,
        "pub" to KEYWORD,
        "private" to KEYWORD,
        "module" to KEYWORD,
        "import" to KEYWORD,
        "var" to KEYWORD,
        "mvar" to KEYWORD,
        "const" to KEYWORD,
        "for" to KEYWORD,
        "to" to KEYWORD,
        "while" to KEYWORD,
        "do" to KEYWORD,
        "break" to KEYWORD,
        "continue" to KEYWORD,
        "return" to KEYWORD,
        "spawn" to KEYWORD,
        "spawn_link" to KEYWORD,
        "spawn_monitor" to KEYWORD,
        "receive" to KEYWORD,
        "after" to KEYWORD,
        "try" to KEYWORD,
        "catch" to KEYWORD,
        "finally" to KEYWORD,
        "throw" to KEYWORD,
        "panic" to KEYWORD,
        "when" to KEYWORD,
        "true" to KEYWORD,
        "false" to KEYWORD,
        "self" to KEYWORD,
        "Self" to KEYWORD,
        "reactive" to KEYWORD,
        "deriving" to KEYWORD,
        "where" to KEYWORD,
        "forall" to KEYWORD,
        "extern" to KEYWORD,
        "test" to KEYWORD,
        "quote" to KEYWORD,
        "from" to KEYWORD,
    )

}
