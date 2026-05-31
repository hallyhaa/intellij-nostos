package org.babelserver.intellijnostos.lsp

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Alarm
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

        // Coalesces daemon restarts. While the server compiles a project on
        // open it publishes diagnostics one file at a time, and a full
        // DaemonCodeAnalyzer.restart() per notification thrashes the annotator,
        // semantic-token and inlay passes for the open editors. A trailing
        // debounce collapses each burst into a single restart once diagnostics
        // settle. SWING_THREAD runs the request on the EDT, where restart()
        // expects to be called; the alarm is parented to the project-scoped
        // server manager so it is disposed with the project.
        val restartAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, manager)

        val diagnosticsCache = NostosDiagnosticsCache.getInstance(project)
        manager.diagnosticsListener = { params ->
            firstDiagnostics.complete(Unit)
            diagnosticsCache.cache[params.uri] = params.diagnostics

            // Re-run the daemon so the external annotator (diagnostics) and the
            // declarative inlay pass pick up what the LSP just published. The
            // diagnostics arrived without an editor edit, so a per-file restart
            // would be gated out by the unchanged document mod-stamp; a
            // whole-daemon restart() ignores those per-file stamps.
            restartAlarm.cancelAllRequests()
            restartAlarm.addRequest(
                { DaemonCodeAnalyzer.getInstance(project).restart("Nostos LSP published diagnostics") },
                RESTART_DEBOUNCE_MS,
            )
        }

        // Resolve the workspace root from the filesystem — the index can be
        // stale right after a project is created.
        val basePath = project.basePath ?: return
        val manifests = NostosProjectRoot.findManifests(File(basePath))

        // Common case: a nostos.toml exists, so we already know this is a
        // Nostos project and where the root is — skip the index entirely. The
        // (index-blocking) .nos enumeration is only needed for the rare
        // no-manifest case below, so it is deferred to there.
        val lspRoot = NostosProjectRoot.choose(manifests, basePath)

        if (manifests.isEmpty()) {
            // No manifest: enumerate .nos files. If there are none this is not a
            // Nostos project and we stop without starting the server.
            val nosFiles = smartReadAction(project) {
                FileTypeIndex.getFiles(NostosFileType, GlobalSearchScope.projectScope(project))
                    .map { it.path }
            }
            if (nosFiles.isEmpty()) return

            // .nos files but no nostos.toml. The server still runs (rooted at
            // lspRoot), but without a manifest it cannot resolve the project's
            // dependencies. Offer to generate one — beside the user's sources
            // rather than dumped at the project root — rather than writing it
            // unprompted. A single notification per project, targeting one best
            // directory, keeps this from nagging on loose .nos files. Source
            // roots are model state, so a plain readAction suffices (no need to
            // wait out indexing with smartReadAction).
            val sourceRoots = readAction {
                com.intellij.openapi.roots.ProjectRootManager.getInstance(project)
                    .contentSourceRoots.map { it.path }
            }
            val target = NostosProjectRoot.chooseManifestTarget(sourceRoots, nosFiles, basePath)
            if (target != null) {
                ApplicationManager.getApplication().invokeLater {
                    NostosMissingManifestNotifier.notify(project, target)
                }
            }
        }

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

        /**
         * Trailing-debounce window for diagnostics-driven daemon restarts.
         * Long enough to swallow a startup burst of per-file diagnostics, short
         * enough that a restart after an isolated edit feels immediate.
         */
        private const val RESTART_DEBOUNCE_MS = 200
    }
}
