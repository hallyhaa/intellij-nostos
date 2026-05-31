package org.babelserver.intellijnostos.lsp

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.util.Alarm
import org.babelserver.intellijnostos.NostosDiagnosticsCache
import org.babelserver.intellijnostos.NostosFileType
import org.babelserver.intellijnostos.settings.NostosAppSettings
import org.babelserver.intellijnostos.settings.NostosSettingsConfigurable
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class NostosLspServerManager(private val project: Project) : Disposable {

    private val log = Logger.getInstance(NostosLspServerManager::class.java)
    private var process: Process? = null
    private var server: LanguageServer? = null
    private var client: NostosLspClient? = null
    private var initialized = false
    private val openFiles = mutableSetOf<String>()

    /**
     * Whether the server negotiated incremental document sync at initialize.
     * When true we send ranged edits; otherwise we resend the whole document.
     */
    private var incrementalSync = false

    /** Per-document LSP version, keyed by URI. Reset when the server stops. */
    private val documentVersions = ConcurrentHashMap<String, Int>()

    /**
     * Documents edited since the last flush. A burst of typing collapses into a
     * single didChange per quiet window. In full-sync mode we keep the Document
     * and read its text once at flush; in incremental mode we accumulate the
     * ranged edits as they happen (each tiny, no full-buffer copy) and send them
     * as one ordered batch.
     */
    private val pendingFullText = ConcurrentHashMap<VirtualFile, Document>()
    private val pendingIncremental = ConcurrentHashMap<VirtualFile, MutableList<TextDocumentContentChangeEvent>>()
    private val changeDebounce by lazy { Alarm(Alarm.ThreadToUse.SWING_THREAD, this) }

    /** Workspace root last handed to nostos-lsp, reused when restarting. */
    private var lastLspRoot: String? = null

    /** Holds the editor/VFS listeners so a restart can dispose and re-register them. */
    private var listenersDisposable: Disposable? = null

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
     * @param lspRoot the workspace root to hand to nostos-lsp (the directory
     *   containing nostos.toml). Falls back to the project base path when null.
     */
    fun startIfNeeded(notifyIfMissing: Boolean = true, lspRoot: String? = null) {
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

        val rootDir = lspRoot ?: lastLspRoot ?: project.basePath
        lastLspRoot = rootDir
        log.info("Starting nostos-lsp: $lspPath (root: $rootDir)")

        try {
            val processBuilder = ProcessBuilder(lspPath)
                .directory(File(rootDir ?: "."))
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
                // nostos-lsp is single-root, so we hand it one folder. workspaceFolders
                // replaces the deprecated rootUri; the server reads either, preferring
                // rootUri and falling back to the first workspace folder.
                workspaceFolders = rootDir?.let {
                    // Same URI spelling as the per-file requests, so a server
                    // that prefix-matches document URIs against the root agrees.
                    listOf(WorkspaceFolder(NostosLspUri.of(it), File(it).name))
                }
                capabilities = ClientCapabilities().apply {
                    textDocument = TextDocumentClientCapabilities().apply {
                        synchronization = SynchronizationCapabilities().apply {
                            this.didSave = true
                            this.willSave = false
                            dynamicRegistration = false
                        }
                        publishDiagnostics = PublishDiagnosticsCapabilities()
                        completion = CompletionCapabilities()
                        signatureHelp = SignatureHelpCapabilities()
                        hover = HoverCapabilities()
                        definition = DefinitionCapabilities()
                        references = ReferencesCapabilities()
                        rename = RenameCapabilities().apply { prepareSupport = true }
                        semanticTokens = SemanticTokensCapabilities().apply {
                            requests = SemanticTokensClientCapabilitiesRequests().apply {
                                full = org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(true)
                            }
                            tokenTypes = listOf("namespace", "type", "function", "variable", "parameter", "property", "enumMember", "keyword", "string", "number", "operator", "comment", "method", "struct", "enum", "interface", "typeParameter")
                            tokenModifiers = listOf("declaration", "definition")
                            formats = listOf(TokenFormat.Relative)
                        }
                        inlayHint = InlayHintCapabilities()
                    }
                    workspace = WorkspaceClientCapabilities().apply {
                        didChangeWatchedFiles = DidChangeWatchedFilesCapabilities()
                    }
                    window = WindowClientCapabilities().apply {
                        workDoneProgress = true
                    }
                }
            }

            server!!.initialize(initParams).thenAccept { result ->
                log.info("LSP initialized: ${result.capabilities}")
                incrementalSync = isIncrementalSync(result.capabilities?.textDocumentSync)
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
        val disposable = Disposer.newDisposable("NostosLspListeners")
        Disposer.register(this, disposable)
        listenersDisposable = disposable

        val connection = project.messageBus.connect(disposable)
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
                if (!initialized) return
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (file.fileType != NostosFileType) return
                // Record the edit and debounce; flush sends one didChange per
                // quiet window. didChange's own openFiles guard filters out
                // files this server doesn't own.
                if (incrementalSync) {
                    // Capture the ranged edit now (cheap); the range arithmetic
                    // needs this event's old/new fragments.
                    pendingIncremental.getOrPut(file) { mutableListOf() }.add(incrementalChange(event))
                } else {
                    pendingFullText[file] = event.document
                }
                changeDebounce.cancelAllRequests()
                changeDebounce.addRequest(::flushPendingChanges, CHANGE_DEBOUNCE_MS)
            }
        }, disposable)

        // Report on-disk changes to .nos files that are not open in an editor.
        // Editors are covered by didChange above; this catches external edits,
        // VCS operations, and changes to files the user never opened.
        val appConnection = ApplicationManager.getApplication().messageBus.connect(disposable)
        appConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (!initialized) return
                val fileEvents = events.mapNotNull { it.toWatchedFileEvent() }
                if (fileEvents.isNotEmpty()) {
                    server?.workspaceService?.didChangeWatchedFiles(DidChangeWatchedFilesParams(fileEvents))
                }
            }
        })
    }

    /**
     * Maps a VFS event for a .nos file to an LSP [FileEvent], or null when the
     * event is irrelevant (not a .nos file, outside this project's workspace
     * root, or a file already tracked as open and therefore handled by
     * didChange).
     *
     * The VFS_CHANGES listener is application-wide, so without the root check a
     * project's server would be told about .nos changes in every other open
     * project too.
     */
    private fun VFileEvent.toWatchedFileEvent(): FileEvent? {
        if (!path.endsWith(".nos")) return null
        if (!isUnderWorkspaceRoot(path)) return null
        val uri = NostosLspUri.of(path)
        if (uri in openFiles) return null
        val type = when (this) {
            is VFileCreateEvent, is VFileCopyEvent -> FileChangeType.Created
            is VFileDeleteEvent -> FileChangeType.Deleted
            is VFileContentChangeEvent, is VFileMoveEvent -> FileChangeType.Changed
            else -> return null
        }
        return FileEvent(uri, type)
    }

    /** True when [filePath] lives inside the workspace root handed to nostos-lsp. */
    private fun isUnderWorkspaceRoot(filePath: String): Boolean {
        val root = (lastLspRoot ?: project.basePath)?.trimEnd('/') ?: return false
        return filePath == root || filePath.startsWith("$root/")
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
        if (!initialized || uri in openFiles) return
        // Read the text BEFORE marking the file open: if there's no document we
        // must not leave the URI in openFiles, or a later didChange/didClose
        // would be sent for a document the server never received a didOpen for.
        val text = ApplicationManager.getApplication().runReadAction<String?> {
            FileDocumentManager.getInstance().getDocument(file)?.text
        } ?: return
        openFiles.add(uri)
        documentVersions[uri] = 1
        server?.textDocumentService?.didOpen(DidOpenTextDocumentParams(
            TextDocumentItem(uri, "nostos", 1, text)
        ))
    }

    fun didClose(file: VirtualFile) {
        val uri = file.toUri()
        if (!initialized || !openFiles.remove(uri)) return
        documentVersions.remove(uri)
        pendingFullText.remove(file)
        pendingIncremental.remove(file)
        server?.textDocumentService?.didClose(DidCloseTextDocumentParams(
            TextDocumentIdentifier(uri)
        ))
    }

    /** Sends every document edited since the last flush, as one didChange each. */
    private fun flushPendingChanges() {
        if (pendingIncremental.isNotEmpty()) {
            val snapshot = HashMap(pendingIncremental)
            pendingIncremental.clear()
            for ((file, changes) in snapshot) sendDidChange(file, changes)
        }
        if (pendingFullText.isNotEmpty()) {
            val snapshot = HashMap(pendingFullText)
            pendingFullText.clear()
            for ((file, document) in snapshot) didChange(file, document.text)
        }
    }

    fun didChange(file: VirtualFile, content: String) {
        // Whole-document replacement (full-sync mode, or an explicit caller).
        sendDidChange(file, listOf(TextDocumentContentChangeEvent(content)))
    }

    private fun sendDidChange(file: VirtualFile, changes: List<TextDocumentContentChangeEvent>) {
        val uri = file.toUri()
        if (!initialized || uri !in openFiles) return
        // Per-document monotonic version: LSP wants the version to increase per
        // URI, not across all files from one shared counter.
        val nextVersion = documentVersions.merge(uri, 1) { old, _ -> old + 1 } ?: 1
        server?.textDocumentService?.didChange(DidChangeTextDocumentParams(
            VersionedTextDocumentIdentifier(uri, nextVersion),
            changes,
        ))
    }

    /**
     * Builds the LSP ranged change for one [DocumentEvent]. The start position
     * is read from the (new) document at the edit offset — content before the
     * offset is untouched, so its line/column is identical in the pre-edit
     * document the range must describe. The end position is that start plus the
     * shape of the removed text ([DocumentEvent.getOldFragment]).
     */
    private fun incrementalChange(event: DocumentEvent): TextDocumentContentChangeEvent {
        val doc = event.document
        val offset = event.offset
        val startLine = doc.getLineNumber(offset)
        val start = Position(startLine, offset - doc.getLineStartOffset(startLine))
        val range = Range(start, changeEndPosition(start, event.oldFragment))
        return TextDocumentContentChangeEvent(range, event.newFragment.toString())
    }

    private fun isIncrementalSync(
        sync: org.eclipse.lsp4j.jsonrpc.messages.Either<TextDocumentSyncKind, TextDocumentSyncOptions>?,
    ): Boolean = when {
        sync == null -> false
        sync.isLeft -> sync.left == TextDocumentSyncKind.Incremental
        else -> sync.right?.change == TextDocumentSyncKind.Incremental
    }

    val activeServer: LanguageServer? get() = if (initialized) server else null

    /**
     * Stops the language server and starts it again. Internal plugin primitive
     * for situations where the server's state has drifted from reality —
     * crash recovery, configured-path or workspace-root changes, and so on.
     * Not exposed to users.
     *
     * Runs synchronously (the start path blocks on initialization), so callers
     * must invoke this off the EDT.
     *
     * @param newRoot when non-null, the workspace root to re-root the server at
     *   (used after a nostos.toml is generated in a directory other than the
     *   current root). When null, the previous root is reused.
     */
    internal fun restart(newRoot: String? = null) {
        log.info("Restarting nostos-lsp${newRoot?.let { " (root: $it)" } ?: ""}")
        stopServer()
        startIfNeeded(lspRoot = newRoot)
    }

    /** Stops the server if running. Exposed for deterministic test teardown. */
    internal fun stop() = stopServer()

    private fun stopServer() {
        listenersDisposable?.let { Disposer.dispose(it) }
        listenersDisposable = null
        changeDebounce.cancelAllRequests()
        client?.progressTracker?.cancelAll()
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
        incrementalSync = false
        openFiles.clear()
        documentVersions.clear()
        pendingFullText.clear()
        pendingIncremental.clear()
    }

    override fun dispose() {
        stopServer()
        // Drop this project's diagnostics on project close. (Not on a plain
        // stop/restart: the next server publishes fresh diagnostics anyway, and
        // clearing there would race with code that seeds the cache directly.)
        NostosDiagnosticsCache.getInstance(project).cache.clear()
    }

    private fun checkMinimumVersion(): Boolean {
        val settings = NostosAppSettings.getInstance()
        val versionStr = settings.cachedVersion(settings.getEffectiveNostosPath())
            ?: return true // Can't determine version — proceed optimistically

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

        /** Quiet window before a burst of edits is flushed to the server. */
        private const val CHANGE_DEBOUNCE_MS = 150

        /**
         * End position of an incremental edit, given its [start] position and
         * the text it removed. Within a single line the column advances by the
         * removed length; across lines it moves down one line per newline and
         * the column becomes the offset within the final removed line. Pure and
         * separated out so the off-by-one-prone arithmetic can be unit-tested.
         */
        internal fun changeEndPosition(start: Position, removed: CharSequence): Position {
            val newlines = removed.count { it == '\n' }
            return if (newlines == 0) {
                Position(start.line, start.character + removed.length)
            } else {
                Position(start.line + newlines, removed.length - (removed.lastIndexOf('\n') + 1))
            }
        }

        private const val NOSTOS_INSTALL_URL = "https://heynostos.tech"

        private val VERSION_PATTERN = Regex("""\d+(?:\.\d+)+""")

        /**
         * Parse the dotted version out of `nostos --version` output. Handles
         * "0.2.17", "nostos 0.2.17", and forms with trailing build metadata
         * such as "nostos 0.2.18 (abcdef0)" by picking the first dotted-number
         * token rather than the last whitespace-separated one.
         */
        internal fun parseNostosVersion(versionOutput: String): Version? {
            val numPart = VERSION_PATTERN.find(versionOutput)?.value ?: return null
            return Version.parseVersion(numPart)
        }

        fun getInstance(project: Project): NostosLspServerManager =
            project.getService(NostosLspServerManager::class.java)
    }

    private fun resolveLspExecutable(): LspLookup {
        val settings = NostosAppSettings.getInstance()
        return NostosExecutableResolver(
            effectiveNostosPath = { settings.getEffectiveNostosPath() },
            detectNostos = { settings.cachedDetectNostos() },
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

private fun VirtualFile.toUri(): String = NostosLspUri.of(this)
