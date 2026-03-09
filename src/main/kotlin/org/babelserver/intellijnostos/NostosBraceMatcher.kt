package org.babelserver.intellijnostos

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class NostosBraceMatcher : PairedBraceMatcher {

    override fun getPairs() = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?) = true

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int) = openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
            BracePair(NostosTokenTypes.LPAREN, NostosTokenTypes.RPAREN, false),
            BracePair(NostosTokenTypes.LBRACKET, NostosTokenTypes.RBRACKET, false),
            BracePair(NostosTokenTypes.LBRACE, NostosTokenTypes.RBRACE, true),
        )
    }
}
