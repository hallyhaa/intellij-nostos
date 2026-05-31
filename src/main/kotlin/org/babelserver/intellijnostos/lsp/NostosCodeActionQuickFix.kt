package org.babelserver.intellijnostos.lsp

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.util.concurrent.TimeUnit

/**
 * Quick-fix attached to a Nostos diagnostic that asks nostos-lsp for
 * `textDocument/codeAction`s scoped to that diagnostic and applies the first
 * one whose inline `WorkspaceEdit` resolves. The server's headline use is
 * "Add missing import" on an unknown function/variable, but this stays generic:
 * any quick-fix the server offers for the diagnostic is applied the same way.
 *
 * The code-action roundtrip is a short blocking call made when the user
 * explicitly invokes the fix — the same convention completion and hover use.
 * It deliberately does not start in a write action: the network call runs
 * first, then [NostosWorkspaceEdits.apply] wraps the edits in its own command.
 */
class NostosCodeActionQuickFix(
    private val fileUri: String,
    private val diagnostic: Diagnostic,
) : IntentionAction {

    private val log = Logger.getInstance(NostosCodeActionQuickFix::class.java)

    override fun getText(): String = "Nostos: fix with language server"

    override fun getFamilyName(): String = "Nostos language server quick-fix"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        NostosLspServerManager.getInstance(project).activeServer != null

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val server = NostosLspServerManager.getInstance(project).activeServer ?: return

        val params = CodeActionParams(
            TextDocumentIdentifier(fileUri),
            diagnostic.range,
            CodeActionContext(listOf(diagnostic)),
        )

        val response = try {
            server.textDocumentService.codeAction(params)
                .get(CODE_ACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            log.debug("codeAction request failed", e)
            null
        } ?: return

        // The server returns CodeAction objects (Either.right); plain Commands
        // (Either.left) would need workspace/executeCommand, which nostos-lsp
        // does not advertise, so we only handle inline-edit code actions.
        for (item in response) {
            if (!item.isRight) continue
            val action = item.right
            val edit = action.edit ?: continue
            if (NostosWorkspaceEdits.apply(project, edit, action.title ?: text)) return
        }
    }

    companion object {
        private const val CODE_ACTION_TIMEOUT_MS = 2_000L
    }
}
