// lsp4j deprecated SymbolInformation in favour of WorkspaceSymbol, but
// nostos-lsp's workspace/symbol handler still returns Vec<SymbolInformation>
// (the Either.left shape), so the client must read it. Suppress here until the
// server migrates to the WorkspaceSymbol response.
@file:Suppress("DEPRECATION")

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
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.WorkspaceSymbolParams
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.swing.Icon

/**
 * Backs IDEA's "Go to Symbol" (Ctrl+Alt+Shift+N) with nostos-lsp's
 * `workspace/symbol`. The server does a case-insensitive substring match over
 * functions/types across the project, so we forward the user's pattern straight
 * to it and turn each returned [SymbolInformation] into a navigable item.
 *
 * The query roundtrips are short, blocking calls made while the user types in
 * the Go to Symbol popup — the same convention completion uses.
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
    private class Cached(val project: Project, val symbols: List<SymbolInformation>, val atNanos: Long)

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
            .mapNotNull { info -> toNavigationItem(project, info) }
            .toTypedArray()
    }

    /** Every symbol the server knows, cached for [CACHE_TTL_NANOS] per session. */
    private fun allSymbols(project: Project): List<SymbolInformation> {
        val now = System.nanoTime()
        cache?.let { if (it.project == project && now - it.atNanos < CACHE_TTL_NANOS) return it.symbols }
        val fresh = query(project, "")
        cache = Cached(project, fresh, now)
        return fresh
    }

    private fun query(project: Project, queryString: String): List<SymbolInformation> {
        val server = NostosLspServerManager.getInstance(project).activeServer ?: return emptyList()
        val response = try {
            server.workspaceService.symbol(WorkspaceSymbolParams(queryString))
                .get(SYMBOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.debug("workspace/symbol request failed", e)
            null
        } ?: return emptyList()

        // nostos-lsp returns the legacy SymbolInformation list (Either.left).
        return when {
            response.isLeft -> response.left ?: emptyList()
            else -> emptyList()
        }
    }

    private fun toNavigationItem(project: Project, info: SymbolInformation): NavigationItem? {
        val location: Location = info.location ?: return null
        val vfile = resolveFile(location.uri) ?: return null
        val line = location.range?.start?.line ?: 0
        val character = location.range?.start?.character ?: 0
        return NostosSymbolNavigationItem(project, info.name ?: return null, vfile, line, character)
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
