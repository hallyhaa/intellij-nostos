package org.babelserver.intellijnostos

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.MergeFunction
import com.intellij.lexer.MergingLexerAdapterBase
import com.intellij.psi.tree.TokenSet

private val TOKENS_TO_MERGE = TokenSet.create(
    NostosTokenTypes.BLOCK_COMMENT,
    NostosTokenTypes.STRING,
    NostosTokenTypes.CHAR
)

class NostosLexerAdapter : MergingLexerAdapterBase(
    FlexAdapter(NostosFlexLexer(null))
) {
    override fun getMergeFunction() = MergeFunction { type, lexer ->
        if (TOKENS_TO_MERGE.contains(type)) {
            while (lexer.tokenType == type) {
                lexer.advance()
            }
        }
        type
    }
}
