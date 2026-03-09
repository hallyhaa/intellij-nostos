package org.babelserver.intellijnostos.psi

import com.intellij.psi.tree.IElementType
import org.babelserver.intellijnostos.NostosLanguage

class NostosElementType(debugName: String) : IElementType(debugName, NostosLanguage) {
    override fun toString(): String = "NostosElementType.${super.toString()}"
}
