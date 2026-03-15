package org.babelserver.intellijnostos.lsp

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient

class NostosLspClient(private val project: Project) : LanguageClient {

    private val log = Logger.getInstance(NostosLspClient::class.java)
    var diagnosticsHandler: ((PublishDiagnosticsParams) -> Unit)? = null

    override fun telemetryEvent(obj: Any?) {
        // nostos-lsp does not send telemetry events
    }

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
        log.info("Received ${diagnostics.diagnostics.size} diagnostics for ${diagnostics.uri}")
        for (d in diagnostics.diagnostics) {
            log.info("  [${d.severity}] ${d.range.start.line}:${d.range.start.character}-${d.range.end.line}:${d.range.end.character} ${d.message}")
        }
        diagnosticsHandler?.invoke(diagnostics)
    }

    override fun showMessage(params: MessageParams) {
        log.info("LSP message [${params.type}]: ${params.message}")
    }

    override fun showMessageRequest(params: ShowMessageRequestParams) = null

    @Suppress("unused") // Called by lsp4j via reflection
    @JsonNotification("nostos/fileStatus")
    fun fileStatus(params: com.google.gson.JsonObject) {
        val files = params.getAsJsonArray("files") ?: return
        val statuses = files.mapNotNull { entry ->
            val obj = entry.asJsonObject
            val path = obj["path"]?.asString ?: return@mapNotNull null
            val status = obj["status"]?.asString ?: return@mapNotNull null
            path to status
        }
        log.info("File status update: ${statuses.size} files")
        NostosFileStatusCache.updateStatuses(statuses)
        ApplicationManager.getApplication().invokeLater {
            ProjectView.getInstance(project).refresh()
        }
    }

    override fun logMessage(params: MessageParams) {
        when (params.type) {
            MessageType.Error -> log.error("LSP: ${params.message}")
            MessageType.Warning -> log.warn("LSP: ${params.message}")
            else -> log.info("LSP: ${params.message}")
        }
    }
}
