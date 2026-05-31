package org.babelserver.intellijnostos

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.babelserver.intellijnostos.lsp.NostosCodeActionQuickFix
import org.babelserver.intellijnostos.lsp.NostosLspServerManager
import org.babelserver.intellijnostos.lsp.NostosLspUri
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import java.util.concurrent.ConcurrentHashMap

class NostosExternalAnnotator : ExternalAnnotator<NostosExternalAnnotator.Info, List<Diagnostic>>() {

    data class Info(
        val filePath: String,
        val fileUri: String,
        val document: Document,
        val project: com.intellij.openapi.project.Project,
        /**
         * Cache contents key. Including this makes `Info.equals` change whenever
         * the LSP cache for this file changes, so IDEA's ExternalToolPass cannot
         * short-circuit `doAnnotate` based on stale equality when diagnostics
         * arrive without an accompanying document edit.
         */
        val diagnosticsKey: Int,
    )

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Info? {
        if (file !is NostosFile) return null
        val virtualFile = file.virtualFile ?: return null
        val uri = NostosLspUri.of(virtualFile)
        val diagnosticsKey = NostosDiagnosticsCache.getInstance(file.project).cache[uri]?.hashCode() ?: 0
        return Info(virtualFile.path, uri, editor.document, file.project, diagnosticsKey)
    }

    private fun hasFixableCodeAction(diag: Diagnostic): Boolean {
        // The server offers an "Add missing import" code action for unknown
        // function/variable diagnostics. Gate the quick-fix on that message
        // shape so we don't attach a (request-issuing) fix to every diagnostic.
        val msg = diag.message?.lowercase() ?: return false
        return ("unknown function" in msg || "unknown variable" in msg)
    }

    override fun doAnnotate(info: Info): List<Diagnostic> {
        val manager = NostosLspServerManager.getInstance(info.project)
        manager.startIfNeeded()
        return NostosDiagnosticsCache.getInstance(info.project).cache[info.fileUri] ?: emptyList()
    }

    override fun apply(file: PsiFile, diagnostics: List<Diagnostic>, holder: AnnotationHolder) {
        val document = file.viewProvider.document ?: return
        // Compute the file URI once rather than per fixable diagnostic.
        val uri by lazy(LazyThreadSafetyMode.NONE) { NostosLspUri.of(file.virtualFile) }
        for (diag in diagnostics) {
            val startOffset = lspPositionToOffset(document, diag.range.start.line, diag.range.start.character)
            val endOffset = lspPositionToOffset(document, diag.range.end.line, diag.range.end.character)
            if (startOffset < 0 || endOffset < 0) continue

            val range = TextRange(
                startOffset.coerceAtMost(document.textLength),
                endOffset.coerceAtMost(document.textLength).coerceAtLeast(startOffset),
            )
            if (range.isEmpty) continue

            val severity = when (diag.severity) {
                DiagnosticSeverity.Error -> HighlightSeverity.ERROR
                DiagnosticSeverity.Warning -> HighlightSeverity.WARNING
                DiagnosticSeverity.Information -> HighlightSeverity.WEAK_WARNING
                DiagnosticSeverity.Hint -> HighlightSeverity.INFORMATION
                else -> HighlightSeverity.WARNING
            }

            val builder = holder.newAnnotation(severity, diag.message).range(range)
            if (hasFixableCodeAction(diag)) {
                builder.withFix(NostosCodeActionQuickFix(uri, diag))
            }
            builder.create()
        }
    }

    private fun lspPositionToOffset(document: Document, line: Int, character: Int): Int {
        if (line < 0 || line >= document.lineCount) return -1
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        // coerceIn, not coerceAtMost: a negative character would otherwise land
        // before the line start (while still >= 0) and mark the wrong range.
        return (lineStart + character).coerceIn(lineStart, lineEnd)
    }

}

/**
 * Per-project store of the diagnostics nostos-lsp last published, keyed by file
 * URI. Project-scoped (not a global object) so two open projects cannot collide
 * on same-named paths, and so the entries can be cleared when this project's
 * server stops. Populated by the diagnostics listener in NostosLspStartupActivity.
 */
@Service(Service.Level.PROJECT)
class NostosDiagnosticsCache {
    val cache = ConcurrentHashMap<String, List<Diagnostic>>()

    companion object {
        fun getInstance(project: Project): NostosDiagnosticsCache =
            project.getService(NostosDiagnosticsCache::class.java)
    }
}
