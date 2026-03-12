package org.babelserver.intellijnostos.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

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
        configuration.name = file.nameWithoutExtension
        configuration.workingDirectory = context.project.basePath ?: ""
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
