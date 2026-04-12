package org.babelserver.intellijnostos

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PlatformIcons
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

    override fun getVariants(): Array<Any> {
        val result = mutableListOf<LookupElementBuilder>()
        val seen = mutableSetOf<String>()

        fun addNamed(named: NostosNamedElement) {
            val name = named.name ?: return
            if (!seen.add(name)) return
            result.add(
                LookupElementBuilder.create(named, name)
                    .withIcon(iconFor(named))
                    .withTypeText(typeTextFor(named))
            )
        }

        fun addRaw(name: String, psiElement: PsiElement, typeText: String) {
            if (!seen.add(name)) return
            result.add(
                LookupElementBuilder.create(psiElement, name)
                    .withIcon(PlatformIcons.VARIABLE_ICON)
                    .withTypeText(typeText)
            )
        }

        // Local scope
        collectLocalVariants(element, ::addNamed, ::addRaw)

        // File-level declarations
        collectFileVariants(element.containingFile, ::addNamed)

        // Cross-file declarations
        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        val thisFile = element.containingFile.virtualFile
        val psiManager = PsiManager.getInstance(project)
        for (vFile in FileTypeIndex.getFiles(NostosFileType, scope)) {
            if (vFile == thisFile) continue
            val psiFile = psiManager.findFile(vFile) ?: continue
            collectFileVariants(psiFile, ::addNamed)
        }

        return result.toTypedArray()
    }

    private fun collectLocalVariants(
        from: PsiElement,
        addNamed: (NostosNamedElement) -> Unit,
        addRaw: (String, PsiElement, String) -> Unit,
    ) {
        val refOffset = from.textOffset
        var scope: PsiElement? = from.parent
        while (scope != null && scope !is PsiFile) {
            when (scope) {
                is NostosFnDecl -> addParams(scope.paramList, addNamed)
                is NostosBareFnDecl -> addBareParams(scope.bareParamList, addNamed)
                is NostosExternDecl -> addParams(scope.paramList, addNamed)
                is NostosTemplateDecl -> addParams(scope.paramList, addNamed)
                is NostosFnSignature -> addParams(scope.paramList, addNamed)
                is NostosBackslashLambdaExpr -> {
                    scope.lambdaParams?.let { lambdaParams ->
                        for (child in lambdaParams.node.getChildren(null)) {
                            if (child.elementType == NostosTypes.IDENTIFIER || child.elementType == NostosTypes.FUNCTION_NAME) {
                                addRaw(child.text, child.psi, "parameter")
                            }
                        }
                    }
                }
                is NostosFnLambdaExpr -> addParams(scope.paramList, addNamed)
                is NostosBlockExpr -> {
                    for (child in scope.children) {
                        if (child.textOffset >= refOffset) break
                        if (child is NostosNamedElement) addNamed(child)
                    }
                }
                is NostosForExpr -> {
                    val pattern = PsiTreeUtil.findChildOfType(scope, NostosPattern::class.java)
                    addPatternBindings(pattern, addNamed)
                }
                is NostosBraceMatchArm, is NostosMatchArm -> {
                    val pattern = PsiTreeUtil.findChildOfType(scope, NostosPattern::class.java)
                    addPatternBindings(pattern, addNamed)
                }
            }
            scope = scope.parent
        }
    }

    private fun collectFileVariants(file: PsiFile, add: (NostosNamedElement) -> Unit) {
        for (child in file.children) {
            if (child is NostosNamedElement) add(child)
            if (child is NostosModuleDecl) {
                for (moduleChild in child.children) {
                    if (moduleChild is NostosNamedElement) add(moduleChild)
                }
            }
        }
    }

    private fun addParams(paramList: NostosParamList?, add: (NostosNamedElement) -> Unit) {
        if (paramList == null) return
        for (param in paramList.paramList) {
            (param as? NostosNamedElement)?.let(add)
        }
    }

    private fun addBareParams(bareParamList: NostosBareParamList?, add: (NostosNamedElement) -> Unit) {
        if (bareParamList == null) return
        for (param in bareParamList.bareParamList) {
            (param as? NostosNamedElement)?.let(add)
        }
    }

    private fun addPatternBindings(pattern: PsiElement?, add: (NostosNamedElement) -> Unit) {
        if (pattern == null) return
        for (vp in PsiTreeUtil.findChildrenOfType(pattern, NostosVarPattern::class.java)) {
            (vp as? NostosNamedElement)?.let(add)
        }
    }

    private fun iconFor(element: NostosNamedElement): javax.swing.Icon? {
        return when (element.node.elementType) {
            NostosTypes.FN_DECL, NostosTypes.BARE_FN_DECL, NostosTypes.TEMPLATE_DECL -> PlatformIcons.FUNCTION_ICON
            NostosTypes.EXTERN_DECL -> PlatformIcons.FUNCTION_ICON
            NostosTypes.VAR_DECL, NostosTypes.MVAR_DECL, NostosTypes.PARAM, NostosTypes.BARE_PARAM -> PlatformIcons.VARIABLE_ICON
            NostosTypes.CONST_DECL -> PlatformIcons.FIELD_ICON
            NostosTypes.MODULE_DECL -> PlatformIcons.PACKAGE_ICON
            else -> null
        }
    }

    private fun typeTextFor(element: NostosNamedElement): String? {
        return when (element.node.elementType) {
            NostosTypes.FN_DECL, NostosTypes.BARE_FN_DECL -> "function"
            NostosTypes.EXTERN_DECL -> "extern"
            NostosTypes.TEMPLATE_DECL -> "template"
            NostosTypes.VAR_DECL -> "var"
            NostosTypes.MVAR_DECL -> "mvar"
            NostosTypes.CONST_DECL -> "const"
            NostosTypes.PARAM, NostosTypes.BARE_PARAM -> "parameter"
            NostosTypes.MODULE_DECL -> "module"
            else -> null
        }
    }

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
