package org.babelserver.intellijnostos.lsp

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.swing.Icon

/**
 * Backs IDEA's "Go to Symbol" (Ctrl+Alt+Shift+N) with nostos-lsp's
 * `workspace/symbol`. We fetch every symbol the server knows (an empty query
 * means "all" in `workspace/symbol`), let the popup match the user's pattern
 * against the names, and turn each [WorkspaceSymbol] into a navigable item.
 *
 * lsp4j hands the response over as `Either<List<SymbolInformation>,
 * List<WorkspaceSymbol>>` and picks the side by whether the elements carry the
 * legacy `deprecated` property, which nostos-lsp never sends, so in practice
 * every response, from old servers (pre-LSP 3.17 SymbolInformation JSON) and
 * new ones alike, arrives as the right side. The left type is deprecated in
 * lsp4j and would be flagged by the plugin verifier.
 *
 * The query round-trips are short, blocking calls made while the user types in
 * the `Go to Symbol` popup, the same convention as completion uses.
 */
class NostosWorkspaceSymbolContributor : ChooseByNameContributor {

    private val log = Logger.getInstance(NostosWorkspaceSymbolContributor::class.java)

    // The Go to Symbol popup calls getNames once and getItemsByName once per
    // matching name as the user types — each previously firing its own blocking
    // workspace/symbol roundtrip, with getNames re-fetching every symbol in the
    // project each keystroke. We fetch the full symbol set once and reuse it for
    // a short window so a single popup session does one roundtrip, not many.
    @Volatile
    private var cache: Cached? = null

    // This contributor is an application-level singleton shared across projects,
    // so the cache must record which project it was populated for and only be
    // reused for that same project.
    private class Cached(val project: Project, val symbols: List<WorkspaceSymbol>, val atNanos: Long)

    override fun getNames(project: Project, includeNonProjectItems: Boolean): Array<String> {
        return allSymbols(project).mapNotNull { it.name }.distinct().toTypedArray()
    }

    override fun getItemsByName(
        name: String,
        pattern: String,
        project: Project,
        includeNonProjectItems: Boolean,
    ): Array<NavigationItem> {
        // Filter the cached full set rather than issuing a second roundtrip.
        return allSymbols(project)
            .filter { it.name == name }
            .mapNotNull { symbol -> toNavigationItem(project, symbol) }
            .toTypedArray()
    }

    /** Every symbol the server knows, cached for [CACHE_TTL_NANOS] per session. */
    private fun allSymbols(project: Project): List<WorkspaceSymbol> {
        val now = System.nanoTime()
        cache?.let { if (it.project == project && now - it.atNanos < CACHE_TTL_NANOS) return it.symbols }
        val fresh = queryAllSymbols(project)
        cache = Cached(project, fresh, now)
        return fresh
    }

    private fun queryAllSymbols(project: Project): List<WorkspaceSymbol> {
        val server = NostosLspServerManager.getInstance(project).activeServer ?: return emptyList()
        val response = try {
            server.workspaceService.symbol(WorkspaceSymbolParams(""))
                .get(SYMBOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.debug("workspace/symbol request failed", e)
            null
        } ?: return emptyList()

        return when {
            response.isRight -> response.right ?: emptyList()
            else -> emptyList()
        }
    }

    private fun toNavigationItem(project: Project, symbol: WorkspaceSymbol): NavigationItem? {
        val location = symbol.resolvedLocation() ?: return null
        val vFile = resolveFile(location.uri) ?: return null
        val line = location.range?.start?.line ?: 0
        val character = location.range?.start?.character ?: 0
        return NostosSymbolNavigationItem(project, symbol.name ?: return null, vFile, line, character)
    }

    private fun resolveFile(uri: String): VirtualFile? {
        val path = try {
            URI(uri).path
        } catch (_: Exception) {
            null
        } ?: return null
        return LocalFileSystem.getInstance().findFileByPath(path)
    }

    companion object {
        private const val SYMBOL_TIMEOUT_MS = 2_000L
        private const val CACHE_TTL_NANOS = 3_000_000_000L // 3s — covers one popup session
    }
}

/**
 * The location of a [WorkspaceSymbol], as a full [Location]. LSP 3.17 lets a
 * server send just a URI (the `WorkspaceSymbolLocation` right side, resolved
 * lazily via `workspaceSymbol/resolve`). Nostos-lsp always sends the full
 * left side, but a URI-only location still navigates to the top of its file.
 */
internal fun WorkspaceSymbol.resolvedLocation(): Location? {
    val either = location ?: return null
    return when {
        either.isLeft -> either.left
        else -> either.right?.uri?.let { uri -> Location(uri, Range()) }
    }
}

private class NostosSymbolNavigationItem(
    private val project: Project,
    private val name: String,
    private val file: VirtualFile,
    private val line: Int,
    private val character: Int,
) : NavigationItem {

    override fun getName(): String = name

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = name
        override fun getLocationString(): String = file.name
        override fun getIcon(unused: Boolean): Icon? = file.fileType.icon
    }

    override fun navigate(requestFocus: Boolean) {
        OpenFileDescriptor(project, file, line, character).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = true

    override fun canNavigateToSource(): Boolean = true
}
