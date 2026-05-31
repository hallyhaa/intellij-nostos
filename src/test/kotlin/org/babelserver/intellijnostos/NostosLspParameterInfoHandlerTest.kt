package org.babelserver.intellijnostos

import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Tuple
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NostosLspParameterInfoHandlerTest {

    private val handler = NostosLspParameterInfoHandler()

    @Test
    fun fallbackShowsTheSignatureLabelWhenParametersAreEmpty() {
        // What nostos-lsp returns for a polymorphic function: a descriptive
        // label but no structured parameter entries.
        val signature = SignatureInformation().apply {
            label = "chunk: HasMethod(length|a) a => a -> auto -> auto"
            parameters = emptyList()
        }
        assertEquals(
            "chunk: HasMethod(length|a) a => a -> auto -> auto",
            handler.fallbackPresentation(signature),
        )
    }

    @Test
    fun fallbackStillShowsLabelEvenIfParameterListWasNeverSet() {
        val signature = SignatureInformation().apply {
            label = "f: a -> auto"
            // parameters left null
        }
        assertEquals("f: a -> auto", handler.fallbackPresentation(signature))
    }

    @Test
    fun fallbackUsesNoParametersMarkerWhenLabelIsBlank() {
        val signature = SignatureInformation().apply { label = "   " }
        assertEquals("<no parameters>", handler.fallbackPresentation(signature))
    }

    @Test
    fun fallbackIsUnaffectedByPresenceOfParameterObjectsElsewhere() {
        // Sanity: fallbackPresentation only looks at the label, so even a
        // populated-but-ignored parameter list does not change its output.
        val signature = SignatureInformation().apply {
            label = "g: Int -> Int"
            parameters = listOf(ParameterInformation("x: Int"))
        }
        assertEquals("g: Int -> Int", handler.fallbackPresentation(signature))
    }

    // computeCurrentParameterIndex — pure comma/paren counting.

    @Test
    fun parameterIndexIsZeroRightAfterTheOpeningParen() {
        // "foo(|" — caret at offset 4, just inside the call.
        assertEquals(0, handler.computeCurrentParameterIndex("foo(", 4))
    }

    @Test
    fun parameterIndexCountsTopLevelCommas() {
        // "foo(a, b, |" — caret after the second comma -> third parameter.
        val text = "foo(a, b, "
        assertEquals(2, handler.computeCurrentParameterIndex(text, text.length))
    }

    @Test
    fun parameterIndexIgnoresCommasInNestedCalls() {
        // "foo(bar(a, b), |" — the comma inside bar(...) must not count; the
        // caret sits at the second argument of foo.
        val text = "foo(bar(a, b), "
        assertEquals(1, handler.computeCurrentParameterIndex(text, text.length))
    }

    @Test
    fun parameterIndexIsMinusOneOutsideAnyCall() {
        assertEquals(-1, handler.computeCurrentParameterIndex("just text", 9))
    }

    @Test
    fun parameterIndexIsMinusOneAtStartOfDocument() {
        assertEquals(-1, handler.computeCurrentParameterIndex("foo(", 0))
    }

    // activeSignature — selects the right signature, clamps the index.

    @Test
    fun activeSignatureReturnsNullWhenThereAreNoSignatures() {
        assertNull(handler.activeSignature(SignatureHelp(emptyList(), 0, 0)))
    }

    @Test
    fun activeSignaturePicksTheActiveIndex() {
        val help = SignatureHelp(
            listOf(SignatureInformation("first"), SignatureInformation("second")),
            1,
            0,
        )
        assertEquals("second", handler.activeSignature(help)?.label)
    }

    @Test
    fun activeSignatureClampsAnOutOfRangeIndex() {
        val help = SignatureHelp(listOf(SignatureInformation("only")), 5, 0)
        assertEquals("only", handler.activeSignature(help)?.label)
    }

    // parameterText — handles both string labels and [start, end] ranges.

    @Test
    fun parameterTextUsesAStringLabelDirectly() {
        val sig = SignatureInformation("f(x: Int)")
        val param = ParameterInformation().apply { label = Either.forLeft("x: Int") }
        assertEquals("x: Int", handler.parameterText(sig, param))
    }

    @Test
    fun parameterTextResolvesARangeLabelAgainstTheSignature() {
        val sig = SignatureInformation("f(x: Int, y: Int)")
        // [2, 8] points at "x: Int" within the signature label.
        val param = ParameterInformation().apply {
            label = Either.forRight(Tuple.two(2, 8))
        }
        assertEquals("x: Int", handler.parameterText(sig, param))
    }
}
