package org.babelserver.intellijnostos.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.tree.IElementType
import org.babelserver.intellijnostos.NostosFileType
import org.babelserver.intellijnostos.psi.NostosTypes

object NostosElementFactory {

    fun createIdentifier(project: Project, name: String, tokenType: IElementType): ASTNode {
        // Generate a small Nostos snippet that produces the desired token type
        val text = when (tokenType) {
            NostosTypes.TYPE_NAME -> "type $name = Unit"
            NostosTypes.FUNCTION_NAME -> "fn $name() = 0"
            else -> "var $name = 0"
        }
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("dummy.nos", NostosFileType, text)
        val decl = file.firstChild
        return findFirstToken(decl.node, tokenType)
            ?: error("Could not create $tokenType token for '$name'")
    }

    private fun findFirstToken(node: ASTNode, type: IElementType): ASTNode? {
        if (node.elementType == type) return node
        var child = node.firstChildNode
        while (child != null) {
            val found = findFirstToken(child, type)
            if (found != null) return found
            child = child.treeNext
        }
        return null
    }
}
