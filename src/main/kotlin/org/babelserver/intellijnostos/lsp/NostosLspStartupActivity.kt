package org.babelserver.intellijnostos.lsp

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
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

        manager.diagnosticsListener = { params ->
            firstDiagnostics.complete(Unit)
            NostosDiagnosticsCache.cache[params.uri] = params.diagnostics

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

        // Locate every .nos file once; needed both to decide whether this is a
        // Nostos project and, when there is no manifest, to pick where to offer
        // generating one.
        val nosFiles = smartReadAction(project) {
            FileTypeIndex.getFiles(NostosFileType, GlobalSearchScope.projectScope(project))
                .map { it.path }
        }

        // Start the language server only for Nostos projects: those with a
        // nostos.toml, or with .nos files somewhere in the project.
        val isNostosProject = manifests.isNotEmpty() || nosFiles.isNotEmpty()
        if (!isNostosProject) return

        val lspRoot = NostosProjectRoot.choose(manifests, basePath)

        // The project has .nos files but no manifest. The server still runs
        // (rooted at lspRoot), but without nostos.toml it cannot resolve the
        // project's dependencies. Offer to generate one — beside the user's
        // sources rather than dumped at the project root — rather than writing
        // it unprompted. A single notification per project, targeting one best
        // directory, keeps this from nagging on projects with loose .nos files.
        if (manifests.isEmpty()) {
            val sourceRoots = smartReadAction(project) {
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
