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
        val configured = config.nostosExecutable.ifBlank {
            NostosAppSettings.getInstance().getEffectiveNostosPath()
        }
        val nostos = resolveAbsoluteExecutable(configured)

        if (nostos == null) {
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

        val commandLine = if (config.binName.isNotBlank()) {
            // Running a [[bin]] entry point: nostos resolves it against the
            // project directory, selected with --bin. The bin name originates
            // from a project's nostos.toml (or a saved run config), so validate
            // it here before it becomes a command-line argument.
            if (!NostosManifest.isValidBinName(config.binName)) {
                throw ExecutionException("Invalid Nostos bin name: '${config.binName}'")
            }
            val projectDir = config.workingDirectory.ifBlank { config.project.basePath ?: "." }
            GeneralCommandLine(nostos, projectDir, "--bin", config.binName)
        } else {
            // If the script is main.nos and has sibling .nos files, run the directory
            // instead of the file — Nostos resolves modules relative to the directory.
            val scriptFile = File(config.scriptPath)
            val target = if (scriptFile.name == "main.nos") {
                scriptFile.parent
            } else {
                config.scriptPath
            }
            GeneralCommandLine(nostos, target)
        }

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

    /**
     * Resolves [configured] to an absolute executable path, or null if none can
     * be found. A relative or bare name (e.g. "nostos") is never launched as-is:
     * the process runs with the project directory as its working directory, and
     * on some platforms a non-absolute name resolves against that directory,
     * which would let an untrusted project ship its own "nostos" binary. Bare
     * names are instead resolved against PATH and well-known install locations.
     */
    private fun resolveAbsoluteExecutable(configured: String): String? {
        val file = File(configured)
        if (file.isAbsolute) {
            return if (file.canExecute()) file.path else null
        }
        return NostosAppSettings.detectNostos()
    }
}
