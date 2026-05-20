package org.babelserver.intellijnostos.lsp

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
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

            // Force the daemon to re-run for the whole project. The per-file
            // restart(psiFile, reason) variant skips files whose document
            // modification stamp has not changed since the last analysis --
            // which is exactly the LSP-driven case: diagnostics change but
            // the document does not. The project-wide restart() bypasses
            // that gate. forceHintsUpdateOnNextPass clears the inlay pass's
            // own modification-stamp cache so the next run actually re-
            // queries our provider. The Internal class lives in
            // com.intellij.codeInsight.daemon.impl; the platform exposes
            // no equivalent on a non-impl surface yet.
            ApplicationManager.getApplication().invokeLater {
                InlayHintsPassFactoryInternal.Companion.forceHintsUpdateOnNextPass()
                val fem = FileEditorManager.getInstance(project)
                val psiManager = PsiManager.getInstance(project)
                val daemon = DaemonCodeAnalyzer.getInstance(project)
                for (file in fem.openFiles) {
                    if (file.fileType != NostosFileType) continue
                    val psiFile = psiManager.findFile(file) ?: continue
                    // Bump the file's PSI modification counter so the daemon
                    // scheduler does not skip its per-file mod-stamp gate. The
                    // LSP changed the diagnostics without an accompanying
                    // editor edit, so the document mod-stamp is unchanged --
                    // which on its own makes restart(psiFile) a no-op for
                    // any file that was analysed recently. Marking the PSI
                    // subtree as changed forces a fresh pass and re-runs our
                    // external annotator and inlay hint provider.
                    (psiFile as? PsiFileImpl)?.subtreeChanged()
                    daemon.restart(psiFile, RESTART_REASON)
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
        private const val RESTART_REASON = "Nostos LSP publishDiagnostics"
    }
}
