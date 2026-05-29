package org.babelserver.intellijnostos.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import java.io.File

class NostosRunConfigurationProducer : LazyRunConfigurationProducer<NostosRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        NostosConfigurationType.getInstance().configurationFactories[0]

    override fun setupConfigurationFromContext(
        configuration: NostosRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (file.extension != "nos") return false

        configuration.scriptPath = file.path
        configuration.workingDirectory = context.project.basePath ?: ""

        // In a project that declares [[bin]] entry points, prefer running the
        // default one over the bare file.
        val bins = NostosBinDiscovery.binsFor(File(file.path))
        val defaultBin = NostosBinDiscovery.defaultBin(bins)
        if (defaultBin != null) {
            val projectRoot = NostosBinDiscovery.projectRoot(File(file.path))
            configuration.binName = defaultBin.name
            if (projectRoot != null) configuration.workingDirectory = projectRoot.path
            configuration.name = defaultBin.name
        } else {
            configuration.binName = ""
            configuration.name = file.nameWithoutExtension
        }
        return true
    }

    override fun isConfigurationFromContext(
        configuration: NostosRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        return configuration.scriptPath == file.path
    }
}
