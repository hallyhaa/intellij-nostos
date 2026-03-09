package org.babelserver.intellijnostos

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object NostosFileType : LanguageFileType(NostosLanguage) {
    override fun getName(): String = "Nostos"
    override fun getDescription(): String = "Nostos language file"
    override fun getDefaultExtension(): String = "nos"
    override fun getIcon(): Icon = IconLoader.getIcon("/icons/nostos.svg", NostosFileType::class.java)
}
