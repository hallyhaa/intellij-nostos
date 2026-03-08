package com.github.hallyhaa.intellijnostos

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType

class NostosElementType(debugName: String) :
    IElementType(debugName, NostosLanguage) {

    object Factory {
        fun createElement(node: ASTNode?): PsiElement =
            throw AssertionError("Unknown element type: ${node?.elementType}")
    }
}
