package org.babelserver.intellijnostos.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
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

        // Only warn about a missing nostos/nostos-lsp when the project actually
        // contains Nostos files — otherwise the plugin stays silent.
        val hasNostosFiles = smartReadAction(project) {
            FileTypeIndex.containsFileOfType(NostosFileType, GlobalSearchScope.projectScope(project))
        }

        manager.startIfNeeded(notifyIfMissing = hasNostosFiles)
    }
}
