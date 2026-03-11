package org.babelserver.intellijnostos

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.psi.PsiFile

class NostosStructureViewModel(psiFile: PsiFile) : StructureViewModelBase(
    psiFile, NostosStructureViewElement(psiFile)
), StructureViewModel.ElementInfoProvider {

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean {
        val value = element.value
        return value !is PsiFile && value !is org.babelserver.intellijnostos.psi.NostosModuleDecl
                && value !is org.babelserver.intellijnostos.psi.NostosTypeDecl
                && value !is org.babelserver.intellijnostos.psi.NostosTraitDecl
                && value !is org.babelserver.intellijnostos.psi.NostosImplDecl
                && value !is org.babelserver.intellijnostos.psi.NostosColonImplDecl
    }
}
