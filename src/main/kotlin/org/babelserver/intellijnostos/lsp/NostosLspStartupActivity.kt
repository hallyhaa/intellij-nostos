package org.babelserver.intellijnostos.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
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

        // In a single smart-mode read: check whether the project has Nostos
        // files (gates the missing-toolchain warning) and locate nostos.toml
        // manifests (used to pick the LSP workspace root).
        val (hasNostosFiles, lspRoot) = smartReadAction(project) {
            val scope = GlobalSearchScope.projectScope(project)
            val hasFiles = FileTypeIndex.containsFileOfType(NostosFileType, scope)
            val manifests = FilenameIndex
                .getVirtualFilesByName(NostosProjectRoot.MANIFEST_NAME, scope)
                .map { it.path }
            hasFiles to NostosProjectRoot.choose(manifests, project.basePath)
        }

        manager.startIfNeeded(notifyIfMissing = hasNostosFiles, lspRoot = lspRoot)
    }
}
