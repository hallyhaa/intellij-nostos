package org.babelserver.intellijnostos.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import java.io.File

class NostosRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : LocatableConfigurationBase<NostosRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): NostosRunConfigurationOptions =
        super.getOptions() as NostosRunConfigurationOptions

    var scriptPath: String
        get() = options.scriptPath
        set(value) { options.scriptPath = value }

    var nostosExecutable: String
        get() = options.nostosExecutable
        set(value) { options.nostosExecutable = value }

    var arguments: String
        get() = options.arguments
        set(value) { options.arguments = value }

    var workingDirectory: String
        get() = options.workingDirectory
        set(value) { options.workingDirectory = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        NostosSettingsEditor(project)

    override fun checkConfiguration() {
        if (scriptPath.isBlank()) {
            throw RuntimeConfigurationError("Script path is not specified")
        }
        val file = File(scriptPath)
        if (!file.exists()) {
            throw RuntimeConfigurationWarning("Script file does not exist: $scriptPath")
        }
        if (!file.name.endsWith(".nos")) {
            throw RuntimeConfigurationWarning("File is not a Nostos (.nos) file")
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        NostosRunProfileState(this, environment)

    override fun suggestedName(): String? {
        if (scriptPath.isBlank()) return null
        return File(scriptPath).nameWithoutExtension
    }
}
