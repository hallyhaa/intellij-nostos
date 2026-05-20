package org.babelserver.intellijnostos.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import org.babelserver.intellijnostos.NostosDiagnosticsCache
import org.babelserver.intellijnostos.NostosFileType
import java.io.File
import java.util.concurrent.CompletableFuture

class NostosLspStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val manager = NostosLspServerManager.getInstance(project)

        // Signalled by the first publishDiagnostics for any file; gives the
        // heuristic "analyzing" progress something to wait on.
        val firstDiagnostics = CompletableFuture<Unit>()

        manager.diagnosticsListener = { params ->
            firstDiagnostics.complete(Unit)
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

        val lspRoot = NostosProjectRoot.choose(manifests, basePath)

        // Two-phase heuristic progress. The startup phase covers the cold
        // process spawn and the initialize handshake. The analysis phase
        // covers the LSP's compile-on-open work and stays visible until the
        // first publishDiagnostics lands. Once nostos-lsp implements
        // `window/workDoneProgress`, finer-grained server-emitted progress
        // will appear alongside via [NostosLspProgressTracker].
        withBackgroundProgress(project, "Starting Nostos language server") {
            manager.startIfNeeded(lspRoot = lspRoot)
        }
        if (manager.activeServer != null) {
            withBackgroundProgress(project, "Analyzing Nostos project") {
                withTimeoutOrNull(ANALYSIS_TIMEOUT_MS) { firstDiagnostics.await() }
            }
        }
    }

    companion object {
        private const val ANALYSIS_TIMEOUT_MS = 60_000L
    }
}
