package org.babelserver.intellijnostos

import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.SignatureInformation
import org.junit.jupiter.api.Assertions.assertEquals
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
}
