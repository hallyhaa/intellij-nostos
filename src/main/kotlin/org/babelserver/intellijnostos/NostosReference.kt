package org.babelserver.intellijnostos

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.babelserver.intellijnostos.psi.*

/**
 * Reference from a ref_expr (identifier usage) to its declaration.
 * Resolves IDENTIFIER and FUNCTION_NAME tokens to fn_decl, bare_fn_decl,
 * var_decl, const_decl, mvar_decl, param, bare_param, etc.
 */
class NostosReference(element: PsiElement, private val nameText: String) :
    PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength)) {

    override fun resolve(): PsiElement? {
        return resolveLocal(element) ?: resolveInFile(element.containingFile) ?: resolveAcrossFiles()
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun handleElementRename(newElementName: String): PsiElement {
        val nameNode = element.node.findChildByType(NostosTypes.IDENTIFIER)
            ?: element.node.findChildByType(NostosTypes.FUNCTION_NAME)
            ?: return element
        val newNode = org.babelserver.intellijnostos.psi.impl.NostosElementFactory
            .createIdentifier(element.project, newElementName, nameNode.elementType)
        nameNode.treeParent.replaceChild(nameNode, newNode)
        return element
    }

    /**
     * Walk up the PSI tree looking for local declarations: block scope,
     * function params, for-loop variables, match arm bindings, lambda params.
     */
    private fun resolveLocal(from: PsiElement): PsiElement? {
        var scope: PsiElement? = from.parent
        while (scope != null && scope !is PsiFile) {
            when (scope) {
                // Function params
                is NostosFnDecl -> {
                    findInParamList(scope.paramList)?.let { return it }
                }
                is NostosBareFnDecl -> {
                    findInBareParamList(scope.bareParamList)?.let { return it }
                }
                is NostosExternDecl -> {
                    findInParamList(scope.paramList)?.let { return it }
                }
                is NostosTemplateDecl -> {
                    findInParamList(scope.paramList)?.let { return it }
                }
                is NostosFnSignature -> {
                    findInParamList(scope.paramList)?.let { return it }
                }
                // Lambda params
                is NostosBackslashLambdaExpr -> {
                    scope.lambdaParams?.let { lambdaParams ->
                        for (child in lambdaParams.node.getChildren(null)) {
                            if ((child.elementType == NostosTypes.IDENTIFIER || child.elementType == NostosTypes.FUNCTION_NAME)
                                && child.text == nameText
                            ) return child.psi
                        }
                    }
                }
                is NostosFnLambdaExpr -> {
                    findInParamList(scope.paramList)?.let { return it }
                }
                // Block expression — look for bare_fn_decl and var/mvar/const before our position
                is NostosBlockExpr -> {
                    findDeclBefore(scope, from)?.let { return it }
                }
                // For-loop bindings
                is NostosForExpr -> {
                    val pattern = PsiTreeUtil.findChildOfType(scope, NostosPattern::class.java)
                    findInPattern(pattern)?.let { return it }
                }
                // Match arm pattern bindings
                is NostosBraceMatchArm -> {
                    val pattern = PsiTreeUtil.findChildOfType(scope, NostosPattern::class.java)
                    findInPattern(pattern)?.let { return it }
                }
                is NostosMatchArm -> {
                    val pattern = PsiTreeUtil.findChildOfType(scope, NostosPattern::class.java)
                    findInPattern(pattern)?.let { return it }
                }
            }
            scope = scope.parent
        }
        return null
    }

    private fun findInParamList(paramList: NostosParamList?): PsiElement? {
        if (paramList == null) return null
        for (param in paramList.paramList) {
            if ((param as? NostosNamedElement)?.name == nameText) return param
        }
        return null
    }

    private fun findInBareParamList(bareParamList: NostosBareParamList?): PsiElement? {
        if (bareParamList == null) return null
        for (param in bareParamList.bareParamList) {
            if ((param as? NostosNamedElement)?.name == nameText) return param
        }
        return null
    }

    private fun findInPattern(pattern: PsiElement?): PsiElement? {
        if (pattern == null) return null
        val varPatterns = PsiTreeUtil.findChildrenOfType(pattern, NostosVarPattern::class.java)
        for (vp in varPatterns) {
            if (vp.text == nameText) return vp
        }
        return null
    }

    /**
     * Look for declarations in a block that appear before the reference.
     */
    private fun findDeclBefore(block: PsiElement, ref: PsiElement): PsiElement? {
        val refOffset = ref.textOffset
        for (child in block.children) {
            if (child.textOffset >= refOffset) break
            if (child is NostosNamedElement && child.name == nameText) return child
        }
        return null
    }

    /**
     * Resolve at file level — look for all top-level declarations.
     */
    private fun resolveInFile(file: PsiFile): PsiElement? {
        for (child in file.children) {
            if (child is NostosNamedElement && child.name == nameText) return child
            // Also check inside modules
            if (child is NostosModuleDecl) {
                findInModule(child)?.let { return it }
            }
        }
        return null
    }

    private fun findInModule(module: NostosModuleDecl): PsiElement? {
        for (child in module.children) {
            if (child is NostosNamedElement && child.name == nameText) return child
        }
        return null
    }

    /**
     * Cross-file resolution: scan all .nos files in the project.
     */
    private fun resolveAcrossFiles(): PsiElement? {
        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)
        val thisFile = element.containingFile.virtualFile

        for (vFile in FileTypeIndex.getFiles(NostosFileType, scope)) {
            if (vFile == thisFile) continue
            val psiFile = psiManager.findFile(vFile) ?: continue
            resolveInFile(psiFile)?.let { return it }
        }
        return null
    }
}
