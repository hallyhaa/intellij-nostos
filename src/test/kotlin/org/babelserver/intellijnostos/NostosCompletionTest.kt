package org.babelserver.intellijnostos

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NostosCompletionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    fun testKeywordCompletion() {
        myFixture.configureByText("test.nos", "f<caret>")
        val lookups = myFixture.completeBasic()
        assertNotNull("Expected completion results", lookups)
        val texts = lookups.map { it.lookupString }
        assertContainsElements(texts, "fn", "for", "false", "finally", "from")
    }

    fun testKeywordCompletionMatch() {
        myFixture.configureByText("test.nos", "sp<caret>")
        val lookups = myFixture.completeBasic()
        assertNotNull("Expected completion results", lookups)
        val texts = lookups.map { it.lookupString }
        assertContainsElements(texts, "spawn", "spawn_link", "spawn_monitor")
        assertDoesntContain(texts, "fn", "if", "for")
    }

    fun testAllKeywordsPresent() {
        myFixture.configureByText("test.nos", "<caret>")
        val lookups = myFixture.completeBasic()
        assertNotNull("Expected completion results", lookups)
        val texts = lookups.map { it.lookupString }
        // Declarations
        assertContainsElements(texts, "fn", "type", "trait", "impl", "module", "var", "const")
        // Control flow
        assertContainsElements(texts, "if", "match", "for", "while", "return")
        // Error handling
        assertContainsElements(texts, "try", "catch", "throw")
        // Concurrency
        assertContainsElements(texts, "spawn", "receive")
        // Literals
        assertContainsElements(texts, "true", "false", "self")
    }

    fun testKeywordInsertsSuffix() {
        myFixture.configureByText("test.nos", "fn<caret>")
        myFixture.completeBasic()
        // After inserting "fn", a space should follow
        myFixture.checkResult("fn ")
    }
}
