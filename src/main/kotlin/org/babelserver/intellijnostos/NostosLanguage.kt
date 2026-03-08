package org.babelserver.intellijnostos

import com.intellij.lang.Language

object NostosLanguage : Language("Nostos") {

    @Suppress("unused") // deserialization safe singleton
    private fun readResolve(): Any = NostosLanguage

}
