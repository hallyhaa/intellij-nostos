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

    // ==================== doAnnotate with diagnostics ====================

    fun testApplyErrorDiagnostic() {
        val file = myFixture.configureByText("test.nos", "x = 1 + 1")
        val info = annotator.collectInformation(file, myFixture.editor, false)
        assertNotNull(info)

        val diag = Diagnostic(
            Range(Position(0, 4), Position(0, 9)),
            "some error"
        ).apply { severity = DiagnosticSeverity.Error }

        NostosDiagnosticsCache.cache[info!!.fileUri] = listOf(diag)

        val result = annotator.doAnnotate(info)
        assertEquals(1, result.size)
        assertEquals("some error", result[0].message)
        assertEquals(DiagnosticSeverity.Error, result[0].severity)

        NostosDiagnosticsCache.cache.remove(info.fileUri)
    }

    fun testApplyWarningDiagnostic() {
        val file = myFixture.configureByText("test.nos", "x = 1 / 0")
        val info = annotator.collectInformation(file, myFixture.editor, false)
        assertNotNull(info)

        val diag = Diagnostic(
            Range(Position(0, 4), Position(0, 9)),
            "division by zero"
        ).apply { severity = DiagnosticSeverity.Warning }

        NostosDiagnosticsCache.cache[info!!.fileUri] = listOf(diag)

        val result = annotator.doAnnotate(info)
        assertEquals(1, result.size)
        assertEquals("division by zero", result[0].message)
        assertEquals(DiagnosticSeverity.Warning, result[0].severity)

        NostosDiagnosticsCache.cache.remove(info.fileUri)
    }

    fun testApplyMultipleDiagnostics() {
        val file = myFixture.configureByText("test.nos", "x = 1 + 2")
        val info = annotator.collectInformation(file, myFixture.editor, false)
        assertNotNull(info)

        val diags = listOf(
            Diagnostic(Range(Position(0, 4), Position(0, 5)), "error 1").apply { severity = DiagnosticSeverity.Error },
            Diagnostic(Range(Position(0, 8), Position(0, 9)), "error 2").apply { severity = DiagnosticSeverity.Error },
        )

        NostosDiagnosticsCache.cache[info!!.fileUri] = diags

        val result = annotator.doAnnotate(info)
        assertEquals(2, result.size)
        assertEquals("error 1", result[0].message)
        assertEquals("error 2", result[1].message)

        NostosDiagnosticsCache.cache.remove(info.fileUri)
    }
}
