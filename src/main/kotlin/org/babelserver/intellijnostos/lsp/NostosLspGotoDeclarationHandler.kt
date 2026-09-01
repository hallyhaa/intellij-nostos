package org.babelserver.intellijnostos.lsp

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.babelserver.intellijnostos.NostosFileType
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Fallback for Go to Declaration (Ctrl+B): when the PSI reference at the caret
 * does not resolve, ask nostos-lsp's textDocument/definition instead. The
 * server resolves with the compiler's real name lookup, so this covers what
 * the name-based PSI search cannot: definitions in `[extensions]` dependencies
 * outside the project, and spots the PSI grammar cannot tie together.
 *
 * When the PSI reference resolves, this handler steps aside (returns null), so
 * the existing offline-capable navigation is untouched.
 */
class NostosLspGotoDeclarationHandler : GotoDeclarationHandler {

    private val log = Logger.getInstance(NostosLspGotoDeclarationHandler::class.java)

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val file = sourceElement?.containingFile ?: return null
        if (file.fileType != NostosFileType) return null
        val virtualFile = file.virtualFile ?: return null
        if (isNostosReplFile(virtualFile)) return null

        // Fallback only: with a resolving PSI reference, keep today's behavior.
        if (file.findReferenceAt(offset)?.resolve() != null) return null

        val project = file.project
        val server = NostosLspServerManager.getInstance(project).activeServer ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return null
        if (offset > document.textLength) return null
        val line = document.getLineNumber(offset)
        val position = Position(line, offset - document.getLineStartOffset(line))

        val response = try {
            server.textDocumentService
                .definition(DefinitionParams(TextDocumentIdentifier(NostosLspUri.of(virtualFile)), position))
                .get(DEFINITION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.debug("textDocument/definition request failed", e)
            null
        } ?: return null

        val targets = definitionLocations(response).mapNotNull { psiElementAtLocation(project, it) }
        return if (targets.isEmpty()) null else targets.toTypedArray()
    }

    private companion object {
        private const val DEFINITION_TIMEOUT_MS = 1_500L
    }
}

/**
 * Normalises a textDocument/definition response to plain [Location]s: the
 * left side is already locations; the right side (LocationLink, LSP 3.14+)
 * is reduced to its target, preferring the precise selection range.
 */
internal fun definitionLocations(response: Either<List<Location>, List<LocationLink>>?): List<Location> = when {
    response == null -> emptyList()
    response.isLeft -> response.left ?: emptyList()
    else -> (response.right ?: emptyList()).mapNotNull { link ->
        val uri = link.targetUri ?: return@mapNotNull null
        Location(uri, link.targetSelectionRange ?: link.targetRange ?: Range())
    }
}

/** The PSI element at an LSP [location], or null when it cannot be mapped. */
private fun psiElementAtLocation(project: Project, location: Location): PsiElement? {
    val path = try {
        URI(location.uri).path
    } catch (_: Exception) {
        null
    } ?: return null
    val vfile = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
    val psiFile = PsiManager.getInstance(project).findFile(vfile) ?: return null
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null
    val start = location.range?.start ?: return psiFile
    if (start.line < 0 || start.line >= document.lineCount) return psiFile
    val offset = (document.getLineStartOffset(start.line) + start.character)
        .coerceAtMost(document.getLineEndOffset(start.line))
    return psiFile.findElementAt(offset) ?: psiFile
}
