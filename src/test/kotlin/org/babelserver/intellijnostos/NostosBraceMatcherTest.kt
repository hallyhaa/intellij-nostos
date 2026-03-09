package org.babelserver.intellijnostos

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NostosBraceMatcherTest : BasePlatformTestCase() {

    private fun doMatchBrace(before: String, after: String) {
        myFixture.configureByText("test.nos", before)
        myFixture.performEditorAction("EditorMatchBrace")
        myFixture.checkResult(after)
    }

    // ==================== Parentheses ====================

    fun testMatchParenForward() {
        doMatchBrace("foo<caret>(x, y)", "foo(x, y)<caret>")
    }

    fun testMatchParenBackward() {
        doMatchBrace("foo(x, y<caret>)", "foo<caret>(x, y)")
    }

    // ==================== Square brackets ====================

    fun testMatchBracketForward() {
        doMatchBrace("<caret>[1, 2, 3]", "[1, 2, 3]<caret>")
    }

    fun testMatchBracketBackward() {
        doMatchBrace("[1, 2, 3<caret>]", "<caret>[1, 2, 3]")
    }

    // ==================== Curly braces ====================

    fun testMatchBraceForward() {
        doMatchBrace("spawn <caret>{ worker() }", "spawn { worker() }<caret>")
    }

    fun testMatchBraceBackward() {
        doMatchBrace("spawn { worker() <caret>}", "spawn <caret>{ worker() }")
    }

    // ==================== Nested ====================

    fun testMatchNestedParens() {
        doMatchBrace("foo<caret>(bar(x), y)", "foo(bar(x), y)<caret>")
    }

    fun testMatchInnerParen() {
        doMatchBrace("foo(<caret>(x), y)", "foo((x)<caret>, y)")
    }
}
