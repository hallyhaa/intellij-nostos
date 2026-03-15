package org.babelserver.intellijnostos

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.babelserver.intellijnostos.lsp.NostosLspServerManager
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class NostosExternalAnnotator : ExternalAnnotator<NostosExternalAnnotator.Info, List<Diagnostic>>() {

    private val log = Logger.getInstance(NostosExternalAnnotator::class.java)

    data class Info(val filePath: String, val fileUri: String, val document: Document, val project: com.intellij.openapi.project.Project)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Info? {
        if (file !is NostosFile) return null
        val virtualFile = file.virtualFile ?: return null
        val uri = URI("file", "", virtualFile.path, null).toString()
        return Info(virtualFile.path, uri, editor.document, file.project)
    }

    override fun doAnnotate(info: Info): List<Diagnostic> {
        val manager = NostosLspServerManager.getInstance(info.project)
        manager.startIfNeeded()
        val result = NostosDiagnosticsCache.cache[info.fileUri] ?: emptyList()
        log.info("doAnnotate: uri='${info.fileUri}', cached=${result.size}, cacheKeys=${NostosDiagnosticsCache.cache.keys}")
        return result
    }

    override fun apply(file: PsiFile, diagnostics: List<Diagnostic>, holder: AnnotationHolder) {
        val document = file.viewProvider.document ?: return
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

            holder.newAnnotation(severity, diag.message).range(range).create()
        }
    }

    private fun lspPositionToOffset(document: Document, line: Int, character: Int): Int {
        if (line < 0 || line >= document.lineCount) return -1
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        return (lineStart + character).coerceAtMost(lineEnd)
    }

}

object NostosDiagnosticsCache {
    val cache = ConcurrentHashMap<String, List<Diagnostic>>()
}
