package org.babelserver.intellijnostos.lsp

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.babelserver.intellijnostos.NostosFileType
import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.swing.JTree

private const val CALL_HIERARCHY_TIMEOUT_MS = 2_000L
private val logger = Logger.getInstance(NostosCallHierarchyProvider::class.java)

/**
 * Wires IDEA's Call Hierarchy (Ctrl+Alt+H) to nostos-lsp's
 * `textDocument/prepareCallHierarchy` + `callHierarchy/incomingCalls` /
 * `outgoingCalls`.
 *
 * IDEA's hierarchy framework is PSI-element centric while LSP call hierarchy is
 * position based. The bridge is [psiElementForItem]: each LSP
 * [CallHierarchyItem] (a `uri` + `selectionRange`) maps to the PSI element at
 * that offset, which the framework renders and navigates to.
 */
class NostosCallHierarchyProvider : HierarchyProvider {

    override fun getTarget(dataContext: DataContext): PsiElement? {
        val file = dataContext.getData(CommonDataKeys.PSI_FILE) ?: return null
        if (file.fileType != NostosFileType) return null
        val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return null
        if (NostosLspServerManager.getInstance(file.project).activeServer == null) return null
        val offset = editor.caretModel.offset
        return file.findElementAt(offset) ?: file.findElementAt(offset - 1)
    }

    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser =
        NostosCallHierarchyBrowser(target.project, target)

    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
        (hierarchyBrowser as NostosCallHierarchyBrowser).changeView(CallHierarchyBrowserBase.getCallerType())
    }
}

private class NostosCallHierarchyBrowser(
    project: Project,
    target: PsiElement,
) : CallHierarchyBrowserBase(project, target) {

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? =
        descriptor.psiElement

    override fun isApplicableElement(element: PsiElement): Boolean =
        element.containingFile?.fileType == NostosFileType

    override fun createTrees(trees: MutableMap<in String, in JTree>) {
        trees[getCallerType()] = createTree(false)
        trees[getCalleeType()] = createTree(false)
    }

    override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement): HierarchyTreeStructure? =
        when (type) {
            getCalleeType() -> NostosCalleeTreeStructure(myProject, psiElement)
            else -> NostosCallerTreeStructure(myProject, psiElement)
        }

    override fun getComparator(): Comparator<NodeDescriptor<*>> =
        Comparator { a, b -> a.toString().compareTo(b.toString(), ignoreCase = true) }
}

/** Lets the protected [HierarchyNodeDescriptor] constructor be reached. */
private class NostosCallNodeDescriptor(
    project: Project,
    parent: HierarchyNodeDescriptor?,
    element: PsiElement,
    isBase: Boolean,
) : HierarchyNodeDescriptor(project, parent, element, isBase)

/** Incoming calls: who calls the function under the caret. */
private class NostosCallerTreeStructure(
    project: Project,
    element: PsiElement,
) : HierarchyTreeStructure(project, NostosCallNodeDescriptor(project, null, element, true)) {

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val element = descriptor.psiElement ?: return emptyArray()
        val item = prepareItem(myProject, element) ?: return emptyArray()
        val server = NostosLspServerManager.getInstance(myProject).activeServer ?: return emptyArray()
        val incoming: List<CallHierarchyIncomingCall> = try {
            server.textDocumentService
                .callHierarchyIncomingCalls(CallHierarchyIncomingCallsParams(item))
                .get(CALL_HIERARCHY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.debug("incomingCalls failed", e)
            null
        } ?: return emptyArray()

        return incoming.mapNotNull { call ->
            val psi = psiElementForItem(myProject, call.from) ?: return@mapNotNull null
            NostosCallNodeDescriptor(myProject, descriptor, psi, false)
        }.toTypedArray()
    }
}

/** Outgoing calls: which functions the function under the caret calls. */
private class NostosCalleeTreeStructure(
    project: Project,
    element: PsiElement,
) : HierarchyTreeStructure(project, NostosCallNodeDescriptor(project, null, element, true)) {

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val element = descriptor.psiElement ?: return emptyArray()
        val item = prepareItem(myProject, element) ?: return emptyArray()
        val server = NostosLspServerManager.getInstance(myProject).activeServer ?: return emptyArray()
        val outgoing: List<CallHierarchyOutgoingCall> = try {
            server.textDocumentService
                .callHierarchyOutgoingCalls(CallHierarchyOutgoingCallsParams(item))
                .get(CALL_HIERARCHY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.debug("outgoingCalls failed", e)
            null
        } ?: return emptyArray()

        return outgoing.mapNotNull { call ->
            val psi = psiElementForItem(myProject, call.to) ?: return@mapNotNull null
            NostosCallNodeDescriptor(myProject, descriptor, psi, false)
        }.toTypedArray()
    }
}

/** Runs `prepareCallHierarchy` at a PSI element's location, returning the first item. */
private fun prepareItem(project: Project, element: PsiElement): CallHierarchyItem? {
    val server = NostosLspServerManager.getInstance(project).activeServer ?: return null
    val file = element.containingFile ?: return null
    val virtualFile = file.virtualFile ?: return null
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return null
    val offset = element.textRange.startOffset
    val line = document.getLineNumber(offset)
    val character = offset - document.getLineStartOffset(line)
    val uri = NostosLspUri.of(virtualFile)
    return try {
        server.textDocumentService
            .prepareCallHierarchy(CallHierarchyPrepareParams(TextDocumentIdentifier(uri), Position(line, character)))
            .get(CALL_HIERARCHY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            ?.firstOrNull()
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        logger.debug("prepareCallHierarchy failed", e)
        null
    }
}

/** Maps an LSP call-hierarchy item back to the PSI element at its selection range. */
private fun psiElementForItem(project: Project, item: CallHierarchyItem): PsiElement? {
    val uri = item.uri ?: return null
    val path = try {
        URI(uri).path
    } catch (_: Exception) {
        null
    } ?: return null
    val vfile: VirtualFile = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
    val psiFile = PsiManager.getInstance(project).findFile(vfile) ?: return null
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null
    val range = item.selectionRange ?: item.range ?: return null
    val line = range.start.line
    if (line < 0 || line >= document.lineCount) return null
    val offset = (document.getLineStartOffset(line) + range.start.character)
        .coerceAtMost(document.getLineEndOffset(line))
    return psiFile.findElementAt(offset) ?: psiFile
}
