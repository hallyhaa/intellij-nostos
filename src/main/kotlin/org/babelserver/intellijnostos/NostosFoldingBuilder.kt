package org.babelserver.intellijnostos

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.babelserver.intellijnostos.psi.NostosTypes

class NostosFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        collectFoldRegions(root.node, document, descriptors)
        return descriptors.toTypedArray()
    }

    private fun collectFoldRegions(
        node: ASTNode,
        document: Document,
        descriptors: MutableList<FoldingDescriptor>
    ) {
        val type = node.elementType

        when {
            type in BRACE_FOLDED_TYPES -> {
                foldBraceNode(node, document, descriptors, "{...}")
            }
            type == NostosTypes.MAP_EXPR -> {
                foldBraceNode(node, document, descriptors, "%{...}")
            }
            type == NostosTypes.SET_EXPR -> {
                foldBraceNode(node, document, descriptors, "#{...}")
            }
            type == NostosTypes.LIST_EXPR || type == NostosTypes.LIST_PATTERN -> {
                foldBracketNode(node, document, descriptors, NostosTypes.LBRACKET, NostosTypes.RBRACKET, "[...]")
            }
            type == NostosTypes.MODULE_DECL -> {
                foldEndDelimited(node, document, descriptors)
            }
            type == NostosTypes.COLON_IMPL_DECL -> {
                foldEndDelimited(node, document, descriptors)
            }
            type == NostosTypes.BLOCK_COMMENT -> {
                if (isMultiLine(node, document)) {
                    descriptors.add(FoldingDescriptor(node, node.textRange))
                }
            }
            type == NostosTypes.COMMENT -> {
                foldConsecutiveLineComments(node, document, descriptors)
            }
            type == NostosTypes.STRING -> {
                if (isMultiLine(node, document)) {
                    descriptors.add(FoldingDescriptor(node, node.textRange))
                }
            }
        }

        var child = node.firstChildNode
        while (child != null) {
            collectFoldRegions(child, document, descriptors)
            child = child.treeNext
        }
    }

    private fun foldBraceNode(
        node: ASTNode,
        document: Document,
        descriptors: MutableList<FoldingDescriptor>,
        placeholder: String
    ) {
        val lbrace = findChildToken(node, NostosTypes.LBRACE)
            ?: findChildToken(node, NostosTypes.PERCENT_LBRACE)
            ?: findChildToken(node, NostosTypes.HASH_LBRACE)
        val rbrace = findChildToken(node, NostosTypes.RBRACE)
        if (lbrace != null && rbrace != null) {
            val range = TextRange(lbrace.startOffset, rbrace.startOffset + rbrace.textLength)
            if (isMultiLineRange(range, document)) {
                descriptors.add(FoldingDescriptor(node, range, null, placeholder))
            }
        }
    }

    private fun foldBracketNode(
        node: ASTNode,
        document: Document,
        descriptors: MutableList<FoldingDescriptor>,
        openType: IElementType,
        closeType: IElementType,
        placeholder: String
    ) {
        val open = findChildToken(node, openType) ?: return
        val close = findChildToken(node, closeType) ?: return
        val range = TextRange(open.startOffset, close.startOffset + close.textLength)
        if (isMultiLineRange(range, document)) {
            descriptors.add(FoldingDescriptor(node, range, null, placeholder))
        }
    }

    private fun foldEndDelimited(
        node: ASTNode,
        document: Document,
        descriptors: MutableList<FoldingDescriptor>
    ) {
        val endToken = findChildToken(node, NostosTypes.END) ?: return
        val firstLineEnd = document.getLineEndOffset(
            document.getLineNumber(node.startOffset)
        )
        val range = TextRange(firstLineEnd, endToken.startOffset + endToken.textLength)
        if (isMultiLineRange(range, document)) {
            descriptors.add(FoldingDescriptor(node, range, null, "...end"))
        }
    }

    private fun foldConsecutiveLineComments(
        node: ASTNode,
        document: Document,
        descriptors: MutableList<FoldingDescriptor>
    ) {
        // Only handle the first comment in a consecutive group
        val prev = node.treePrev
        if (prev != null && prev.elementType == com.intellij.psi.TokenType.WHITE_SPACE
            && !prev.text.contains("\n\n")) {
            val beforePrev = prev.treePrev
            if (beforePrev != null && beforePrev.elementType == NostosTypes.COMMENT) {
                return // Not the first in the group
            }
        }
        if (prev != null && prev.elementType == NostosTypes.COMMENT) {
            return // Not the first in the group
        }

        // Scan forward to find the last consecutive comment
        var last = node
        var next = node.treeNext
        while (next != null) {
            if (next.elementType == NostosTypes.COMMENT) {
                last = next
                next = next.treeNext
            } else if (next.elementType == com.intellij.psi.TokenType.WHITE_SPACE
                && !next.text.contains("\n\n")) {
                // Single newline whitespace — check if followed by another comment
                val afterWs = next.treeNext
                if (afterWs != null && afterWs.elementType == NostosTypes.COMMENT) {
                    last = afterWs
                    next = afterWs.treeNext
                } else {
                    break
                }
            } else {
                break
            }
        }

        if (last !== node) {
            val range = TextRange(node.startOffset, last.startOffset + last.textLength)
            val firstLine = node.text.trimEnd()
            val placeholder = if (firstLine.length > 40) firstLine.take(40) + "..." else firstLine
            descriptors.add(FoldingDescriptor(node, range, null, placeholder))
        }
    }

    private fun findChildToken(node: ASTNode, type: IElementType): ASTNode? {
        var child = node.firstChildNode
        while (child != null) {
            if (child.elementType == type) return child
            child = child.treeNext
        }
        return null
    }

    private fun isMultiLine(node: ASTNode, document: Document): Boolean =
        isMultiLineRange(node.textRange, document)

    private fun isMultiLineRange(range: TextRange, document: Document): Boolean =
        document.getLineNumber(range.startOffset) < document.getLineNumber(range.endOffset)

    override fun getPlaceholderText(node: ASTNode): String {
        return when (node.elementType) {
            NostosTypes.BLOCK_COMMENT -> "#* ... *#"
            NostosTypes.COMMENT -> "# ..."
            NostosTypes.STRING -> "\"...\""
            NostosTypes.MAP_EXPR, NostosTypes.MAP_PATTERN -> "%{...}"
            NostosTypes.SET_EXPR, NostosTypes.SET_PATTERN -> "#{...}"
            NostosTypes.LIST_EXPR, NostosTypes.LIST_PATTERN -> "[...]"
            NostosTypes.MODULE_DECL, NostosTypes.COLON_IMPL_DECL -> "...end"
            else -> "{...}"
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean =
        node.elementType == NostosTypes.BLOCK_COMMENT

    companion object {
        private val BRACE_FOLDED_TYPES = setOf(
            NostosTypes.BLOCK_EXPR,
            NostosTypes.MATCH_BRACE_BODY,
            NostosTypes.TRY_BRACE_BODY,
            NostosTypes.RECEIVE_BRACE_BODY,
            NostosTypes.TRAIT_BRACE_BODY,
            NostosTypes.IMPL_BODY,
            NostosTypes.RECORD_EXPR,
            NostosTypes.RECORD_TYPE_BODY,
            NostosTypes.VARIANT_FIELDS,
            NostosTypes.RECORD_PATTERN,
        )
    }
}
