package org.babelserver.intellijnostos.lsp

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Maintenance commands for nostos' on-disk module cache (the .nostos-cache
 * directory), exposed from nostos-lsp over workspace/executeCommand. The
 * server reports the outcome through window/showMessage, surfaced as a
 * notification balloon. Enabled while the language server is running.
 */
internal abstract class NostosCacheAction(private val command: String) : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible =
            project != null && NostosLspServerManager.getInstance(project).activeServer != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        NostosLspServerManager.getInstance(project).executeCommand(command, emptyList())
    }
}

/** Persists the compiled modules to the cache, for faster engine startup ("nostos.buildCache"). */
internal class NostosBuildCacheAction : NostosCacheAction("nostos.buildCache")

/** Deletes the caches — the cure when stale cache content misbehaves ("nostos.clearCache"). */
internal class NostosClearCacheAction : NostosCacheAction("nostos.clearCache")
