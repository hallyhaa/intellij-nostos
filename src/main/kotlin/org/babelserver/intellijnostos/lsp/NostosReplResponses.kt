package org.babelserver.intellijnostos.lsp

import com.google.gson.JsonObject

/**
 * Parsers for the JSON that nostos-lsp's REPL commands return over
 * workspace/executeCommand. Kept UI-free so they can be unit-tested.
 */

/** A rendered `nostos.eval` response: the text to print, and whether it is an error. */
internal data class EvalOutput(val text: String, val isError: Boolean)

/**
 * Renders a `nostos.eval` response, `{"success": true, "result": …}` or
 * `{"success": false, "error": …}`. Anything unexpected is shown as-is rather
 * than swallowed, so server changes surface instead of disappearing.
 */
internal fun formatEvalResponse(response: Any?): EvalOutput {
    val obj = response as? JsonObject
        ?: return EvalOutput(response?.toString() ?: "(no response from server)", response == null)
    val success = obj.get("success")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
    return if (success) {
        EvalOutput(obj.stringField("result") ?: "()", isError = false)
    } else {
        EvalOutput(obj.stringField("error") ?: "Unknown error", isError = true)
    }
}

/**
 * The insertable texts of a `nostos.replComplete` response,
 * `{"completions": [{"label": …, "insertText": …, …}]}`. Falls back from
 * insertText to label per item; malformed items are skipped.
 */
internal fun parseReplCompletions(response: Any?): List<String> {
    val obj = response as? JsonObject ?: return emptyList()
    val completions = obj.get("completions")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
    return completions.mapNotNull { item ->
        val completion = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        completion.stringField("insertText") ?: completion.stringField("label")
    }
}

private fun JsonObject.stringField(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
