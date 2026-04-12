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

    // ==================== Symbol Completion ====================

    fun testFunctionCompletion() {
        myFixture.configureByText("test.nos", """
            fn helper(x: Int): Int = x + 1
            fn helpText(): String = "hi"
            result = hel<caret>
        """.trimIndent())
        val lookups = myFixture.completeBasic()
        assertNotNull("Expected completion results", lookups)
        val texts = lookups.map { it.lookupString }
        assertContainsElements(texts, "helper", "helpText")
    }

    fun testBareFunctionCompletion() {
        myFixture.configureByText("test.nos", """
            double(n) = n * 2
            doubly(n) = n * 4
            result = dou<caret>
        """.trimIndent())
        val lookups = myFixture.completeBasic()
        assertNotNull("Expected completion results", lookups)
        val texts = lookups.map { it.lookupString }
        assertContainsElements(texts, "double", "doubly")
    }

    fun testVariableCompletion() {
        myFixture.configureByText("test.nos", """
            var counter = 0
            var countdown = 10
            result = cou<caret>
        """.trimIndent())
        val lookups = myFixture.completeBasic()
        assertNotNull("Expected completion results", lookups)
        val texts = lookups.map { it.lookupString }
        assertContainsElements(texts, "counter", "countdown")
    }

    fun testConstantCompletion() {
        myFixture.configureByText("test.nos", """
            const max_size = 100
            const max_value = 255
            result = max<caret>
        """.trimIndent())
        val lookups = myFixture.completeBasic()
        assertNotNull("Expected completion results", lookups)
        val texts = lookups.map { it.lookupString }
        assertContainsElements(texts, "max_size", "max_value")
    }

    fun testParameterCompletion() {
        myFixture.configureByText("test.nos", """
            fn compute(value: Int, valid: Bool): Int = val<caret>
        """.trimIndent())
        val lookups = myFixture.completeBasic()
        assertNotNull("Expected completion results", lookups)
        val texts = lookups.map { it.lookupString }
        assertContainsElements(texts, "value", "valid")
    }

    fun testTypeCompletion() {
        myFixture.configureByText("test.nos", """
            type Colour = Red | Green | Blue
            type Collection = Empty | Single
            fn paint(c: Col<caret>)
        """.trimIndent())
        val lookups = myFixture.completeBasic()
        assertNotNull("Expected completion results", lookups)
        val texts = lookups.map { it.lookupString }
        assertContainsElements(texts, "Colour", "Collection")
    }

    fun testTraitCompletion() {
        myFixture.configureByText("test.nos", """
            trait Printable {
                fn show(self): String
            }
            trait Provable {
                fn prove(self): Bool
            }
            fn display(p: Pr<caret>)
        """.trimIndent())
        val lookups = myFixture.completeBasic()
        assertNotNull("Expected completion results", lookups)
        val texts = lookups.map { it.lookupString }
        assertContainsElements(texts, "Printable", "Provable")
    }

    fun testCrossFileCompletion() {
        myFixture.configureByText("helpers.nos", """
            fn helper(): Int = 42
            fn helpFormat(): String = "fmt"
        """.trimIndent())
        myFixture.configureByText("main.nos", """
            result = hel<caret>
        """.trimIndent())
        val lookups = myFixture.completeBasic()
        assertNotNull("Expected completion results", lookups)
        val texts = lookups.map { it.lookupString }
        assertContainsElements(texts, "helper", "helpFormat")
    }

    fun testFileLevelDeclarationsAlwaysVisible() {
        myFixture.configureByText("test.nos", """
            result = lat<caret>
            fn latitude(): Int = 0
            fn lateral(): Int = 1
        """.trimIndent())
        val lookups = myFixture.completeBasic()
        // File-level declarations are visible regardless of position
        assertNotNull("Expected completion results", lookups)
        val texts = lookups.map { it.lookupString }
        assertContainsElements(texts, "latitude", "lateral")
    }
}
