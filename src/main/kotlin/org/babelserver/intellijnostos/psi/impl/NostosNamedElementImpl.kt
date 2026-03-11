package org.babelserver.intellijnostos.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.babelserver.intellijnostos.psi.NostosNamedElement
import org.babelserver.intellijnostos.psi.NostosTypes

abstract class NostosNamedElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), NostosNamedElement {

    override fun getNameIdentifier(): PsiElement? {
        // Try direct children first (works for fn_decl, type_decl, var_decl, etc.)
        val direct = node.findChildByType(NAME_TOKENS)
        if (direct != null) return direct.psi
        // For bare_param: name is nested inside pattern tree
        return findDeepNameToken(node)?.psi
    }

    private fun findDeepNameToken(parent: ASTNode): ASTNode? {
        var child = parent.firstChildNode
        while (child != null) {
            if (NAME_TOKENS.contains(child.elementType)) return child
            val deep = findDeepNameToken(child)
            if (deep != null) return deep
            child = child.treeNext
        }
        return null
    }

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement {
        val nameNode = nameIdentifier?.node ?: return this
        val newNode = NostosElementFactory.createIdentifier(project, name, nameNode.elementType)
        nameNode.treeParent.replaceChild(nameNode, newNode)
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    companion object {
        private val NAME_TOKENS = TokenSet.create(
            NostosTypes.FUNCTION_NAME,
            NostosTypes.IDENTIFIER,
            NostosTypes.TYPE_NAME,
        )
    }
}
