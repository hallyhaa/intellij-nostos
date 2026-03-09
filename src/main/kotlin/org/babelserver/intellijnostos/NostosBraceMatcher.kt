package org.babelserver.intellijnostos

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.babelserver.intellijnostos.psi.NostosTypes

class NostosBraceMatcher : PairedBraceMatcher {
    override fun getPairs() = PAIRS
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?) = true
    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int) = openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
            BracePair(NostosTypes.LPAREN, NostosTypes.RPAREN, false),
            BracePair(NostosTypes.LBRACKET, NostosTypes.RBRACKET, false),
            BracePair(NostosTypes.LBRACE, NostosTypes.RBRACE, true),
        )
    }
}
