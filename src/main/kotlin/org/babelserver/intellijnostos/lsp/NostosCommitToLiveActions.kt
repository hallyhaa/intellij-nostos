package org.babelserver.intellijnostos.lsp

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.babelserver.intellijnostos.NostosFileType

/**
 * Commits the current Nostos file to the running live system. (Just saving a file
 * never touches the running system).Enabled only in Nostos files while the
 * language server is running.
 */
class NostosCommitToLiveAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = project != null &&
            file?.fileType == NostosFileType &&
            NostosLspServerManager.getInstance(project).activeServer != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        NostosLspServerManager.getInstance(project).commitToLive(file)
    }
}

/**
 * Commits every open Nostos document to the running live system
 * ("nostos.commitAll"). Enabled while the language server is running.
 */
class NostosCommitAllToLiveAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            NostosLspServerManager.getInstance(project).activeServer != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        NostosLspServerManager.getInstance(project).commitAllToLive()
    }
}
