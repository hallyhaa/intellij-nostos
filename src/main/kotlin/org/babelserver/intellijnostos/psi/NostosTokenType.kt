package org.babelserver.intellijnostos.psi

import com.intellij.psi.tree.IElementType
import org.babelserver.intellijnostos.NostosLanguage

class NostosTokenType(debugName: String) : IElementType(debugName, NostosLanguage) {

    /**
     * Used both for PSI debugging and, more visibly, for the "X, Y or Z
     * expected" parser error messages (IDEA builds those from each expected
     * element type's toString). Render category tokens (ALL_CAPS debug names
     * such as IDENTIFIER) in lower case, and keyword/operator tokens (which
     * carry their literal text as the debug name, e.g. "fn", "->") quoted.
     * This matches how the Java and Kotlin parsers phrase the same errors:
     * "identifier, 'fn', 'trait' or 'type' expected".
     */
    override fun toString(): String {
        val name = super.toString()
        return if (name.matches(CATEGORY_NAME)) {
            name.lowercase().replace('_', ' ')
        } else {
            "'$name'"
        }
    }

    companion object {
        /** ALL_CAPS token names denote a category (IDENTIFIER, STRING, ...). */
        private val CATEGORY_NAME = Regex("[A-Z][A-Z0-9_]*")
    }
}
