package org.babelserver.intellijnostos.lsp

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameHandler
import org.babelserver.intellijnostos.NostosFileType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Wires IDEA's Refactor → Rename (F6 / Shift+F6) to nostos-lsp's
 * `textDocument/prepareRename` and `textDocument/rename` requests.
 *
 * Flow:
 *  1. `prepareRename` runs synchronously on the EDT (short timeout): tells us
 *     whether the caret is on a renameable symbol, plus a placeholder name to
 *     pre-fill the dialog with.
 *  2. A modal input dialog asks for the new name with light local validation.
 *  3. `rename` runs in a [Task.Backgroundable] (the server may take time on a
 *     large project), returning a `WorkspaceEdit` describing every text edit
 *     to apply.
 *  4. The edits are applied back on the EDT inside a single
 *     [WriteCommandAction] so the whole rename is one undo step.
 */
class NostosLspRenameHandler : RenameHandler {

    private val log = Logger.getInstance(NostosLspRenameHandler::class.java)

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val file = dataContext.getData(CommonDataKeys.PSI_FILE) ?: return false
        if (file.fileType != NostosFileType) return false
        // We do not check active LSP here — if the server is not running we
        // tell the user inside invoke() so the message is visible rather
        // than silently falling back to another handler.
        return dataContext.getData(CommonDataKeys.EDITOR) != null
    }

    override fun isRenaming(dataContext: DataContext): Boolean = isAvailableOnDataContext(dataContext)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        if (editor == null || file == null) return
        invokeInEditor(project, editor, file)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        // Triggered by other entry points (e.g. project view) — not the focus
        // of this v1, which is editor-driven rename.
    }

    private fun invokeInEditor(project: Project, editor: Editor, file: PsiFile) {
        val server = NostosLspServerManager.getInstance(project).activeServer
        if (server == null) {
            showError(editor, "Nostos language server is not running")
            return
        }
        val virtualFile = file.virtualFile ?: return
        val document = editor.document
        val caret = editor.caretModel.offset
        val line = document.getLineNumber(caret)
        val character = caret - document.getLineStartOffset(line)
        val uri = URI("file", "", virtualFile.path, null).toString()
        val position = Position(line, character)

        val prepared = try {
            server.textDocumentService
                .prepareRename(PrepareRenameParams(TextDocumentIdentifier(uri), position))
                .get(PREPARE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            log.debug("prepareRename failed", e)
            null
        }
        val (range, placeholder) = extractRangeAndPlaceholder(prepared, document, caret) ?: run {
            showError(editor, "Cannot rename element at caret")
            return
        }

        val newName = Messages.showInputDialog(
            project,
            "Rename '$placeholder' to:",
            "Rename",
            null,
            placeholder,
            IdentifierValidator(),
        ) ?: return
        if (newName == placeholder) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Renaming '$placeholder' to '$newName'",
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val edit = try {
                    server.textDocumentService
                        .rename(RenameParams(TextDocumentIdentifier(uri), position, newName))
                        .get(RENAME_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (e: Exception) {
                    log.warn("rename request failed", e)
                    null
                }
                ApplicationManager.getApplication().invokeLater {
                    if (edit == null) {
                        showError(editor, "Rename failed: language server returned no edit")
                        return@invokeLater
                    }
                    applyWorkspaceEdit(project, edit, "Rename '$placeholder' to '$newName'", editor, range)
                }
            }
        })
    }

    private fun applyWorkspaceEdit(
        project: Project,
        edit: WorkspaceEdit,
        commandName: String,
        editor: Editor,
        @Suppress("UNUSED_PARAMETER") originalRange: Range,
    ) {
        val perFileEdits: Map<String, List<TextEdit>> = edit.changes ?: emptyMap()
        val documentChanges = edit.documentChanges
        if (perFileEdits.isEmpty() && documentChanges.isNullOrEmpty()) {
            showError(editor, "Rename: nothing to change")
            return
        }

        CommandProcessor.getInstance().executeCommand(project, {
            WriteCommandAction.runWriteCommandAction(project) {
                for ((uri, edits) in perFileEdits) {
                    applyEditsToFile(project, uri, edits)
                }
                if (!documentChanges.isNullOrEmpty()) {
                    for (entry in documentChanges) {
                        if (entry.isLeft) {
                            val textDocEdit = entry.left
                            val edits = textDocEdit.edits ?: continue
                            applyEditsToFile(project, textDocEdit.textDocument.uri, edits)
                        }
                        // Resource operations (create/rename/delete files) are
                        // not in scope for rename refactoring.
                    }
                }
            }
        }, commandName, null)
    }

    private fun applyEditsToFile(project: Project, uri: String, edits: List<TextEdit>) {
        val vfile = resolveFile(uri) ?: run {
            log.warn("rename: could not resolve $uri")
            return
        }
        val document = FileDocumentManager.getInstance().getDocument(vfile) ?: run {
            log.warn("rename: no document for $uri")
            return
        }
        // Apply edits in descending range order so that earlier offsets remain
        // valid while later edits are applied — a LSP convention.
        val sorted = edits.sortedWith(
            compareByDescending<TextEdit> { it.range.start.line }
                .thenByDescending { it.range.start.character },
        )
        for (e in sorted) {
            val start = lspPositionToOffset(document, e.range.start.line, e.range.start.character)
            val end = lspPositionToOffset(document, e.range.end.line, e.range.end.character)
            if (start < 0 || end < start) continue
            document.replaceString(start, end, e.newText ?: "")
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    private fun resolveFile(uri: String): com.intellij.openapi.vfs.VirtualFile? {
        val path = URI(uri).path ?: return null
        return LocalFileSystem.getInstance().findFileByPath(path)
    }

    private fun lspPositionToOffset(document: Document, line: Int, character: Int): Int {
        if (line < 0 || line >= document.lineCount) return -1
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        return (lineStart + character).coerceAtMost(lineEnd)
    }

    private fun extractRangeAndPlaceholder(
        prepared: Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>?,
        document: Document,
        caret: Int,
    ): Pair<Range, String>? {
        if (prepared == null) return identifierAtCaret(document, caret)
        return when {
            prepared.isFirst -> {
                val range = prepared.first
                range to textForRange(document, range)
            }
            prepared.isSecond -> {
                val result = prepared.second
                val range = result.range ?: return null
                range to (result.placeholder?.takeIf { it.isNotEmpty() } ?: textForRange(document, range))
            }
            prepared.isThird -> identifierAtCaret(document, caret)
            else -> null
        }
    }

    private fun identifierAtCaret(document: Document, caret: Int): Pair<Range, String>? {
        val text = document.charsSequence
        if (caret < 0 || caret > text.length) return null
        var start = caret
        while (start > 0 && isIdentifierChar(text[start - 1])) start--
        var end = caret
        while (end < text.length && isIdentifierChar(text[end])) end++
        if (start == end) return null
        val placeholder = text.subSequence(start, end).toString()
        val startLine = document.getLineNumber(start)
        val endLine = document.getLineNumber(end)
        val range = Range(
            Position(startLine, start - document.getLineStartOffset(startLine)),
            Position(endLine, end - document.getLineStartOffset(endLine)),
        )
        return range to placeholder
    }

    private fun isIdentifierChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    private fun textForRange(document: Document, range: Range): String {
        val start = lspPositionToOffset(document, range.start.line, range.start.character)
        val end = lspPositionToOffset(document, range.end.line, range.end.character)
        if (start < 0 || end < start) return ""
        return document.charsSequence.subSequence(start, end).toString()
    }

    private fun showError(editor: Editor, message: String) {
        HintManager.getInstance().showErrorHint(editor, message)
    }

    private class IdentifierValidator : InputValidator {
        // Permissive client-side check: non-empty, no whitespace. The LSP makes
        // the final semantic call (e.g. is the new name already taken, does it
        // collide with a keyword?). We just keep obviously-bad input out of the
        // network roundtrip.
        override fun checkInput(inputString: String?): Boolean =
            !inputString.isNullOrBlank() && inputString.none { it.isWhitespace() }

        override fun canClose(inputString: String?): Boolean = checkInput(inputString)
    }

    companion object {
        private const val PREPARE_TIMEOUT_MS = 500L
        private const val RENAME_TIMEOUT_MS = 10_000L
    }
}
