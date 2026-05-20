// lsp4j's MarkedString is deprecated in favour of MarkupContent, but the LSP
// spec still permits servers to send it, so we have to accept both shapes.
@file:Suppress("DEPRECATION")

package org.babelserver.intellijnostos

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.babelserver.intellijnostos.lsp.NostosLspServerManager
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkedString
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Surfaces nostos-lsp's hover information in IDEA's documentation popup
 * (Ctrl+Q and the mouse-over tooltip). Translates the PSI element under the
 * cursor into an LSP position, issues `textDocument/hover`, and renders the
 * returned `MarkupContent` or `MarkedString[]` as HTML.
 */
class NostosLspDocumentationProvider : AbstractDocumentationProvider() {

    private val log = Logger.getInstance(NostosLspDocumentationProvider::class.java)

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? =
        hoverHtml(originalElement ?: element)

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? =
        hoverHtml(originalElement ?: element)

    private fun hoverHtml(target: PsiElement?): String? {
        target ?: return null
        val file = target.containingFile ?: return null
        if (file.fileType != NostosFileType) return null
        val virtualFile = file.virtualFile ?: return null
        val project = target.project
        val server = NostosLspServerManager.getInstance(project).activeServer ?: return null

        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return null
        val offset = target.textRange.startOffset
        if (offset < 0 || offset > document.textLength) return null
        val line = document.getLineNumber(offset)
        val character = offset - document.getLineStartOffset(line)

        val params = HoverParams().apply {
            textDocument = TextDocumentIdentifier(URI("file", "", virtualFile.path, null).toString())
            position = Position(line, character)
        }

        val hover = try {
            server.textDocumentService.hover(params)
                .get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                ?: return null
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.debug("hover request failed", e)
            return null
        }

        return contentsToHtml(hover.contents)
    }

    private fun contentsToHtml(
        contents: Either<List<Either<String, MarkedString>>, MarkupContent>?,
    ): String? {
        contents ?: return null
        if (contents.isLeft) {
            val parts = contents.left ?: return null
            val rendered = parts.joinToString("<br>") { part ->
                if (part.isLeft) escapeHtml(part.left ?: "")
                else markedStringToHtml(part.right)
            }
            return rendered.ifBlank { null }
        }
        val markup = contents.right ?: return null
        val text = markup.value ?: return null
        if (text.isBlank()) return null
        return if (markup.kind == MarkupKind.MARKDOWN) {
            markdownToHtml(text)
        } else {
            "<pre>${escapeHtml(text)}</pre>"
        }
    }

    private fun markedStringToHtml(ms: MarkedString?): String {
        val value = ms?.value ?: return ""
        // MarkedString may carry a language hint; we render it as a code block
        // regardless, which works fine for the type signatures the LSP returns.
        return "<pre>${escapeHtml(value)}</pre>"
    }

    private fun markdownToHtml(md: String): String {
        return try {
            val flavour = GFMFlavourDescriptor()
            val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(md)
            HtmlGenerator(md, tree, flavour).generateHtml()
        } catch (e: Exception) {
            log.debug("markdown render failed, falling back to <pre>", e)
            "<pre>${escapeHtml(md)}</pre>"
        }
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    companion object {
        private const val REQUEST_TIMEOUT_MS = 1500L
    }
}
