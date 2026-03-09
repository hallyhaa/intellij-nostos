package org.babelserver.intellijnostos

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NostosCommenterTest : BasePlatformTestCase() {

    fun testLineCommentAdded() {
        myFixture.configureByText("test.nos", "x = 5<caret>")
        myFixture.performEditorAction("CommentByLineComment")
        assertEquals("#x = 5", myFixture.editor.document.text)
    }

    fun testLineCommentRemoved() {
        myFixture.configureByText("test.nos", "#x = 5<caret>")
        myFixture.performEditorAction("CommentByLineComment")
        assertEquals("x = 5", myFixture.editor.document.text)
    }

    fun testBlockCommentAdded() {
        myFixture.configureByText("test.nos", "<selection>x = 5</selection>")
        myFixture.performEditorAction("CommentByBlockComment")
        val text = myFixture.editor.document.text
        assertTrue("Should contain block comment markers, got: $text",
            text.contains("#*") && text.contains("*#") && text.contains("x = 5"))
    }

    fun testBlockCommentRemoved() {
        myFixture.configureByText("test.nos", "<selection>#* x = 5 *#</selection>")
        myFixture.performEditorAction("CommentByBlockComment")
        val text = myFixture.editor.document.text.trim()
        assertEquals("x = 5", text)
    }
}
