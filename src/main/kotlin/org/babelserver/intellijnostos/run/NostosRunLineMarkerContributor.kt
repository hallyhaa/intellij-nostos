package org.babelserver.intellijnostos.run

import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.babelserver.intellijnostos.NostosFile
import java.io.File

class NostosRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        if (element !is LeafPsiElement) return null
        val file = element.containingFile as? NostosFile ?: return null

        // Mark the very first token of the file only.
        if (element.textRange.startOffset != 0) return null

        // If the file hosts [[bin]] entry points, offer to run each of them.
        val path = file.virtualFile?.path
        if (path != null) {
            val hostedBins = NostosBinDiscovery.binsInModule(File(path))
            val projectRoot = if (hostedBins.isNotEmpty()) NostosBinDiscovery.projectRoot(File(path)) else null
            if (projectRoot != null) {
                val actions = hostedBins.map { RunBinAction(projectRoot, it) }.toTypedArray<AnAction>()
                return Info(AllIcons.RunConfigurations.TestState.Run, actions) { "Run Nostos entry point" }
            }
        }

        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            ExecutorAction.getActions(1),
        ) { "Run '${file.name}'" }
    }

    /** Runs a single `[[bin]]` entry point via an ephemeral run configuration. */
    private class RunBinAction(
        private val projectRoot: File,
        private val bin: NostosBin,
    ) : AnAction(
        "Run '${bin.name}'",
        "Run the Nostos '${bin.name}' entry point",
        AllIcons.RunConfigurations.TestState.Run,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            val factory = NostosConfigurationType.getInstance().configurationFactories[0]
            val settings = RunManager.getInstance(project).createConfiguration(bin.name, factory)
            (settings.configuration as NostosRunConfiguration).apply {
                binName = bin.name
                workingDirectory = projectRoot.path
                scriptPath = File(projectRoot, "main.nos").path
            }
            ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
        }
    }
}
