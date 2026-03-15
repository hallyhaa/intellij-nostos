package org.babelserver.intellijnostos.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiDocumentManager
import org.babelserver.intellijnostos.NostosDiagnosticsCache
import org.babelserver.intellijnostos.NostosFileType

class NostosLspStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val manager = NostosLspServerManager.getInstance(project)

        manager.diagnosticsListener = { params ->
            NostosDiagnosticsCache.cache[params.uri] = params.diagnostics

            // Trigger re-annotation for the affected file
            ApplicationManager.getApplication().invokeLater {
                val fem = FileEditorManager.getInstance(project)
                for (file in fem.openFiles) {
                    if (file.fileType == NostosFileType) {
                        PsiDocumentManager.getInstance(project).reparseFiles(listOf(file), false)
                    }
                }
            }
        }

        manager.startIfNeeded()
    }
}
