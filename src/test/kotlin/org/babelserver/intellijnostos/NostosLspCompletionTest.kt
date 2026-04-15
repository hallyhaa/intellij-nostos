package org.babelserver.intellijnostos

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PlatformIcons
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat

class NostosLspCompletionTest : BasePlatformTestCase() {

    // ==================== plainInsertText ====================

    fun testPlainTextReturnsRaw() {
        val item = CompletionItem("score").apply { insertText = "score" }
        assertEquals("score", NostosLspCompletionContributor.plainInsertText(item))
    }

    fun testPlainTextFallsBackToLabel() {
        val item = CompletionItem("score")
        assertEquals("score", NostosLspCompletionContributor.plainInsertText(item))
    }

    fun testSnippetPlaceholdersStripped() {
        val item = CompletionItem("doSomething").apply {
            insertText = "doSomething(\${1:arg1}, \${2:arg2})"
            insertTextFormat = InsertTextFormat.Snippet
        }
        assertEquals("doSomething(arg1, arg2)", NostosLspCompletionContributor.plainInsertText(item))
    }

    fun testSnippetTabstopsStripped() {
        val item = CompletionItem("value").apply {
            insertText = "value\$1"
            insertTextFormat = InsertTextFormat.Snippet
        }
        assertEquals("value", NostosLspCompletionContributor.plainInsertText(item))
    }

    fun testNonSnippetDollarPreserved() {
        val item = CompletionItem("price").apply {
            insertText = "price\$1"
            insertTextFormat = InsertTextFormat.PlainText
        }
        assertEquals("price\$1", NostosLspCompletionContributor.plainInsertText(item))
    }

    // ==================== lspKindToPriority ====================

    fun testFieldPriorityHighest() {
        assertEquals(20.0, NostosLspCompletionContributor.lspKindToPriority(CompletionItemKind.Field))
        assertEquals(20.0, NostosLspCompletionContributor.lspKindToPriority(CompletionItemKind.Property))
    }

    fun testMethodPriorityAboveFunction() {
        val method = NostosLspCompletionContributor.lspKindToPriority(CompletionItemKind.Method)
        val function = NostosLspCompletionContributor.lspKindToPriority(CompletionItemKind.Function)
        assertTrue("Method ($method) should be > Function ($function)", method > function)
    }

    fun testUnknownKindPriorityZero() {
        assertEquals(0.0, NostosLspCompletionContributor.lspKindToPriority(null))
        assertEquals(0.0, NostosLspCompletionContributor.lspKindToPriority(CompletionItemKind.Keyword))
    }

    // ==================== lspKindToIcon ====================

    fun testFieldIcon() {
        assertEquals(PlatformIcons.FIELD_ICON, NostosLspCompletionContributor.lspKindToIcon(CompletionItemKind.Field))
    }

    fun testFunctionIcon() {
        assertEquals(PlatformIcons.FUNCTION_ICON, NostosLspCompletionContributor.lspKindToIcon(CompletionItemKind.Function))
    }

    fun testNullKindReturnsNull() {
        assertNull(NostosLspCompletionContributor.lspKindToIcon(null))
    }

    // ==================== processCompletionItems ====================

    fun testFiltersTypeAnnotations() {
        val items = listOf(
            CompletionItem(": GameState"),
            CompletionItem("scoreBoard").apply { kind = CompletionItemKind.Field },
            CompletionItem(": Int"),
        )
        val result = NostosLspCompletionContributor.processCompletionItems(items)
        assertEquals(1, result.size)
        assertEquals("scoreBoard", result[0].label)
    }

    fun testSortsFieldsBeforeMethods() {
        val items = listOf(
            CompletionItem("show").apply { kind = CompletionItemKind.Method },
            CompletionItem("hash").apply { kind = CompletionItemKind.Method },
            CompletionItem("board").apply { kind = CompletionItemKind.Field },
            CompletionItem("colourInTurn").apply { kind = CompletionItemKind.Field },
        )
        val result = NostosLspCompletionContributor.processCompletionItems(items)
        assertEquals("board", result[0].label)
        assertEquals("colourInTurn", result[1].label)
        assertEquals("hash", result[2].label)
        assertEquals("show", result[3].label)
    }

    fun testSortsBySortTextWithinSameKind() {
        val items = listOf(
            CompletionItem("zebra").apply { kind = CompletionItemKind.Field; sortText = "2" },
            CompletionItem("alpha").apply { kind = CompletionItemKind.Field; sortText = "1" },
            CompletionItem("middle").apply { kind = CompletionItemKind.Field; sortText = "3" },
        )
        val result = NostosLspCompletionContributor.processCompletionItems(items)
        assertEquals("alpha", result[0].label)
        assertEquals("zebra", result[1].label)
        assertEquals("middle", result[2].label)
    }

    fun testSortFallsBackToLabelWhenNoSortText() {
        val items = listOf(
            CompletionItem("zebra").apply { kind = CompletionItemKind.Field },
            CompletionItem("alpha").apply { kind = CompletionItemKind.Field },
        )
        val result = NostosLspCompletionContributor.processCompletionItems(items)
        assertEquals("alpha", result[0].label)
        assertEquals("zebra", result[1].label)
    }

    fun testEmptyListReturnsEmpty() {
        val result = NostosLspCompletionContributor.processCompletionItems(emptyList())
        assertTrue(result.isEmpty())
    }

    fun testAllTypeAnnotationsFilteredReturnsEmpty() {
        val items = listOf(CompletionItem(": Foo"), CompletionItem(": Bar"))
        val result = NostosLspCompletionContributor.processCompletionItems(items)
        assertTrue(result.isEmpty())
    }

    // ==================== Dot detection (via fixture) ====================

    fun testNoDotNoLspCompletion() {
        myFixture.configureByText("test.nos", "x<caret>")
        val lookups = myFixture.completeBasic()
        // Without a dot, only keyword completions should appear (no LSP items)
        if (lookups != null) {
            val texts = lookups.map { it.lookupString }
            assertDoesntContain(texts, "scoreBoard", "colourInTurn")
        }
    }
}
