package org.babelserver.intellijnostos

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.babelserver.intellijnostos.lsp.NostosLspServerManager
import org.babelserver.intellijnostos.lsp.NostosLspUri
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.util.concurrent.TimeUnit

/**
 * Surfaces nostos-lsp's hover information in IDEA's documentation popup
 * (Ctrl+Q and the mouse-over tooltip). Translates the PSI element under the
 * cursor into an LSP position, issues `textDocument/hover`, and renders the
 * returned `MarkupContent` as HTML. nostos-lsp always replies with
 * `MarkupContent` (Markdown), so the deprecated `MarkedString` shape that the
 * LSP spec still permits is not handled.
 */
class NostosLspDocumentationProvider : AbstractDocumentationProvider() {

    private val log = Logger.getInstance(NostosLspDocumentationProvider::class.java)

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? =
        hoverHtml(originalElement ?: element, REQUEST_TIMEOUT_MS)

    // The mouse-over tooltip is more latency-sensitive than Ctrl+Q and is fine
    // to drop if the server is slow, so it waits a shorter time.
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? =
        hoverHtml(originalElement ?: element, QUICK_NAV_TIMEOUT_MS)

    private fun hoverHtml(target: PsiElement?, timeoutMs: Long): String? {
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
            textDocument = TextDocumentIdentifier(NostosLspUri.of(virtualFile))
            position = Position(line, character)
        }

        val hover = try {
            server.textDocumentService.hover(params)
                .get(timeoutMs, TimeUnit.MILLISECONDS)
                ?: return null
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.debug("hover request failed", e)
            return null
        }

        return markupToHtml(hover.contents?.right)
    }

    private fun markupToHtml(markup: MarkupContent?): String? {
        markup ?: return null
        val text = markup.value ?: return null
        if (text.isBlank()) return null
        return if (markup.kind == MarkupKind.MARKDOWN) {
            wrapContent(markdownToHtml(text))
        } else {
            wrapDefinition(escapeHtml(text))
        }
    }

    /**
     * Wraps a code-shaped chunk in IDEA's `<div class='definition'><pre>...`
     * markers so the popup picks up the theme-aware background and foreground
     * colours of IDEA's documentation CSS instead of a default white `<pre>`.
     */
    private fun wrapDefinition(content: String): String =
        DocumentationMarkup.DEFINITION_START + content + DocumentationMarkup.DEFINITION_END

    /** Same idea as [wrapDefinition] but for HTML prose (markdown output). */
    private fun wrapContent(content: String): String =
        DocumentationMarkup.CONTENT_START + content + DocumentationMarkup.CONTENT_END

    private fun markdownToHtml(md: String): String {
        return try {
            val flavour = GFMFlavourDescriptor()
            val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(md)
            HtmlGenerator(md, tree, flavour).generateHtml()
        } catch (e: Exception) {
            log.debug("markdown render failed, falling back to escaped text", e)
            escapeHtml(md)
        }
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    companion object {
        private const val REQUEST_TIMEOUT_MS = 1500L
        private const val QUICK_NAV_TIMEOUT_MS = 600L
    }
}
