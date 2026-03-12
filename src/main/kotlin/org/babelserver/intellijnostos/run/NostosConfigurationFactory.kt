package org.babelserver.intellijnostos.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project

class NostosConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = NostosConfigurationType.ID

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        NostosRunConfiguration(project, this, "Nostos")

    override fun getOptionsClass(): Class<out BaseState> =
        NostosRunConfigurationOptions::class.java
}
