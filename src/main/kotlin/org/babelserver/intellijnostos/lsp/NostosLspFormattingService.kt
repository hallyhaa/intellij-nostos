package org.babelserver.intellijnostos.lsp

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.psi.PsiFile
import org.babelserver.intellijnostos.NostosFileType
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Routes Reformat Code (Ctrl+Alt+L) through nostos-lsp's
 * textDocument/formatting — nostos' own canonical formatter — so the IDE can
 * never disagree with `nostos fmt`. Only claims files while the language
 * server is running; otherwise the platform falls through to the PSI-based
 * [org.babelserver.intellijnostos.NostosFormattingModelBuilder], which keeps
 * formatting working offline.
 */
class NostosLspFormattingService : AsyncDocumentFormattingService() {

    // No FORMAT_FRAGMENTS: nostos-lsp formats whole documents only. Selection
    // formatting therefore also falls through to the PSI formatter.
    override fun getFeatures(): Set<FormattingService.Feature> = emptySet()

    override fun canFormat(file: PsiFile): Boolean =
        file.fileType == NostosFileType &&
            !isNostosReplFile(file.virtualFile) &&
            NostosLspServerManager.getInstance(file.project).activeServer != null

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        val context = request.context
        val virtualFile = context.virtualFile ?: return null
        val server = NostosLspServerManager.getInstance(context.project).activeServer ?: return null

        val indent = CodeStyle.getIndentOptions(context.containingFile)
        val params = DocumentFormattingParams(
            TextDocumentIdentifier(NostosLspUri.of(virtualFile)),
            FormattingOptions(indent.INDENT_SIZE.takeIf { it > 0 } ?: 4, !indent.USE_TAB_CHARACTER),
        )
        val originalText = request.documentText

        return object : FormattingTask {
            @Volatile
            private var pending: CompletableFuture<MutableList<out TextEdit>>? = null

            override fun run() {
                try {
                    val future = server.textDocumentService.formatting(params)
                    pending = future
                    val edits = future.get(FORMAT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    // Null means "document unknown to the server"; empty means
                    // "already formatted". Either way the text stands.
                    request.onTextReady(applyTextEdits(originalText, edits ?: emptyList()))
                } catch (_: CancellationException) {
                    // Cancelled by the platform — nothing to deliver.
                } catch (e: Exception) {
                    request.onError("Nostos", "Formatting via nostos-lsp failed: ${e.message}")
                }
            }

            override fun cancel(): Boolean {
                pending?.cancel(true)
                return true
            }

            override fun isRunUnderProgress(): Boolean = true
        }
    }

    override fun getNotificationGroupId(): String = "Nostos"

    override fun getName(): String = "nostos fmt"
}

private const val FORMAT_TIMEOUT_MS = 5_000L
