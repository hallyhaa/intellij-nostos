package com.github.hallyhaa.intellijnostos

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object NostosFileType : LanguageFileType(NostosLanguage) {
    override fun getName(): String = "Nostos"
    override fun getDescription(): String = "Nostos language file"
    override fun getDefaultExtension(): String = "nos"
    override fun getIcon(): Icon? = null
}
