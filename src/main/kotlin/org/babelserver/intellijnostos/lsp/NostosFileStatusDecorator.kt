package org.babelserver.intellijnostos.lsp

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import java.util.concurrent.ConcurrentHashMap

class NostosFileStatusDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val file = node.virtualFile ?: return
        if (file.extension != "nos") return
        val status = NostosFileStatusCache.statuses[file.path] ?: return

        if (status != "error") return
        data.locationString = (data.locationString ?: "") + " ✗"
    }
}

object NostosFileStatusCache {
    val statuses = ConcurrentHashMap<String, String>()

    fun updateStatuses(files: List<Pair<String, String>>) {
        statuses.clear()
        for ((path, status) in files) {
            statuses[path] = status
        }
    }
}
