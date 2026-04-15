package org.babelserver.intellijnostos

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.PlatformIcons
import com.intellij.util.ProcessingContext
import org.babelserver.intellijnostos.lsp.NostosLspServerManager
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.swing.Icon

/**
 * Completion contributor that delegates to the nostos-lsp server.
 * Provides context-aware completions including dot-expression member access.
 */
class NostosLspCompletionContributor : CompletionContributor() {

    override fun beforeCompletion(context: CompletionInitializationContext) {
        val offset = context.startOffset
        val doc = context.editor.document
        if (offset > 0 && doc.getText(TextRange(offset - 1, offset)) == ".") {
            context.dummyIdentifier = CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED
        }
    }

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(NostosLanguage),
            LspCompletionProvider()
        )
    }

    private class LspCompletionProvider : CompletionProvider<CompletionParameters>() {

        private val log = Logger.getInstance(LspCompletionProvider::class.java)

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val project = parameters.position.project

            val server = project.getServiceIfCreated(NostosLspServerManager::class.java)
                ?.activeServer ?: return

            val file = parameters.originalFile.virtualFile ?: return
            val document = parameters.originalFile.viewProvider.document ?: return

            // Only trigger LSP completion after a dot (member access)
            val caretOffset = parameters.offset.coerceAtMost(document.textLength)
            if (caretOffset <= 0 || document.getText(TextRange(caretOffset - 1, caretOffset)) != ".") return

            val line = document.getLineNumber(caretOffset)
            val character = caretOffset - document.getLineStartOffset(line)
            val uri = URI("file", "", file.path, null).toString()

            try {
                @Suppress("UNCHECKED_CAST")
                val response = server.textDocumentService
                    .completion(CompletionParams(TextDocumentIdentifier(uri), Position(line, character)))
                    .get(2, TimeUnit.SECONDS) as? Either<List<CompletionItem>, CompletionList>

                val items: List<CompletionItem> = when {
                    response == null -> emptyList()
                    response.isRight -> response.right?.items ?: emptyList()
                    response.isLeft -> response.left ?: emptyList()
                    else -> emptyList()
                }

                if (items.isEmpty()) return

                val lspResult = result.withPrefixMatcher(result.prefixMatcher.cloneWithPrefix(""))
                val sorted = items
                    .filter { !it.label.startsWith(":") }  // skip type annotations
                    .sortedWith(compareByDescending<CompletionItem> { lspKindToPriority(it.kind) }
                        .thenBy { it.sortText ?: it.label })
                for ((index, item) in sorted.withIndex()) {
                    val element = LookupElementBuilder.create(item.insertText ?: item.label)
                        .withPresentableText(item.label)
                        .withTypeText(item.detail)
                        .withIcon(lspKindToIcon(item.kind))
                    val priority = (sorted.size - index).toDouble()
                    lspResult.addElement(PrioritizedLookupElement.withPriority(element, priority))
                }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                log.debug("LSP completion request failed", e)
            }
        }

        private fun lspKindToPriority(kind: CompletionItemKind?): Double = when (kind) {
            CompletionItemKind.Field, CompletionItemKind.Property -> 20.0
            CompletionItemKind.Method -> 10.0
            CompletionItemKind.Function -> 5.0
            else -> 0.0
        }

        private fun lspKindToIcon(kind: CompletionItemKind?): Icon? = when (kind) {
            CompletionItemKind.Function, CompletionItemKind.Method -> PlatformIcons.FUNCTION_ICON
            CompletionItemKind.Variable -> PlatformIcons.VARIABLE_ICON
            CompletionItemKind.Field, CompletionItemKind.Property -> PlatformIcons.FIELD_ICON
            CompletionItemKind.Class, CompletionItemKind.Struct -> PlatformIcons.CLASS_ICON
            CompletionItemKind.Interface -> PlatformIcons.INTERFACE_ICON
            CompletionItemKind.Module -> PlatformIcons.PACKAGE_ICON
            CompletionItemKind.Enum, CompletionItemKind.EnumMember -> PlatformIcons.ENUM_ICON
            CompletionItemKind.Constant -> PlatformIcons.FIELD_ICON
            CompletionItemKind.TypeParameter -> PlatformIcons.PARAMETER_ICON
            else -> null
        }
    }
}
