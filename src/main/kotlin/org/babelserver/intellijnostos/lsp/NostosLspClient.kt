package org.babelserver.intellijnostos.lsp

import com.intellij.ide.projectView.ProjectView
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture

class NostosLspClient(private val project: Project) : LanguageClient {

    private val log = Logger.getInstance(NostosLspClient::class.java)
    var diagnosticsHandler: ((PublishDiagnosticsParams) -> Unit)? = null

    internal val progressTracker = NostosLspProgressTracker(project)

    override fun telemetryEvent(obj: Any?) {
        // nostos-lsp does not send telemetry events
    }

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
        // Per-diagnostic detail is debug-only: at INFO it formats and writes one
        // line per diagnostic on every publish (i.e. on every edit), which is
        // real string/IO work on the LSP read thread.
        if (log.isDebugEnabled) {
            log.debug("Received ${diagnostics.diagnostics.size} diagnostics for ${diagnostics.uri}")
            for (d in diagnostics.diagnostics) {
                log.debug("  [${d.severity}] ${d.range.start.line}:${d.range.start.character}-${d.range.end.line}:${d.range.end.character} ${d.message}")
            }
        }
        diagnosticsHandler?.invoke(diagnostics)
    }

    override fun showMessage(params: MessageParams) {
        log.info("LSP message [${params.type}]: ${params.message}")
        // window/showMessage is the server asking for a user-visible message.
        // Nostos-lsp uses it to report executeCommand outcomes (live commits,
        // cache maintenance), so show it as a notification balloon.
        val message = params.message ?: return
        val type = when (params.type) {
            MessageType.Error -> NotificationType.ERROR
            MessageType.Warning -> NotificationType.WARNING
            else -> NotificationType.INFORMATION
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Nostos")
            .createNotification(message, type)
            .notify(project)
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
        log.debug("File status update: ${statuses.size} files")
        // Only repaint when the statuses actually changed.
        if (NostosFileStatusCache.getInstance(project).updateStatuses(statuses)) {
            ApplicationManager.getApplication().invokeLater({
                ProjectView.getInstance(project).refresh()
                // Editor tabs cache file icons, so recompute them
                FileEditorManagerEx.getInstanceEx(project).refreshIcons()
            }, project.disposed)
        }
    }

    override fun logMessage(params: MessageParams) {
        when (params.type) {
            MessageType.Error -> log.error("LSP: ${params.message}")
            MessageType.Warning -> log.warn("LSP: ${params.message}")
            else -> log.info("LSP: ${params.message}")
        }
    }

    override fun createProgress(params: WorkDoneProgressCreateParams): CompletableFuture<Void> {
        // Acknowledge the token — the actual Begin/Report/End notifications arrive
        // via notifyProgress and are routed through the tracker there.
        return CompletableFuture.completedFuture(null)
    }

    override fun notifyProgress(params: ProgressParams) {
        val token = tokenAsString(params.token) ?: return
        val value = params.value ?: return
        if (!value.isLeft) return
        when (val notification = value.left) {
            is WorkDoneProgressBegin ->
                progressTracker.begin(
                    token,
                    notification.title ?: "Nostos",
                    notification.message,
                    notification.percentage,
                )
            is WorkDoneProgressReport ->
                progressTracker.report(token, notification.message, notification.percentage)
            is WorkDoneProgressEnd ->
                progressTracker.end(token, notification.message)
        }
    }

    private fun tokenAsString(token: Either<String, Int>?): String? = when {
        token == null -> null
        token.isLeft -> token.left
        else -> token.right.toString()
    }
}
