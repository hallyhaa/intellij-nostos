package org.babelserver.intellijnostos

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class NostosFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NostosLanguage) {

    override fun getFileType(): FileType = NostosFileType
    override fun toString(): String = "Nostos File"
}
