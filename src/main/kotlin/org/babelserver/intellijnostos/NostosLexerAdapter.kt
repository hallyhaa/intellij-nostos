package org.babelserver.intellijnostos

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.MergeFunction
import com.intellij.lexer.MergingLexerAdapterBase
import org.babelserver.intellijnostos.psi.NostosTypes

class NostosLexerAdapter : MergingLexerAdapterBase(
    NostosStateEncodingLexer(NostosFlexLexer(null))
) {
    override fun getMergeFunction() = MergeFunction { type, lexer ->
        if (TOKENS_TO_MERGE.contains(type)) {
            while (lexer.tokenType == type) { lexer.advance() }
        }
        type
    }

    companion object {
        private val TOKENS_TO_MERGE = setOf(
            NostosTypes.BLOCK_COMMENT,
            NostosTypes.STRING,
            NostosTypes.CHAR
        )
    }
}

internal class NostosStateEncodingLexer(
    private val flexLexer: NostosFlexLexer
) : FlexAdapter(flexLexer) {
    override fun getState(): Int {
        return super.getState() or (flexLexer.interpolationDepth shl 16)
    }

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        flexLexer.interpolationDepth = initialState ushr 16
        super.start(buffer, startOffset, endOffset, initialState and 0xFFFF)
    }
}
