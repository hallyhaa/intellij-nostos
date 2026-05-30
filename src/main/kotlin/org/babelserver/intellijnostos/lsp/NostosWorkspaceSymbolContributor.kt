package org.babelserver.intellijnostos.lsp

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
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

    override fun getNames(project: Project, includeNonProjectItems: Boolean): Array<String> {
        // An empty query asks the server for everything it knows; the framework
        // filters these names against the user's pattern.
        return query(project, "").mapNotNull { it.name }.distinct().toTypedArray()
    }

    override fun getItemsByName(
        name: String,
        pattern: String,
        project: Project,
        includeNonProjectItems: Boolean,
    ): Array<NavigationItem> {
        return query(project, name)
            .filter { it.name == name }
            .mapNotNull { info -> toNavigationItem(project, info) }
            .toTypedArray()
    }

    private fun query(project: Project, queryString: String): List<SymbolInformation> {
        val server = NostosLspServerManager.getInstance(project).activeServer ?: return emptyList()
        val response = try {
            server.workspaceService.symbol(WorkspaceSymbolParams(queryString))
                .get(SYMBOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
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
