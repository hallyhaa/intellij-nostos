package org.babelserver.intellijnostos.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import org.babelserver.intellijnostos.NostosTypeReference
import org.babelserver.intellijnostos.psi.NostosTypes

abstract class NostosTypeRefMixin(node: ASTNode) : ASTWrapperPsiElement(node) {

    override fun getReference(): PsiReference? {
        val typeNameNode = node.findChildByType(NostosTypes.TYPE_NAME) ?: return null
        return NostosTypeReference(this, typeNameNode.text)
    }
}
