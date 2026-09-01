package org.babelserver.intellijnostos.lsp

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NostosReplResponsesTest {

    private fun json(text: String) = JsonParser.parseString(text)

    @Test
    fun successfulEvalRendersResult() {
        val output = formatEvalResponse(json("""{"success": true, "result": "42"}"""))
        assertEquals(EvalOutput("42", isError = false), output)
    }

    @Test
    fun failedEvalRendersError() {
        val output = formatEvalResponse(json("""{"success": false, "error": "Unknown function: foo"}"""))
        assertEquals(EvalOutput("Unknown function: foo", isError = true), output)
    }

    @Test
    fun missingFieldsFallBackSafely() {
        assertEquals(EvalOutput("()", isError = false), formatEvalResponse(json("""{"success": true}""")))
        assertEquals(EvalOutput("Unknown error", isError = true), formatEvalResponse(json("""{"success": false}""")))
        assertEquals(EvalOutput("Unknown error", isError = true), formatEvalResponse(json("""{}""")))
    }

    @Test
    fun nullAndUnexpectedResponsesAreShownAsIs() {
        assertEquals(EvalOutput("(no response from server)", isError = true), formatEvalResponse(null))
        assertEquals(EvalOutput("\"just a string\"", isError = false), formatEvalResponse(json("\"just a string\"")))
    }

    @Test
    fun completionsPreferInsertTextOverLabel() {
        val response = json(
            """{"completions": [
                {"label": "name: ", "kind": "field", "insertText": "name: "},
                {"label": "println(no insertText)"},
                {"kind": "malformed, no label"},
                "not an object"
            ]}""",
        )
        assertEquals(listOf("name: ", "println(no insertText)"), parseReplCompletions(response))
    }

    @Test
    fun completionsHandleMissingOrMalformedResponses() {
        assertEquals(emptyList<String>(), parseReplCompletions(null))
        assertEquals(emptyList<String>(), parseReplCompletions(json("""{}""")))
        assertEquals(emptyList<String>(), parseReplCompletions(json("""{"completions": "not an array"}""")))
    }
}
