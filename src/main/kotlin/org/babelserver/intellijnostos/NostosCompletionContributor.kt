package org.babelserver.intellijnostos

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class NostosCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(NostosLanguage),
            KeywordCompletionProvider(ALL_KEYWORDS)
        )
    }

    private class KeywordCompletionProvider(
        private val keywords: List<KeywordEntry>
    ) : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            for (kw in keywords) {
                val handler = kw.onInsert?.let { action ->
                    InsertHandler<com.intellij.codeInsight.lookup.LookupElement> { ctx, _ -> action(ctx) }
                }
                result.addElement(
                    LookupElementBuilder.create(kw.text)
                        .bold()
                        .withTailText(kw.tail, true)
                        .withInsertHandler(handler)
                )
            }
        }
    }

    companion object {
        private val ALL_KEYWORDS = listOf(
            // Declarations
            kw("fn", " "),
            kw("type", " "),
            kw("trait", " "),
            kw("impl", " "),
            kw("module", " "),
            kw("import", " "),
            kw("use", " "),
            kw("var", " "),
            kw("mvar", " "),
            kw("const", " "),
            KeywordEntry("extern", " fn") { insertSuffix(it, " fn ") },
            kw("template", " "),
            kw("reactive", " "),
            KeywordEntry("test", " \"\"") { insertSuffix(it, " \"\"", -1) },
            kw("pub", " "),
            kw("private", " "),

            // Control flow
            KeywordEntry("if", " ... then") { insertSuffix(it, " ") },
            kw("then", " "),
            kw("else", " "),
            KeywordEntry("match", " ... {}") { insertSuffix(it, " ") },
            KeywordEntry("for", " ... in") { insertSuffix(it, " ") },
            KeywordEntry("while", " ... do") { insertSuffix(it, " ") },
            kw("do", " "),
            kw("in", " "),
            kw("to", " "),
            kw("return", " "),
            KeywordEntry("break"),
            KeywordEntry("continue"),

            // Error handling
            KeywordEntry("try", " ... catch") { insertSuffix(it, " ") },
            kw("catch", " "),
            kw("finally", " "),
            kw("throw", " "),
            kw("panic", " "),

            // Concurrency
            kw("spawn", " "),
            kw("spawn_link", " "),
            kw("spawn_monitor", " "),
            KeywordEntry("receive", " {}") { insertSuffix(it, " ") },
            kw("after", " "),

            // Other keywords
            kw("when", " "),
            KeywordEntry("end"),
            kw("with", " "),
            kw("as", " "),
            kw("from", " "),
            kw("where", " "),
            KeywordEntry("deriving", " ()") { insertSurrounding(it, " (", ")") },
            kw("quote", " "),

            // Literals
            KeywordEntry("true"),
            KeywordEntry("false"),
            KeywordEntry("self"),
            KeywordEntry("Self"),
        )

        private fun kw(text: String, suffix: String) =
            KeywordEntry(text, suffix) { insertSuffix(it, suffix) }

        private fun insertSuffix(
            context: InsertionContext,
            suffix: String,
            caretBackOffset: Int = 0
        ) {
            val editor = context.editor
            val offset = context.tailOffset
            val document = editor.document
            val existingText = if (offset < document.textLength)
                document.getText(com.intellij.openapi.util.TextRange(offset, minOf(offset + suffix.length, document.textLength)))
            else ""
            if (!existingText.startsWith(suffix)) {
                document.insertString(offset, suffix)
            }
            editor.caretModel.moveToOffset(offset + suffix.length + caretBackOffset)
        }

        private fun insertSurrounding(
            context: InsertionContext,
            prefix: String,
            suffix: String
        ) {
            val editor = context.editor
            val offset = context.tailOffset
            val document = editor.document
            document.insertString(offset, prefix + suffix)
            editor.caretModel.moveToOffset(offset + prefix.length)
        }
    }

    private data class KeywordEntry(
        val text: String,
        val tail: String = "",
        val onInsert: ((InsertionContext) -> Unit)? = null,
    )
}
