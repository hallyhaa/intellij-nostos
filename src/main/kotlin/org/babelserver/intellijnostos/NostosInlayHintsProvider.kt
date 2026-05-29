package org.babelserver.intellijnostos

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.OwnBypassCollector
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiFile
import org.babelserver.intellijnostos.lsp.NostosLspServerManager
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintLabelPart
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Renders inlay type hints supplied by nostos-lsp, using the declarative
 * inlay-hints API.
 *
 * The LSP gives us a position-and-label for every hint it wants drawn (inferred
 * type at a binding, parameter type at a call site, etc.). We make a single
 * `textDocument/inlayHint` request per file and place each hint as an inline
 * presentation at the corresponding offset.
 */
class NostosInlayHintsProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        if (file.fileType != NostosFileType) return null
        return NostosCollector(editor)
    }

    /**
     * An [OwnBypassCollector] is invoked once per file (rather than once per PSI
     * element), which matches our single-request-per-file model.
     */
    private class NostosCollector(private val editor: Editor) : OwnBypassCollector {

        private val log = Logger.getInstance(NostosCollector::class.java)

        override fun collectHintsForFile(file: PsiFile, sink: InlayTreeSink) {
            val virtualFile = file.virtualFile ?: return
            val server = NostosLspServerManager.getInstance(file.project).activeServer ?: return
            val document = editor.document

            val params = InlayHintParams().apply {
                textDocument = TextDocumentIdentifier(URI("file", "", virtualFile.path, null).toString())
                // Whole-document range. End at the end of the last real line:
                // LSP line numbers are 0-based, so Position(lineCount, 0) would
                // point one line past the document and a strict server could
                // reject it or drop hints.
                val lastLine = (document.lineCount - 1).coerceAtLeast(0)
                val lastLineEnd = document.getLineEndOffset(lastLine) - document.getLineStartOffset(lastLine)
                range = Range(Position(0, 0), Position(lastLine, lastLineEnd))
            }

            val hints: List<InlayHint> = try {
                server.textDocumentService.inlayHint(params)
                    .get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    ?: return
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                log.debug("inlayHint request failed", e)
                return
            }

            for (hint in hints) {
                val offset = lspPositionToOffset(document, hint.position.line, hint.position.character)
                if (offset < 0) continue
                val text = labelToString(hint.label)?.takeIf { it.isNotEmpty() } ?: continue
                sink.addPresentation(
                    InlineInlayPosition(offset, relatedToPrevious = false),
                    hintFormat = HintFormat.default,
                ) {
                    text(text)
                }
            }
        }

        private fun labelToString(label: Either<String, MutableList<InlayHintLabelPart>>?): String? {
            if (label == null) return null
            return if (label.isLeft) {
                label.left
            } else {
                label.right.joinToString("") { it.value ?: "" }
            }
        }

        private fun lspPositionToOffset(document: Document, line: Int, character: Int): Int {
            if (line < 0 || line >= document.lineCount) return -1
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            return (lineStart + character).coerceAtMost(lineEnd)
        }

        companion object {
            private const val REQUEST_TIMEOUT_MS = 500L
        }
    }
}
