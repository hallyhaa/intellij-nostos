package org.babelserver.intellijnostos

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.util.PlatformIcons
import org.babelserver.intellijnostos.psi.NostosTypes
import javax.swing.Icon

class NostosStructureViewElement(private val element: PsiElement) :
    StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = element

    override fun navigate(requestFocus: Boolean) {
        (element as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean =
        (element as? NavigatablePsiElement)?.canNavigate() ?: false

    override fun canNavigateToSource(): Boolean =
        (element as? NavigatablePsiElement)?.canNavigateToSource() ?: false

    override fun getAlphaSortKey(): String = getPresentation().presentableText ?: ""

    override fun getPresentation(): ItemPresentation {
        if (element is PsiFile) {
            return element.presentation ?: PresentationData(element.name, null, NostosFileType.icon, null)
        }
        return PresentationData(getPresentableText(), null, getIcon(), null)
    }

    override fun getChildren(): Array<TreeElement> {
        val children = mutableListOf<PsiElement>()
        collectStructureChildren(element, children)
        return children.map { NostosStructureViewElement(it) }.toTypedArray()
    }

    private fun collectStructureChildren(parent: PsiElement, result: MutableList<PsiElement>) {
        for (child in parent.children) {
            val type = child.node.elementType
            when {
                type in DECLARATION_TYPES -> result.add(child)
                type == NostosTypes.TYPE_VARIANT -> result.add(child)
                type == NostosTypes.FN_SIGNATURE -> result.add(child)
                type == NostosTypes.TRAIT_MEMBER -> {
                    // Unwrap trait_member to show the contained declaration directly
                    for (inner in child.children) {
                        val innerType = inner.node.elementType
                        if (innerType in DECLARATION_TYPES || innerType == NostosTypes.FN_SIGNATURE) {
                            result.add(inner)
                            break
                        }
                    }
                }
                type in WRAPPER_TYPES -> collectStructureChildren(child, result)
            }
        }
    }

    private fun getPresentableText(): String {
        val node = element.node
        return when (node.elementType) {
            NostosTypes.FN_DECL -> fnPresentation(node)
            NostosTypes.EXTERN_DECL -> "extern ${fnPresentation(node)}"
            NostosTypes.BARE_FN_DECL -> bareFnPresentation(node)
            NostosTypes.FN_SIGNATURE -> fnPresentation(node)
            NostosTypes.TYPE_DECL -> "type ${tokenText(node, NostosTypes.TYPE_NAME)}"
            NostosTypes.REACTIVE_DECL -> "reactive ${tokenText(node, NostosTypes.TYPE_NAME)}"
            NostosTypes.TRAIT_DECL -> "trait ${tokenText(node, NostosTypes.TYPE_NAME)}"
            NostosTypes.IMPL_DECL -> {
                val names = allTokenTexts(node, NostosTypes.TYPE_NAME)
                if (names.size >= 2) "impl ${names[0]} for ${names[1]}" else "impl ${names.firstOrNull() ?: "?"}"
            }
            NostosTypes.COLON_IMPL_DECL -> {
                val names = allTokenTexts(node, NostosTypes.TYPE_NAME)
                if (names.size >= 2) "${names[0]}: ${names[1]}" else names.firstOrNull() ?: "?"
            }
            NostosTypes.MODULE_DECL -> "module ${tokenText(node, NostosTypes.TYPE_NAME)}"
            NostosTypes.TEST_DECL -> {
                val stringExpr = node.findChildByType(NostosTypes.STRING_EXPR)
                val text = stringExpr?.findChildByType(NostosTypes.STRING)?.text
                "test ${text ?: "?"}"
            }
            NostosTypes.VAR_DECL -> "var ${identText(node)}"
            NostosTypes.MVAR_DECL -> "mvar ${identText(node)}"
            NostosTypes.CONST_DECL -> "const ${identText(node)}"
            NostosTypes.TEMPLATE_DECL -> "template ${fnPresentation(node)}"
            NostosTypes.IMPORT_DECL -> "import ${node.findChildByType(NostosTypes.USE_PATH)?.text ?: "?"}"
            NostosTypes.USE_DECL -> "use ${node.findChildByType(NostosTypes.USE_PATH)?.text ?: "?"}"
            NostosTypes.TYPE_VARIANT -> tokenText(node, NostosTypes.TYPE_NAME)
            else -> element.text.take(30)
        }
    }

    private fun fnPresentation(node: ASTNode): String {
        val name = identText(node)
        val paramList = node.findChildByType(NostosTypes.PARAM_LIST)
        if (paramList != null) {
            val params = paramNames(paramList, NostosTypes.PARAM)
            return "$name(${params.joinToString(", ")})"
        }
        return if (node.findChildByType(NostosTypes.LPAREN) != null) "$name()" else name
    }

    private fun bareFnPresentation(node: ASTNode): String {
        val name = identText(node)
        val hasParen = node.findChildByType(NostosTypes.LPAREN) != null
        if (!hasParen) return name
        val paramList = node.findChildByType(NostosTypes.BARE_PARAM_LIST)
        if (paramList == null) return "$name()"
        val params = paramNames(paramList, NostosTypes.BARE_PARAM)
        return "$name(${params.joinToString(", ")})"
    }

    private fun paramNames(listNode: ASTNode, paramType: IElementType): List<String> {
        val result = mutableListOf<String>()
        var child = listNode.firstChildNode
        while (child != null) {
            if (child.elementType == paramType) {
                val name = firstIdentInTree(child)
                if (name != null) result.add(name)
            }
            child = child.treeNext
        }
        return result
    }

    private fun firstIdentInTree(node: ASTNode): String? {
        if (node.elementType == NostosTypes.IDENTIFIER) return node.text
        if (node.elementType == NostosTypes.FUNCTION_NAME) return node.text
        if (node.elementType == NostosTypes.SELF) return node.text
        var child = node.firstChildNode
        while (child != null) {
            val result = firstIdentInTree(child)
            if (result != null) return result
            child = child.treeNext
        }
        return null
    }

    private fun identText(node: ASTNode): String =
        node.findChildByType(NostosTypes.FUNCTION_NAME)?.text
            ?: node.findChildByType(NostosTypes.IDENTIFIER)?.text
            ?: node.findChildByType(NostosTypes.TYPE_NAME)?.text
            ?: "?"

    private fun tokenText(node: ASTNode, type: IElementType): String =
        node.findChildByType(type)?.text ?: "?"

    private fun allTokenTexts(node: ASTNode, type: IElementType): List<String> {
        val result = mutableListOf<String>()
        var child = node.firstChildNode
        while (child != null) {
            if (child.elementType == type) result.add(child.text)
            child = child.treeNext
        }
        return result
    }

    private fun getIcon(): Icon? {
        return when (element.node.elementType) {
            NostosTypes.FN_DECL, NostosTypes.BARE_FN_DECL -> PlatformIcons.FUNCTION_ICON
            NostosTypes.FN_SIGNATURE -> PlatformIcons.ABSTRACT_METHOD_ICON
            NostosTypes.EXTERN_DECL -> PlatformIcons.FUNCTION_ICON
            NostosTypes.TYPE_DECL, NostosTypes.REACTIVE_DECL -> PlatformIcons.CLASS_ICON
            NostosTypes.TRAIT_DECL -> PlatformIcons.INTERFACE_ICON
            NostosTypes.IMPL_DECL, NostosTypes.COLON_IMPL_DECL -> PlatformIcons.ANONYMOUS_CLASS_ICON
            NostosTypes.MODULE_DECL -> PlatformIcons.PACKAGE_ICON
            NostosTypes.TEST_DECL -> AllIcons.Nodes.Test
            NostosTypes.VAR_DECL, NostosTypes.MVAR_DECL -> PlatformIcons.VARIABLE_ICON
            NostosTypes.CONST_DECL -> PlatformIcons.FIELD_ICON
            NostosTypes.TEMPLATE_DECL -> PlatformIcons.FUNCTION_ICON
            NostosTypes.IMPORT_DECL, NostosTypes.USE_DECL -> AllIcons.Nodes.Include
            NostosTypes.TYPE_VARIANT -> PlatformIcons.ENUM_ICON
            else -> null
        }
    }

    companion object {
        private val DECLARATION_TYPES = setOf(
            NostosTypes.FN_DECL,
            NostosTypes.BARE_FN_DECL,
            NostosTypes.TYPE_DECL,
            NostosTypes.TRAIT_DECL,
            NostosTypes.IMPL_DECL,
            NostosTypes.COLON_IMPL_DECL,
            NostosTypes.MODULE_DECL,
            NostosTypes.TEST_DECL,
            NostosTypes.EXTERN_DECL,
            NostosTypes.VAR_DECL,
            NostosTypes.MVAR_DECL,
            NostosTypes.CONST_DECL,
            NostosTypes.TEMPLATE_DECL,
            NostosTypes.REACTIVE_DECL,
            NostosTypes.IMPORT_DECL,
            NostosTypes.USE_DECL,
        )

        private val WRAPPER_TYPES = setOf(
            NostosTypes.TYPE_BODY,
            NostosTypes.TRAIT_BRACE_BODY,
            NostosTypes.IMPL_BODY,
        )
    }
}
