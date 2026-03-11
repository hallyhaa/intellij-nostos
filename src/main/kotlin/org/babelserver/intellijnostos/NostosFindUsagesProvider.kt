package org.babelserver.intellijnostos

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.babelserver.intellijnostos.psi.*

class NostosFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner {
        return DefaultWordsScanner(
            NostosLexerAdapter(),
            IDENTIFIER_TOKENS,
            COMMENT_TOKENS,
            STRING_TOKENS
        )
    }

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean =
        psiElement is NostosNamedElement

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String {
        return when (element) {
            is NostosFnDecl, is NostosBareFnDecl, is NostosFnSignature -> "function"
            is NostosExternDecl -> "extern function"
            is NostosTemplateDecl -> "template"
            is NostosTypeDecl -> "type"
            is NostosTraitDecl -> "trait"
            is NostosReactiveDecl -> "reactive type"
            is NostosModuleDecl -> "module"
            is NostosImplDecl, is NostosColonImplDecl -> "impl"
            is NostosVarDecl -> "variable"
            is NostosMvarDecl -> "mutable variable"
            is NostosConstDecl -> "constant"
            is NostosParam, is NostosBareParam -> "parameter"
            is NostosTypeVariant -> "type variant"
            else -> "element"
        }
    }

    override fun getDescriptiveName(element: PsiElement): String =
        (element as? NostosNamedElement)?.name ?: element.text

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        (element as? NostosNamedElement)?.name ?: element.text

    companion object {
        private val IDENTIFIER_TOKENS = TokenSet.create(
            NostosTypes.IDENTIFIER,
            NostosTypes.FUNCTION_NAME,
            NostosTypes.TYPE_NAME,
        )
        private val COMMENT_TOKENS = TokenSet.create(
            NostosTypes.COMMENT,
            NostosTypes.BLOCK_COMMENT,
        )
        private val STRING_TOKENS = TokenSet.create(
            NostosTypes.STRING,
        )
    }
}
