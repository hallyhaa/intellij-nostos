package org.babelserver.intellijnostos

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Consumer
import java.awt.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Wires the IDE's "Report" button (in the uncaught-exception dialog) to a
 * pre-filled GitHub issue. No server and no credentials: we open the browser at
 * the new-issue URL with the title and body already populated, and the user
 * presses submit on GitHub. The plugin and IDE versions plus the stacktrace are
 * filled in for them.
 */
class NostosErrorReportSubmitter : ErrorReportSubmitter() {

    override fun getReportActionText() = "Report to plugin author on GitHub"

    override fun submit(
        events: Array<IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        val event = events.firstOrNull()
        val throwableText = event?.throwableText ?: event?.message ?: "(no stacktrace)"

        val title = event?.message?.takeIf { it.isNotBlank() }
            ?: throwableText.lineSequence().firstOrNull()?.take(120)
            ?: "Unhandled exception"

        val body = buildBody(throwableText, additionalInfo)
        BrowserUtil.browse(buildIssueUrl(title, body))

        // We can't know whether the user actually filed the issue, so report it
        // as submitted to dismiss the dialog's spinner.
        consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
        return true
    }

    private fun buildBody(throwableText: String, additionalInfo: String?): String {
        val pluginVersion = pluginDescriptor?.version ?: "unknown"
        val appInfo = ApplicationInfo.getInstance()

        return buildString {
            appendLine("**What happened?**")
            appendLine(additionalInfo?.takeIf { it.isNotBlank() } ?: "_(please describe what you were doing)_")
            appendLine()
            appendLine("**Environment**")
            appendLine("- Plugin: Nostos $pluginVersion")
            appendLine("- IDE: ${appInfo.fullApplicationName} (build ${appInfo.build.asString()})")
            appendLine("- OS: ${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION}")
            appendLine("- JRE: ${SystemInfo.JAVA_VERSION}")
            appendLine()
            appendLine("**Stacktrace**")
            appendLine("```")
            // GitHub URLs are bounded (~8 KB); keep room for the rest of the query.
            appendLine(throwableText.take(MAX_STACKTRACE_CHARS))
            appendLine("```")
        }
    }

    companion object {
        private const val ISSUE_BASE_URL = "https://github.com/hallyhaa/intellij-nostos/issues/new"
        private const val MAX_STACKTRACE_CHARS = 6000

        /** Builds the pre-filled new-issue URL. Internal so it can be unit-tested. */
        internal fun buildIssueUrl(title: String, body: String): String {
            val t = URLEncoder.encode(title, StandardCharsets.UTF_8)
            val b = URLEncoder.encode(body, StandardCharsets.UTF_8)
            return "$ISSUE_BASE_URL?labels=bug&title=$t&body=$b"
        }
    }
}
