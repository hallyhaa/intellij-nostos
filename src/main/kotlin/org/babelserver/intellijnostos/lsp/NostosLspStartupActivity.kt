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
import java.io.File

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

        // Resolve the workspace root from the filesystem — the index can be
        // stale right after a project is created.
        val basePath = project.basePath ?: return
        val manifests = NostosProjectRoot.findManifests(File(basePath))

        // Start the language server only for Nostos projects: those with a
        // nostos.toml, or with .nos files somewhere in the project.
        val isNostosProject = manifests.isNotEmpty() || smartReadAction(project) {
            FileTypeIndex.containsFileOfType(NostosFileType, GlobalSearchScope.projectScope(project))
        }
        if (!isNostosProject) return

        manager.startIfNeeded(lspRoot = NostosProjectRoot.choose(manifests, basePath))
    }
}
