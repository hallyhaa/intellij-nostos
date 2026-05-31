package org.babelserver.intellijnostos.lsp

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.babelserver.intellijnostos.NostosFileType
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.util.concurrent.TimeUnit

/**
 * Shows a "N references" CodeVision hint above each function/type definition,
 * backed by nostos-lsp's `textDocument/codeLens`. The server fully populates
 * each lens (`resolveProvider = false`): the reference count is already in the
 * lens command's title, so we just place that title at the lens range. No
 * `codeLens/resolve` roundtrip is needed.
 *
 * Implemented as a [DaemonBoundCodeVisionProvider] so [computeForEditor] runs
 * in the highlighting daemon (off the EDT), where a blocking LSP call is safe.
 */
class NostosReferencesCodeVisionProvider : DaemonBoundCodeVisionProvider {

    private val log = Logger.getInstance(NostosReferencesCodeVisionProvider::class.java)

    override val id: String get() = ID
    override val name: String get() = "Nostos references"
    override val groupId: String get() = ID
    override val relativeOrderings: List<CodeVisionRelativeOrdering> get() = emptyList()
    override val defaultAnchor: CodeVisionAnchorKind get() = CodeVisionAnchorKind.Top

    override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, TextCodeVisionEntry>> {
        if (file.fileType != NostosFileType) return emptyList()
        val virtualFile = file.virtualFile ?: return emptyList()
        val server = NostosLspServerManager.getInstance(file.project).activeServer ?: return emptyList()
        val document = editor.document
        val uri = NostosLspUri.of(virtualFile)

        val lenses = try {
            server.textDocumentService.codeLens(CodeLensParams(TextDocumentIdentifier(uri)))
                .get(CODE_LENS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.debug("codeLens request failed", e)
            null
        } ?: return emptyList()

        val result = ArrayList<Pair<TextRange, TextCodeVisionEntry>>(lenses.size)
        for (lens in lenses) {
            val title = lens.command?.title?.takeIf { it.isNotBlank() } ?: continue
            val line = lens.range?.start?.line ?: continue
            if (line < 0 || line >= document.lineCount) continue
            // Anchor the hint to the definition line's start offset.
            val offset = document.getLineStartOffset(line)
            result.add(TextRange(offset, offset) to TextCodeVisionEntry(title, id))
        }
        return result
    }

    companion object {
        const val ID = "nostos.references"
        private const val CODE_LENS_TIMEOUT_MS = 1_000L
    }
}
