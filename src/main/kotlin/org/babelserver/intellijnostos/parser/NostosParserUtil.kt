package org.babelserver.intellijnostos.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase

@Suppress("unused")
class NostosParserUtil : GeneratedParserUtilBase() {
    companion object {
        /**
         * Parses an expression with priority 0 (excludes fat_arrow_expr at priority -1).
         * Used for guard expressions in match arms so that `=>` is not consumed
         * as part of the guard but left as the arm separator.
         */
        @JvmStatic
        fun parseGuardExpr(builder: PsiBuilder, level: Int): Boolean {
            return NostosParser.expr(builder, level, 0)
        }
    }
}
