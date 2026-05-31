package org.babelserver.intellijnostos.lsp

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer
import org.babelserver.intellijnostos.NostosFileType
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.util.concurrent.TimeUnit

/**
 * Wires IDEA's "highlight usages of symbol under caret" (the highlight you see
 * when the caret rests on an identifier, and Ctrl+Shift+F7) to nostos-lsp's
 * `textDocument/documentHighlight`. The server scans the current document for
 * the word under the caret and returns every occurrence; we paint them as read
 * usages (the server reports only `TEXT` kind, so there is no read/write split).
 */
class NostosHighlightUsagesHandlerFactory : HighlightUsagesHandlerFactory {

    override fun createHighlightUsagesHandler(
        editor: Editor,
        file: PsiFile,
    ): HighlightUsagesHandlerBase<*>? {
        if (file.fileType != NostosFileType) return null
        if (NostosLspServerManager.getInstance(file.project).activeServer == null) return null
        return NostosHighlightUsagesHandler(editor, file)
    }
}

private class NostosHighlightUsagesHandler(
    editor: Editor,
    file: PsiFile,
) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {

    private val log = Logger.getInstance(NostosHighlightUsagesHandler::class.java)

    // A single dummy target: the LSP does the actual symbol resolution from the
    // caret position, so we only need "is the caret somewhere highlightable".
    override fun getTargets(): List<PsiElement> {
        val offset = myEditor.caretModel.offset
        val element = myFile.findElementAt(offset) ?: myFile.findElementAt(offset - 1) ?: return emptyList()
        return listOf(element)
    }

    override fun selectTargets(
        targets: List<PsiElement>,
        selectionConsumer: Consumer<in List<PsiElement>>,
    ) {
        selectionConsumer.consume(targets)
    }

    override fun computeUsages(targets: List<PsiElement>) {
        val server = NostosLspServerManager.getInstance(myFile.project).activeServer ?: return
        val virtualFile = myFile.virtualFile ?: return
        val document = myEditor.document
        val caret = myEditor.caretModel.offset
        val line = document.getLineNumber(caret)
        val character = caret - document.getLineStartOffset(line)
        val uri = NostosLspUri.of(virtualFile)

        val highlights = try {
            server.textDocumentService
                .documentHighlight(DocumentHighlightParams(TextDocumentIdentifier(uri), Position(line, character)))
                .get(HIGHLIGHT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.debug("documentHighlight request failed", e)
            null
        } ?: return

        for (h in highlights) {
            val start = NostosWorkspaceEdits.lspPositionToOffset(document, h.range.start.line, h.range.start.character)
            val end = NostosWorkspaceEdits.lspPositionToOffset(document, h.range.end.line, h.range.end.character)
            if (start < 0 || end < start) continue
            myReadUsages.add(TextRange(start, end))
        }
    }

    companion object {
        private const val HIGHLIGHT_TIMEOUT_MS = 500L
    }
}
