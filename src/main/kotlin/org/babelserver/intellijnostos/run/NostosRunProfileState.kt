package org.babelserver.intellijnostos.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.util.execution.ParametersListUtil
import org.babelserver.intellijnostos.settings.NostosAppSettings
import org.babelserver.intellijnostos.settings.NostosSettingsConfigurable
import java.io.File

class NostosRunProfileState(
    private val config: NostosRunConfiguration,
    environment: ExecutionEnvironment
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val nostos = config.nostosExecutable.ifBlank {
            NostosAppSettings.getInstance().getEffectiveNostosPath()
        }

        if (!File(nostos).canExecute() && NostosAppSettings.detectNostos() == null) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Nostos")
                .createNotification(
                    "Nostos interpreter not found",
                    "Searched /usr/bin, /usr/local/bin, and PATH.",
                    NotificationType.ERROR
                )
                .addAction(NotificationAction.createSimple("Configure\u2026") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        config.project, NostosSettingsConfigurable::class.java
                    )
                })
                .notify(config.project)
            throw ExecutionException("Nostos interpreter not found")
        }

        // If the script is main.nos and has sibling .nos files, run the directory
        // instead of the file — Nostos resolves modules relative to the directory.
        val scriptFile = File(config.scriptPath)
        val target = if (scriptFile.name == "main.nos") {
            scriptFile.parent
        } else {
            config.scriptPath
        }

        val commandLine = GeneralCommandLine(nostos, target)

        if (config.arguments.isNotBlank()) {
            commandLine.addParameters(ParametersListUtil.parse(config.arguments))
        }

        val workDir = config.workingDirectory.ifBlank { config.project.basePath }
        commandLine.withWorkDirectory(workDir)
        commandLine.charset = Charsets.UTF_8

        val processHandler = ProcessHandlerFactory.getInstance()
            .createColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(processHandler)
        return processHandler
    }
}
