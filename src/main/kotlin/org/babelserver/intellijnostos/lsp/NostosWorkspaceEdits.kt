package org.babelserver.intellijnostos.lsp

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import java.net.URI

/**
 * Applies an LSP [WorkspaceEdit] to the project's documents as a single,
 * undoable command. Shared by every feature that receives edits from
 * nostos-lsp (rename, code actions, …) so the edit-application rules live in
 * one place:
 *
 *  - `documentChanges` wins over the legacy `changes` map (LSP spec): a server
 *    that populates both with identical edits would otherwise edit twice.
 *  - Edits are applied in descending range order so earlier offsets stay valid.
 *  - Only files inside project content are touched, so a buggy or hostile
 *    server cannot rewrite arbitrary files on disk.
 */
internal object NostosWorkspaceEdits {

    private val log = Logger.getInstance(NostosWorkspaceEdits::class.java)

    /** Returns true if the edit contained anything applicable. */
    fun apply(project: Project, edit: WorkspaceEdit, commandName: String): Boolean {
        val documentChanges = edit.documentChanges
        val perFileEdits: Map<String, List<TextEdit>> = edit.changes ?: emptyMap()
        if (documentChanges.isNullOrEmpty() && perFileEdits.isEmpty()) return false

        var applied = false
        CommandProcessor.getInstance().executeCommand(project, {
            WriteCommandAction.runWriteCommandAction(project) {
                if (!documentChanges.isNullOrEmpty()) {
                    for (entry in documentChanges) {
                        if (entry.isLeft) {
                            val textDocEdit = entry.left
                            val edits = textDocEdit.edits ?: continue
                            if (applyEditsToFile(project, textDocEdit.textDocument.uri, edits)) applied = true
                        }
                        // Resource operations (create/rename/delete files) are out of scope.
                    }
                } else {
                    for ((uri, edits) in perFileEdits) {
                        if (applyEditsToFile(project, uri, edits)) applied = true
                    }
                }
            }
        }, commandName, null)
        return applied
    }

    private fun applyEditsToFile(project: Project, uri: String, edits: List<TextEdit>): Boolean {
        val vfile = resolveFile(uri) ?: run {
            log.warn("workspace edit: could not resolve $uri")
            return false
        }
        if (!ProjectFileIndex.getInstance(project).isInContent(vfile)) {
            log.warn("workspace edit: refusing to edit file outside project content: $uri")
            return false
        }
        val document = FileDocumentManager.getInstance().getDocument(vfile) ?: run {
            log.warn("workspace edit: no document for $uri")
            return false
        }
        val sorted = edits.sortedWith(
            compareByDescending<TextEdit> { it.range.start.line }
                .thenByDescending { it.range.start.character },
        )
        var changed = false
        for (e in sorted) {
            val start = lspPositionToOffset(document, e.range.start.line, e.range.start.character)
            val end = lspPositionToOffset(document, e.range.end.line, e.range.end.character)
            if (start < 0 || end < start) continue
            document.replaceString(start, end, e.newText ?: "")
            changed = true
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        return changed
    }

    private fun resolveFile(uri: String): VirtualFile? {
        val path = URI(uri).path ?: return null
        return LocalFileSystem.getInstance().findFileByPath(path)
    }

    fun lspPositionToOffset(document: Document, line: Int, character: Int): Int {
        if (line < 0 || line >= document.lineCount) return -1
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        return (lineStart + character).coerceAtMost(lineEnd)
    }
}
