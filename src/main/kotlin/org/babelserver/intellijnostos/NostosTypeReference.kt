package org.babelserver.intellijnostos

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.util.PlatformIcons
import org.babelserver.intellijnostos.psi.*

/**
 * Reference from a TYPE_NAME token in type positions (simple_type, generic_type,
 * constructor_pattern, trait_bound_list) to type_decl, trait_decl, reactive_decl,
 * type_variant, or module_decl.
 */
class NostosTypeReference(element: PsiElement, private val nameText: String) :
    PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength)) {

    override fun resolve(): PsiElement? =
        ResolveCache.getInstance(element.project).resolveWithCaching(this, RESOLVER, false, false)

    private fun doResolve(): PsiElement? =
        resolveInFile(element.containingFile) ?: resolveAcrossFiles()

    override fun getVariants(): Array<Any> {
        val result = mutableListOf<LookupElementBuilder>()
        val seen = mutableSetOf<String>()

        fun add(named: NostosNamedElement, typeText: String, icon: javax.swing.Icon?) {
            val name = named.name ?: return
            if (!seen.add(name)) return
            result.add(LookupElementBuilder.create(named, name).withIcon(icon).withTypeText(typeText))
        }

        collectTypeVariants(element.containingFile, ::add)

        val thisFile = element.containingFile
        for (psiFile in NostosResolveSupport.projectNosFiles(element.project)) {
            if (psiFile == thisFile) continue
            collectTypeVariants(psiFile, ::add)
        }

        return result.toTypedArray()
    }

    private fun collectTypeVariants(
        file: PsiFile,
        add: (NostosNamedElement, String, javax.swing.Icon?) -> Unit,
    ) {
        for (child in file.children) {
            collectTypeDecl(child, add)
            if (child is NostosModuleDecl) {
                add(child as NostosNamedElement, "module", PlatformIcons.PACKAGE_ICON)
                for (moduleChild in child.children) {
                    collectTypeDecl(moduleChild, add)
                }
            }
        }
    }

    private fun collectTypeDecl(
        element: PsiElement,
        add: (NostosNamedElement, String, javax.swing.Icon?) -> Unit,
    ) {
        when (element) {
            is NostosTypeDecl -> {
                add(element as NostosNamedElement, "type", PlatformIcons.CLASS_ICON)
                element.typeBody?.typeVariantList?.forEach { variant ->
                    add(variant as NostosNamedElement, "variant", PlatformIcons.ENUM_ICON)
                }
            }
            is NostosTraitDecl -> add(element as NostosNamedElement, "trait", PlatformIcons.INTERFACE_ICON)
            is NostosReactiveDecl -> add(element as NostosNamedElement, "reactive", PlatformIcons.CLASS_ICON)
        }
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val typeNameNode = element.node.findChildByType(NostosTypes.TYPE_NAME) ?: return element
        val newNode = org.babelserver.intellijnostos.psi.impl.NostosElementFactory
            .createIdentifier(element.project, newElementName, NostosTypes.TYPE_NAME)
        typeNameNode.treeParent.replaceChild(typeNameNode, newNode)
        return element
    }

    private fun resolveInFile(file: PsiFile): PsiElement? {
        for (child in file.children) {
            when (child) {
                is NostosTypeDecl -> {
                    if ((child as NostosNamedElement).name == nameText) return child
                    // Also check type variants
                    child.typeBody?.typeVariantList?.forEach { variant ->
                        if ((variant as NostosNamedElement).name == nameText) return variant
                    }
                }
                is NostosTraitDecl -> {
                    if ((child as NostosNamedElement).name == nameText) return child
                }
                is NostosReactiveDecl -> {
                    if ((child as NostosNamedElement).name == nameText) return child
                }
                is NostosModuleDecl -> {
                    if ((child as NostosNamedElement).name == nameText) return child
                    findInModule(child)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findInModule(module: NostosModuleDecl): PsiElement? {
        for (child in module.children) {
            when (child) {
                is NostosTypeDecl -> {
                    if ((child as NostosNamedElement).name == nameText) return child
                    child.typeBody?.typeVariantList?.forEach { variant ->
                        if ((variant as NostosNamedElement).name == nameText) return variant
                    }
                }
                is NostosTraitDecl -> {
                    if ((child as NostosNamedElement).name == nameText) return child
                }
                is NostosReactiveDecl -> {
                    if ((child as NostosNamedElement).name == nameText) return child
                }
            }
        }
        return null
    }

    private fun resolveAcrossFiles(): PsiElement? {
        val thisFile = element.containingFile
        for (psiFile in NostosResolveSupport.projectNosFiles(element.project)) {
            if (psiFile == thisFile) continue
            resolveInFile(psiFile)?.let { return it }
        }
        return null
    }

    private companion object {
        private val RESOLVER = ResolveCache.Resolver { ref, _ -> (ref as NostosTypeReference).doResolve() }
    }
}
