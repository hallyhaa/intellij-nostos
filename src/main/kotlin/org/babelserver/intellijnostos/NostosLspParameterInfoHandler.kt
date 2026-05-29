package org.babelserver.intellijnostos

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.babelserver.intellijnostos.lsp.NostosLspServerManager
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Tuple
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Surfaces nostos-lsp's `textDocument/signatureHelp` in IDEA's parameter-info
 * popup. The popup is triggered automatically by IDEA when the user types an
 * opening parenthesis after a function name (or explicitly via Ctrl+P), and
 * shows the signature label with the active parameter highlighted.
 */
class NostosLspParameterInfoHandler : ParameterInfoHandler<PsiElement, SignatureHelp> {

    private val log = Logger.getInstance(NostosLspParameterInfoHandler::class.java)

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val file = context.file ?: return null
        if (file.fileType != NostosFileType) return null
        val help = fetchSignatureHelp(file, context.editor, context.offset) ?: return null
        if (help.signatures.isNullOrEmpty()) return null
        context.itemsToShow = arrayOf(help)
        return file
    }

    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        context.showHint(element, context.offset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiElement? {
        val file = context.file ?: return null
        if (file.fileType != NostosFileType) return null
        return file
    }

    override fun updateParameterInfo(parameterOwner: PsiElement, context: UpdateParameterInfoContext) {
        val file = parameterOwner.containingFile ?: (parameterOwner as? PsiFile)
        if (file == null || file.fileType != NostosFileType) {
            context.removeHint()
            return
        }
        // Compute the active parameter locally by counting unparenthesised
        // commas between the enclosing `(` and the caret. Refetching
        // signatureHelp from the LSP on every caret movement was blocking the
        // EDT for up to half a second per keystroke, which made the editor
        // visibly slow and starved the diagnostics pipeline. Local counting
        // is essentially free and matches IDEA's own parameter-info pattern.
        val paramIndex = computeCurrentParameterIndex(context.editor.document, context.offset)
        if (paramIndex < 0) {
            // Caret moved outside any enclosing call — close the popup.
            context.removeHint()
            return
        }
        context.setCurrentParameter(paramIndex)
    }

    override fun updateUI(p: SignatureHelp?, context: ParameterInfoUIContext) {
        if (p == null) return
        val signature = activeSignature(p) ?: return
        val params = signature.parameters.orEmpty()
        if (params.isEmpty()) {
            context.setupUIComponentPresentation(
                NO_PARAMETERS_TEXT,
                0,
                0,
                false,
                false,
                false,
                context.defaultParameterColor,
            )
            return
        }

        // Build the comma-separated parameter list IDEA convention expects
        // ("int x, String y"), and remember where the active parameter sits
        // inside that string so the highlight range hits exactly that
        // parameter. setupUIComponentPresentation renders the highlighted
        // slice in bold against a tinted background, matching how Java and
        // Kotlin render the active parameter.
        val activeIndex = context.currentParameterIndex
        val sb = StringBuilder()
        var highlightStart = 0
        var highlightEnd = 0
        for ((i, param) in params.withIndex()) {
            if (i > 0) sb.append(", ")
            val text = parameterText(signature, param)
            if (i == activeIndex) {
                highlightStart = sb.length
                highlightEnd = sb.length + text.length
            }
            sb.append(text)
        }

        context.setupUIComponentPresentation(
            sb.toString(),
            highlightStart,
            highlightEnd,
            !context.isUIComponentEnabled,
            false,
            false,
            context.defaultParameterColor,
        )
    }

    /**
     * Walk backwards from [caret] through the document text, tracking nested
     * parentheses, until we find the unclosed `(` that encloses the caret.
     * Return the number of top-level commas encountered between that `(` and
     * the caret — i.e. the zero-based active parameter index.
     *
     * Returns -1 if no enclosing `(` is found (the caret has moved outside
     * the call and the popup should close).
     */
    private fun computeCurrentParameterIndex(document: Document, caret: Int): Int {
        if (caret <= 0) return -1
        val text = document.charsSequence
        val safeEnd = caret.coerceAtMost(text.length)
        var nesting = 0
        var commas = 0
        var i = safeEnd - 1
        while (i >= 0) {
            when (text[i]) {
                ')' -> nesting++
                '(' -> {
                    if (nesting == 0) return commas
                    nesting--
                }
                ',' -> if (nesting == 0) commas++
            }
            i--
        }
        return -1
    }

    private fun activeSignature(help: SignatureHelp): SignatureInformation? {
        val signatures = help.signatures ?: return null
        if (signatures.isEmpty()) return null
        val idx = (help.activeSignature ?: 0).coerceIn(0, signatures.size - 1)
        return signatures[idx]
    }

    /**
     * Extract the textual representation of a single parameter. lsp4j stores
     * `ParameterInformation.label` as either a substring of the signature
     * label, or as a `[start, end]` integer pair pointing into that label.
     */
    private fun parameterText(signature: SignatureInformation, param: ParameterInformation): String {
        val labelOrRange: Either<String, Tuple.Two<Int, Int>> = param.label ?: return ""
        return if (labelOrRange.isRight) {
            val range = labelOrRange.right
            val sigLabel = signature.label.orEmpty()
            val start = (range.first ?: 0).coerceIn(0, sigLabel.length)
            val end = (range.second ?: 0).coerceIn(start, sigLabel.length)
            sigLabel.substring(start, end)
        } else {
            labelOrRange.left.orEmpty()
        }
    }

    private fun fetchSignatureHelp(file: PsiFile, editor: Editor, offset: Int): SignatureHelp? {
        val server = NostosLspServerManager.getInstance(file.project).activeServer ?: return null
        val virtualFile = file.virtualFile ?: return null
        val document = editor.document
        if (offset < 0 || offset > document.textLength) return null
        val line = document.getLineNumber(offset)
        val character = offset - document.getLineStartOffset(line)
        val params = SignatureHelpParams().apply {
            textDocument = TextDocumentIdentifier(URI("file", "", virtualFile.path, null).toString())
            position = Position(line, character)
        }
        return try {
            server.textDocumentService.signatureHelp(params)
                .get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.debug("signatureHelp request failed", e)
            null
        }
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 500L
        private const val NO_PARAMETERS_TEXT = "<no parameters>"
    }
}
