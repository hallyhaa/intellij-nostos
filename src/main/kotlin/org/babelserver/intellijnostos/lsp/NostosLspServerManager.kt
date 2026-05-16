package org.babelserver.intellijnostos.lsp

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.Version
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.babelserver.intellijnostos.NostosFileType
import org.babelserver.intellijnostos.settings.NostosAppSettings
import org.babelserver.intellijnostos.settings.NostosSettingsConfigurable
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class NostosLspServerManager(private val project: Project) : Disposable {

    private val log = Logger.getInstance(NostosLspServerManager::class.java)
    private var process: Process? = null
    private var server: LanguageServer? = null
    private var client: NostosLspClient? = null
    private var initialized = false
    private val openFiles = mutableSetOf<String>()

    var diagnosticsListener: ((PublishDiagnosticsParams) -> Unit)? = null
        set(value) {
            field = value
            client?.diagnosticsHandler = value
        }

    /**
     * Starts the language server if it is not already running.
     *
     * @param notifyIfMissing when true, a warning notification is shown if nostos
     *   or nostos-lsp cannot be located. Callers pass false for projects without
     *   Nostos files to avoid pestering unrelated projects.
     */
    fun startIfNeeded(notifyIfMissing: Boolean = true) {
        if (initialized) return
        val lspPath = when (val lookup = resolveLspExecutable()) {
            is LspLookup.Found -> lookup.path
            is LspLookup.LspMissing -> {
                log.info("nostos found in ${lookup.nostosDir}, but no nostos-lsp beside it")
                if (notifyIfMissing) notifyLspMissing(lookup.nostosDir)
                return
            }
            LspLookup.NostosMissing -> {
                log.info("No nostos installation found; LSP features disabled")
                if (notifyIfMissing) notifyNostosMissing()
                return
            }
        }

        if (!checkMinimumVersion()) return

        log.info("Starting nostos-lsp: $lspPath")

        try {
            val processBuilder = ProcessBuilder(lspPath)
                .directory(File(project.basePath ?: "."))
                .redirectErrorStream(false)
            process = processBuilder.start()

            val lspClient = NostosLspClient(project)
            lspClient.diagnosticsHandler = diagnosticsListener
            client = lspClient

            val launcher = LSPLauncher.createClientLauncher(
                lspClient,
                process!!.inputStream,
                process!!.outputStream
            )
            server = launcher.remoteProxy
            launcher.startListening()

            val initParams = InitializeParams().apply {
                @Suppress("DEPRECATION")
                rootUri = project.basePath?.let { File(it).toURI().toString() }
                capabilities = ClientCapabilities().apply {
                    textDocument = TextDocumentClientCapabilities().apply {
                        synchronization = SynchronizationCapabilities().apply {
                            this.didSave = true
                            this.willSave = false
                            dynamicRegistration = false
                        }
                        publishDiagnostics = PublishDiagnosticsCapabilities()
                        completion = CompletionCapabilities()
                        hover = HoverCapabilities()
                        definition = DefinitionCapabilities()
                        references = ReferencesCapabilities()
                        semanticTokens = SemanticTokensCapabilities().apply {
                            requests = SemanticTokensClientCapabilitiesRequests().apply {
                                full = org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(true)
                            }
                            tokenTypes = listOf("namespace", "type", "function", "variable", "parameter", "property", "enumMember", "keyword", "string", "number", "operator", "comment", "method", "struct", "enum", "interface", "typeParameter")
                            tokenModifiers = listOf("declaration", "definition")
                            formats = listOf(TokenFormat.Relative)
                        }
                    }
                }
            }

            server!!.initialize(initParams).thenAccept { result ->
                log.info("LSP initialized: ${result.capabilities}")
                server!!.initialized(InitializedParams())
                initialized = true
                notifyOpenFiles()
            }.get(10, TimeUnit.SECONDS)

            setupListeners()
        } catch (e: Exception) {
            log.warn("Failed to start nostos-lsp", e)
            stopServer()
        }
    }

    private fun setupListeners() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                if (file.fileType == NostosFileType) {
                    didOpen(file)
                }
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                if (file.fileType == NostosFileType) {
                    didClose(file)
                }
            }
        })

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (file.fileType == NostosFileType) {
                    didChange(file, event.document.text)
                }
            }
        }, this)
    }

    private fun notifyOpenFiles() {
        val fem = FileEditorManager.getInstance(project)
        for (file in fem.openFiles) {
            if (file.fileType == NostosFileType) {
                didOpen(file)
            }
        }
    }

    fun didOpen(file: VirtualFile) {
        val uri = file.toUri()
        if (!initialized || !openFiles.add(uri)) return
        val text = ApplicationManager.getApplication().runReadAction<String?> {
            FileDocumentManager.getInstance().getDocument(file)?.text
        } ?: return
        server?.textDocumentService?.didOpen(DidOpenTextDocumentParams(
            TextDocumentItem(uri, "nostos", 1, text)
        ))
    }

    fun didClose(file: VirtualFile) {
        val uri = file.toUri()
        if (!initialized || !openFiles.remove(uri)) return
        server?.textDocumentService?.didClose(DidCloseTextDocumentParams(
            TextDocumentIdentifier(uri)
        ))
    }

    private var version = 2

    fun didChange(file: VirtualFile, content: String) {
        val uri = file.toUri()
        if (!initialized || uri !in openFiles) return
        server?.textDocumentService?.didChange(DidChangeTextDocumentParams(
            VersionedTextDocumentIdentifier(uri, version++),
            listOf(TextDocumentContentChangeEvent(content))
        ))
    }

    val activeServer: LanguageServer? get() = if (initialized) server else null

    private fun stopServer() {
        try {
            server?.shutdown()?.get(5, TimeUnit.SECONDS)
            server?.exit()
        } catch (_: Exception) {
            // Best-effort shutdown — process is force-killed below regardless
        }
        process?.destroyForcibly()
        process = null
        server = null
        client = null
        initialized = false
        openFiles.clear()
    }

    override fun dispose() {
        stopServer()
    }

    private fun checkMinimumVersion(): Boolean {
        val versionStr = NostosAppSettings.getVersion(
            NostosAppSettings.getInstance().getEffectiveNostosPath()
        ) ?: return true // Can't determine version — proceed optimistically

        val version = parseNostosVersion(versionStr)
        if (version != null && version < MIN_VERSION) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Nostos")
                .createNotification(
                    "Nostos $versionStr is too old",
                    "The Nostos IntelliJ plugin requires version $MIN_VERSION or later for LSP diagnostics.",
                    NotificationType.WARNING
                )
                .notify(project)
            return false
        }
        return true
    }

    companion object {
        internal val MIN_VERSION = Version(0, 2, 18)

        private const val NOSTOS_INSTALL_URL = "https://heynostos.tech"

        /** Parse "nostos 0.2.17" or "0.2.17" into a Version. */
        internal fun parseNostosVersion(versionOutput: String): Version? {
            val numPart = versionOutput.trim().split(" ").last()
            return Version.parseVersion(numPart)
        }

        fun getInstance(project: Project): NostosLspServerManager =
            project.getService(NostosLspServerManager::class.java)
    }

    private fun resolveLspExecutable(): LspLookup {
        val settings = NostosAppSettings.getInstance()
        return NostosExecutableResolver(
            effectiveNostosPath = { settings.getEffectiveNostosPath() },
            detectNostos = { NostosAppSettings.detectNostos() },
            isExecutable = { it.canExecute() },
        ).resolve()
    }

    private fun notifyNostosMissing() {
        nostosNotification(
            "Nostos not found",
            "The Nostos plugin could not find a <code>nostos</code> installation, " +
                "so language server features such as diagnostics are unavailable."
        )
    }

    private fun notifyLspMissing(nostosDir: String) {
        nostosNotification(
            "nostos-lsp not found",
            "Found <code>nostos</code> in $nostosDir, but no <code>nostos-lsp</code> executable " +
                "beside it. Language server features are unavailable until nostos-lsp is installed."
        )
    }

    private fun nostosNotification(title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Nostos")
            .createNotification(title, content, NotificationType.WARNING)
            .addAction(NotificationAction.createSimple("Configure path…") {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, NostosSettingsConfigurable::class.java)
            })
            .addAction(NotificationAction.createSimple("Installation instructions") {
                BrowserUtil.browse(NOSTOS_INSTALL_URL)
            })
            .notify(project)
    }

}

private fun VirtualFile.toUri(): String = URI("file", "", this.path, null).toString()
