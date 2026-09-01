package org.babelserver.intellijnostos.lsp

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState
import com.intellij.execution.console.BaseConsoleExecuteActionHandler
import com.intellij.execution.console.ConsoleExecuteAction
import com.intellij.execution.console.ConsoleHistoryController
import com.intellij.execution.console.ConsoleRootType
import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.util.concurrent.TimeUnit

/** Marks the console document so [NostosReplCompletionContributor] only fires there. */
internal val NOSTOS_REPL_FILE = Key.create<Boolean>("nostos.repl.file")

/**
 * Whether [file] is the REPL console's input document. The LSP-backed editor
 * features must skip it: the server has never seen a didOpen for it, so their
 * per-edit requests would stall against an unknown document (blocking calls
 * inside read actions, which every keystroke's write action then waits for).
 * The REPL has its own completion; the other features are meaningless there.
 */
internal fun isNostosReplFile(file: com.intellij.openapi.vfs.VirtualFile?): Boolean =
    file?.getUserData(NOSTOS_REPL_FILE) == true

/**
 * The "Nostos REPL" tool window: a single console surface (like the Python
 * console) where expressions typed at the `nostos>` prompt are evaluated in
 * the running live system via nostos-lsp's "nostos.eval". The input line is a
 * real Nostos editor, so it gets syntax highlighting, and typing anywhere in
 * the console lands at the prompt. Up/Down walk persisted history.
 */
class NostosReplToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // A "+" button on the tab strip opens another REPL, terminal-style.
        (toolWindow as? com.intellij.openapi.wm.ex.ToolWindowEx)?.setTabActions(object : com.intellij.openapi.project.DumbAwareAction(
            "New Nostos REPL", "Open another REPL tab", com.intellij.icons.AllIcons.General.Add,
        ) {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) =
                openNewConsole(project, toolWindow)
        })
        // Closing the last tab with the tab's X leaves the window empty and the
        // factory is never called again; keep a fresh console ready instead.
        toolWindow.contentManager.addContentManagerListener(object : com.intellij.ui.content.ContentManagerListener {
            override fun contentRemoved(event: com.intellij.ui.content.ContentManagerEvent) {
                if (toolWindow.contentManager.contentCount == 0) openNewConsole(project, toolWindow)
            }
        })
        openNewConsole(project, toolWindow)
    }

    private fun openNewConsole(project: Project, toolWindow: ToolWindow) {
        // minHistoryLineCount is 0 (default 2) so the prompt starts at the very
        // top of an empty console, bash-style, instead of below two blank lines.
        val console = object : LanguageConsoleImpl(project, "Nostos REPL", org.babelserver.intellijnostos.NostosLanguage) {
            override val minHistoryLineCount: Int get() = 0

            // Bash spacing: the history must never end with a newline (results
            // are printed without one), or the editor renders a permanent
            // blank line between the last output and the prompt below. The
            // separating newline is added here, in front of the next prompt.
            override fun doAddPromptToHistory() {
                flushDeferredText()
                val history = historyViewer.document.charsSequence
                if (history.isNotEmpty() && history.last() != '\n') {
                    print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
                    flushDeferredText()
                }
                super.doAddPromptToHistory()
            }
        }
        console.prompt = "nostos>"
        console.virtualFile.putUserData(NOSTOS_REPL_FILE, true)

        // Bash-style Ctrl+C: abandon the current line — echo it with a ^C into
        // history and present a fresh prompt. Only when nothing is selected, so
        // Ctrl+C with a selection still copies (the action being disabled lets
        // the keystroke fall through to the platform's copy).
        object : com.intellij.openapi.project.DumbAwareAction() {
            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
            override fun update(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                e.presentation.isEnabled = !console.consoleEditor.selectionModel.hasSelection()
            }
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                val abandoned = console.consoleEditor.document.text
                console.doAddPromptToHistory()
                console.print("$abandoned^C", ConsoleViewContentType.USER_INPUT)
                console.flushDeferredText()
                console.setInputText("")
            }
        }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet.fromString("control C"),
            console.consoleEditor.component,
        )

        val executeAction = ConsoleExecuteAction(console, NostosReplExecuteHandler(project))
        executeAction.registerCustomShortcutSet(executeAction.shortcutSet, console.consoleEditor.component)
        // The registered instance, not a fresh one: RootTypes are an EP, and
        // the history controller resolves storage paths through the registry.
        val rootType = com.intellij.ide.scratch.RootType.findByClass(NostosReplRootType::class.java)
        ConsoleHistoryController(rootType, "nostos-repl", console).install()

        NostosLspServerManager.getInstance(project).startIfNeeded(notifyIfMissing = false)

        val contentManager = toolWindow.contentManager
        val content = ContentFactory.getInstance().createContent(
            console.component,
            "REPL ${nextTabNumber(contentManager)}",
            false,
        )
        // Disposing the content (Ctrl+D, tab close, window close) disposes the console.
        content.setDisposer(console)
        // Focus lands at the prompt when the tab is selected.
        content.preferredFocusableComponent = console.consoleEditor.contentComponent
        contentManager.addContent(content)
        contentManager.setSelectedContent(content, true)

        // Bash-style Ctrl+D: terminate this REPL session for real (dispose it).
        // With more tabs open, only this tab closes; ending the last session
        // also hides the window, like exiting the last shell in a terminal.
        // Persisted history makes Up-arrow recall earlier sessions' lines.
        object : com.intellij.openapi.project.DumbAwareAction() {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                val wasLastTab = contentManager.contentCount == 1
                // With no contents left, the contentRemoved listener opens a
                // fresh console for the next time the window is shown.
                contentManager.removeContent(content, true)
                if (wasLastTab) toolWindow.hide()
            }
        }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet.fromString("control D"),
            console.component,
        )

        // After component init: accessing console.component above ran the
        // platform's setupLanguageConsoleEditor, which sets its own
        // additionalLinesCount. Zero virtual trailing lines is what lets an
        // empty history collapse and keeps the prompt snug under the last
        // output line, bash-style.
        console.historyViewer.settings.additionalLinesCount = 0
        console.consoleEditor.settings.additionalLinesCount = 0
    }

    /** Smallest number not already taken by an open "REPL n" tab. */
    private fun nextTabNumber(contentManager: com.intellij.ui.content.ContentManager): Int {
        val taken = contentManager.contents
            .mapNotNull { it.displayName?.removePrefix("REPL ")?.toIntOrNull() }
            .toSet()
        return generateSequence(1) { it + 1 }.first { it !in taken }
    }
}

/** Ties the console history to a persisted root, so it survives IDE restarts. */
internal class NostosReplRootType : ConsoleRootType("nostos", "Nostos")

/**
 * Runs one committed prompt line: sends it to nostos-lsp and prints the result
 * into the console. ConsoleExecuteAction has already moved the line from the
 * input editor into the history view when this is called.
 */
private class NostosReplExecuteHandler(private val project: Project) : BaseConsoleExecuteActionHandler(true) {

    override fun execute(text: String, console: LanguageConsoleView) {
        // The echo of the executed line was queued in the console's deferred
        // print buffer just before this call, while the input editor was
        // cleared synchronously. Flush now so the line reappears in history in
        // the same EDT event it vanished from the prompt — no visible move.
        (console as? LanguageConsoleImpl)?.flushDeferredText()

        val expression = text.trim()
        if (expression.isEmpty()) return
        val future = NostosLspServerManager.getInstance(project).executeCommand("nostos.eval", listOf(expression))
        if (future == null) {
            printBelowEcho(console, "The Nostos language server is not running. Open a Nostos project to start it.", ConsoleViewContentType.ERROR_OUTPUT)
            return
        }
        future.whenComplete { response, error ->
            ApplicationManager.getApplication().invokeLater({
                if (error != null) {
                    printBelowEcho(console, "Request failed: ${error.message}", ConsoleViewContentType.ERROR_OUTPUT)
                } else {
                    val output = formatEvalResponse(response)
                    val type =
                        if (output.isError) ConsoleViewContentType.ERROR_OUTPUT
                        else ConsoleViewContentType.NORMAL_OUTPUT
                    printBelowEcho(console, output.text, type)
                }
            }, project.disposed)
        }
    }

    /**
     * Prints [message] under the echoed line, without a trailing newline: the
     * newline separating this from the next prompt is added by the console's
     * doAddPromptToHistory override, so the history never ends with a blank
     * line and the prompt hugs the last output.
     */
    private fun printBelowEcho(console: LanguageConsoleView, message: String, type: ConsoleViewContentType) {
        console.print(message.trimEnd('\n'), type)
        (console as? LanguageConsoleImpl)?.flushDeferredText()
    }
}

/**
 * Ctrl+Space completion at the REPL prompt, backed by "nostos.replComplete"
 * (which knows REPL-specific context such as constructor fields). Fires only
 * in the REPL console's document; regular .nos editors keep their LSP-based
 * completion. Runs on the completion thread, so the short blocking wait is
 * acceptable — the same convention the editor's LSP completion uses.
 */
/**
 * No completion autopopup at the REPL prompt: a popup that opens while typing
 * swallows the Enter meant to run the line (inserting a suggestion instead).
 * Completion is still available on explicit Ctrl+Space.
 */
internal class NostosReplCompletionConfidence : CompletionConfidence() {

    override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState =
        if (psiFile.originalFile.virtualFile?.getUserData(NOSTOS_REPL_FILE) == true) ThreeState.YES
        else ThreeState.UNSURE
}

internal class NostosReplCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val virtualFile = parameters.originalFile.virtualFile ?: return
        if (virtualFile.getUserData(NOSTOS_REPL_FILE) != true) return
        val project = parameters.originalFile.project
        val text = parameters.originalFile.text
        val future = NostosLspServerManager.getInstance(project)
            .executeCommand("nostos.replComplete", listOf(text, parameters.offset)) ?: return
        val response = try {
            future.get(COMPLETION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            return
        }
        for (item in parseReplCompletions(response)) {
            result.addElement(LookupElementBuilder.create(item))
        }
    }
}

private const val COMPLETION_TIMEOUT_MS = 1_500L
