package org.babelserver.intellijnostos

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class NostosExternalAnnotatorTest : BasePlatformTestCase() {

    private val annotator = NostosExternalAnnotator()

    // ==================== collectInformation ====================

    fun testCollectInfoNonNostosFile() {
        val file = myFixture.configureByText("test.txt", "hello")
        val editor = myFixture.editor
        val info = annotator.collectInformation(file, editor, false)
        assertNull(info)
    }

    fun testCollectInfoNostosFile() {
        val file = myFixture.configureByText("test.nos", "x = 1")
        val editor = myFixture.editor
        val info = annotator.collectInformation(file, editor, false)
        assertNotNull(info)
        assertTrue(info!!.fileUri.endsWith("test.nos"))
    }

    // ==================== Diagnostics cache ====================

    fun testDiagnosticsCacheEmpty() {
        val diagnostics = NostosDiagnosticsCache.cache["file:///nonexistent.nos"]
        assertNull(diagnostics)
    }

    fun testDiagnosticsCacheStore() {
        val uri = "file:///test.nos"
        val diag = Diagnostic(
            Range(Position(0, 0), Position(0, 5)),
            "test error"
        ).apply { severity = DiagnosticSeverity.Error }

        NostosDiagnosticsCache.cache[uri] = listOf(diag)
        val result = NostosDiagnosticsCache.cache[uri]
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals("test error", result[0].message)
        assertEquals(DiagnosticSeverity.Error, result[0].severity)

        NostosDiagnosticsCache.cache.remove(uri)
    }

    // ==================== apply with diagnostics ====================

    fun testApplyErrorDiagnostic() {
        val file = myFixture.configureByText("test.nos", "x = nonexistent + 1")

        val uri = "file://${file.virtualFile.path}"
        val diag = Diagnostic(
            Range(Position(0, 4), Position(0, 15)),
            "unknown variable `nonexistent`"
        ).apply { severity = DiagnosticSeverity.Error }

        NostosDiagnosticsCache.cache[uri] = listOf(diag)

        myFixture.doHighlighting()

        NostosDiagnosticsCache.cache.remove(uri)
    }

    fun testApplyWarningDiagnostic() {
        val file = myFixture.configureByText("test.nos", "x = 1 / 0")

        val uri = "file://${file.virtualFile.path}"
        val diag = Diagnostic(
            Range(Position(0, 4), Position(0, 9)),
            "division by zero"
        ).apply { severity = DiagnosticSeverity.Warning }

        NostosDiagnosticsCache.cache[uri] = listOf(diag)

        myFixture.doHighlighting()

        NostosDiagnosticsCache.cache.remove(uri)
    }

    fun testApplyMultipleDiagnostics() {
        val file = myFixture.configureByText("test.nos", "x = a + b")

        val uri = "file://${file.virtualFile.path}"
        val diags = listOf(
            Diagnostic(Range(Position(0, 4), Position(0, 5)), "unknown `a`").apply { severity = DiagnosticSeverity.Error },
            Diagnostic(Range(Position(0, 8), Position(0, 9)), "unknown `b`").apply { severity = DiagnosticSeverity.Error },
        )

        NostosDiagnosticsCache.cache[uri] = diags

        myFixture.doHighlighting()

        NostosDiagnosticsCache.cache.remove(uri)
    }
}
