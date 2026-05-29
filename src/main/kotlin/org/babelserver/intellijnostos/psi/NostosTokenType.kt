package org.babelserver.intellijnostos.psi

import com.intellij.psi.tree.IElementType
import org.babelserver.intellijnostos.NostosLanguage

class NostosTokenType(debugName: String) : IElementType(debugName, NostosLanguage) {

    /**
     * Used both for PSI debugging and, more visibly, for the "X, Y or Z
     * expected" parser error messages (IDEA builds those from each expected
     * element type's toString). Render category tokens (ALL_CAPS debug names
     * such as IDENTIFIER) in lower case so they read as "identifier" rather
     * than "IDENTIFIER".
     *
     * Keyword/operator tokens (whose debug name is their literal text, e.g.
     * "fn", ")") are returned verbatim. We must NOT quote them here: the
     * parser's expected-tokens formatter already wraps any name that does not
     * start with an identifier character in single quotes, so pre-quoting
     * produced doubled quotes like ''self'' and '')'' in error messages.
     * Leaving them bare yields "')' or <param> expected" and
     * "self, function name or identifier expected".
     */
    override fun toString(): String {
        val name = super.toString()
        return if (name.matches(CATEGORY_NAME)) {
            name.lowercase().replace('_', ' ')
        } else {
            name
        }
    }

    companion object {
        /** ALL_CAPS token names denote a category (IDENTIFIER, STRING, ...). */
        private val CATEGORY_NAME = Regex("[A-Z][A-Z0-9_]*")
    }
}
