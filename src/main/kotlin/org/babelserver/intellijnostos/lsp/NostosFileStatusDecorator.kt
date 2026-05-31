package org.babelserver.intellijnostos.lsp

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

class NostosFileStatusDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val file = node.virtualFile ?: return
        if (file.extension != "nos") return
        val status = NostosFileStatusCache.getInstance(node.project).statuses[file.path] ?: return

        if (status != "error") return
        data.locationString = (data.locationString ?: "") + " ✗"
    }
}

/**
 * Per-project map of file path -> compile status, fed by nostos-lsp's
 * `nostos/fileStatus` notifications and read by the project-view decorator.
 * Project-scoped so two open projects with same-named paths cannot collide.
 */
@Service(Service.Level.PROJECT)
class NostosFileStatusCache {
    val statuses = ConcurrentHashMap<String, String>()

    /**
     * Replaces the status map. Returns true when the new set differs from the
     * old one, so the caller can skip an expensive project-view refresh when
     * nothing actually changed.
     */
    fun updateStatuses(files: List<Pair<String, String>>): Boolean {
        val incoming = files.toMap()
        if (incoming == statuses) return false
        statuses.clear()
        statuses.putAll(incoming)
        return true
    }

    companion object {
        fun getInstance(project: Project): NostosFileStatusCache =
            project.getService(NostosFileStatusCache::class.java)
    }
}
