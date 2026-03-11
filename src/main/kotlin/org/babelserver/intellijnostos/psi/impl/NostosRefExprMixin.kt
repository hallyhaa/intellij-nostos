package org.babelserver.intellijnostos.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import org.babelserver.intellijnostos.NostosReference
import org.babelserver.intellijnostos.psi.NostosTypes

abstract class NostosRefExprMixin(node: ASTNode) : NostosExprImpl(node) {

    override fun getReference(): PsiReference? {
        val nameNode = node.findChildByType(NostosTypes.IDENTIFIER)
            ?: node.findChildByType(NostosTypes.FUNCTION_NAME)
            ?: return null
        return NostosReference(this, nameNode.text)
    }
}
