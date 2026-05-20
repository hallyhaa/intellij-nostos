package org.babelserver.intellijnostos.lsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Routes LSP `$/progress` notifications into IDEA's standard background-task
 * progress bar at the bottom of the IDE window.
 *
 * Each LSP progress token spawns a long-running [Task.Backgroundable]; the
 * task's [ProgressIndicator] is updated from outside its `run` block via
 * shared mutable state, so the language server can keep streaming
 * `WorkDoneProgressReport` notifications and have them reflected in the UI.
 *
 * Progress reporting requires that we both declare the
 * `window.workDoneProgress` client capability and that the server actually
 * emits begin/report/end notifications. Without server-side support this
 * class simply receives nothing — the heuristic startup progress kicked off
 * from [NostosLspStartupActivity] still fires.
 */
internal class NostosLspProgressTracker(private val project: Project) {

    private val log = Logger.getInstance(NostosLspProgressTracker::class.java)
    private val tasks = ConcurrentHashMap<String, Handle>()

    fun begin(token: String, title: String, message: String?, percentage: Int?) {
        if (tasks.containsKey(token)) return
        val effectiveTitle = title.ifBlank { "Nostos" }
        val handle = Handle(effectiveTitle, message, percentage)
        tasks[token] = handle

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            effectiveTitle,
            false,
        ) {
            override fun run(indicator: ProgressIndicator) {
                while (!handle.done.isDone) {
                    indicator.text = effectiveTitle
                    indicator.text2 = handle.message.get() ?: ""
                    val pct = handle.percentage.get()
                    if (pct != null) {
                        indicator.isIndeterminate = false
                        indicator.fraction = pct / 100.0
                    } else {
                        indicator.isIndeterminate = true
                    }
                    try {
                        Thread.sleep(POLL_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        return
                    }
                }
            }
        })
        log.debug("Progress begin: token=$token title=$effectiveTitle")
    }

    fun report(token: String, message: String?, percentage: Int?) {
        val handle = tasks[token] ?: return
        if (message != null) handle.message.set(message)
        if (percentage != null) handle.percentage.set(percentage)
    }

    fun end(token: String, message: String?) {
        val handle = tasks.remove(token) ?: return
        if (message != null) handle.message.set(message)
        handle.done.complete(Unit)
        log.debug("Progress end: token=$token")
    }

    /** Cancel every in-flight indicator. Called when the server is shut down. */
    fun cancelAll() {
        for ((_, handle) in tasks) {
            handle.done.complete(Unit)
        }
        tasks.clear()
    }

    private class Handle(val title: String, initialMessage: String?, initialPercentage: Int?) {
        val message = AtomicReference(initialMessage)
        val percentage = AtomicReference(initialPercentage)
        val done = CompletableFuture<Unit>()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 100L
    }
}
