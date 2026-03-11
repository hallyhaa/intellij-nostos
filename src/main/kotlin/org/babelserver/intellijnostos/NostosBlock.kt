package org.babelserver.intellijnostos

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.tree.TokenSet
import org.babelserver.intellijnostos.psi.NostosTypes

class NostosBlock(
    node: ASTNode,
    private val spacingBuilder: SpacingBuilder,
) : AbstractBlock(node, null, null) {

    override fun buildChildren(): List<Block> {
        val blocks = mutableListOf<Block>()
        var child = myNode.firstChildNode
        while (child != null) {
            if (child.elementType != TokenType.WHITE_SPACE) {
                blocks.add(NostosBlock(child, spacingBuilder))
            }
            child = child.treeNext
        }
        return blocks
    }

    override fun getIndent(): Indent {
        val parent = myNode.treeParent ?: return Indent.getNoneIndent()
        val parentType = parent.elementType
        val type = myNode.elementType

        // Delimiters are never indented relative to their container
        if (type in DELIMITERS) return Indent.getNoneIndent()

        // Content inside brace/bracket/paren containers
        if (parentType in BRACE_CONTAINERS) return Indent.getNormalIndent()
        if (parentType in BRACKET_CONTAINERS) return Indent.getNormalIndent()
        if (parentType in PAREN_CONTAINERS) return Indent.getNormalIndent()

        // Module body: indent everything except the header (module keyword, name, end)
        if (parentType == NostosTypes.MODULE_DECL && type !in MODULE_HEADER) {
            return Indent.getNormalIndent()
        }

        // Trait/impl with end keyword: indent trait_member children
        if (parentType == NostosTypes.TRAIT_DECL && type == NostosTypes.TRAIT_MEMBER) {
            return Indent.getNormalIndent()
        }
        if (parentType == NostosTypes.COLON_IMPL_DECL && type == NostosTypes.TRAIT_MEMBER) {
            return Indent.getNormalIndent()
        }

        // List-like containers (param lists, arg lists, etc.)
        if (parentType in LIST_LIKE) return Indent.getNormalIndent()

        // Body expression after '=' in declarations
        if (parentType in DECL_WITH_BODY && isAfterSibling(NostosTypes.EQ)) {
            return Indent.getNormalIndent()
        }

        // Body expression after '->' in lambdas
        if (parentType in LAMBDA_WITH_BODY && isAfterSibling(NostosTypes.ARROW)) {
            return Indent.getNormalIndent()
        }

        // Right operand of '=>' lambda (x => body)
        if (parentType == NostosTypes.FAT_ARROW_EXPR && isAfterSibling(NostosTypes.FAT_ARROW)) {
            return Indent.getNormalIndent()
        }

        return Indent.getNoneIndent()
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val type = myNode.elementType
        if (type in BRACE_CONTAINERS || type in BRACKET_CONTAINERS
            || type in PAREN_CONTAINERS || type in LIST_LIKE
            || type == NostosTypes.MODULE_DECL
            || type == NostosTypes.TRAIT_DECL
            || type == NostosTypes.COLON_IMPL_DECL
        ) {
            return ChildAttributes(Indent.getNormalIndent(), null)
        }
        return ChildAttributes(Indent.getNoneIndent(), null)
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        return spacingBuilder.getSpacing(this, child1, child2)
    }

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    private fun isAfterSibling(tokenType: com.intellij.psi.tree.IElementType): Boolean {
        var prev = myNode.treePrev
        while (prev != null) {
            if (prev.elementType == tokenType) return true
            prev = prev.treePrev
        }
        return false
    }

    companion object {
        private val DELIMITERS = TokenSet.create(
            NostosTypes.LBRACE, NostosTypes.RBRACE,
            NostosTypes.LBRACKET, NostosTypes.RBRACKET,
            NostosTypes.LPAREN, NostosTypes.RPAREN,
            NostosTypes.HASH_LBRACE, NostosTypes.PERCENT_LBRACE,
            NostosTypes.END,
        )

        private val BRACE_CONTAINERS = TokenSet.create(
            NostosTypes.BLOCK_EXPR,
            NostosTypes.MATCH_BRACE_BODY,
            NostosTypes.TRY_BRACE_BODY,
            NostosTypes.RECEIVE_BRACE_BODY,
            NostosTypes.TRAIT_BRACE_BODY,
            NostosTypes.IMPL_BODY,
            NostosTypes.RECORD_EXPR,
            NostosTypes.RECORD_TYPE,
            NostosTypes.RECORD_TYPE_BODY,
            NostosTypes.RECORD_PATTERN,
            NostosTypes.MAP_EXPR,
            NostosTypes.SET_EXPR,
            NostosTypes.MAP_PATTERN,
            NostosTypes.SET_PATTERN,
        )

        private val BRACKET_CONTAINERS = TokenSet.create(
            NostosTypes.LIST_EXPR,
            NostosTypes.TYPE_PARAMS,
            NostosTypes.LIST_PATTERN,
        )

        private val PAREN_CONTAINERS = TokenSet.create(
            NostosTypes.PAREN_EXPR,
            NostosTypes.TUPLE_TYPE,
            NostosTypes.TUPLE_PATTERN,
            NostosTypes.DERIVE_LIST,
        )

        private val MODULE_HEADER = TokenSet.create(
            NostosTypes.MODULE_KW, NostosTypes.TYPE_NAME, NostosTypes.END,
        )

        private val LIST_LIKE = TokenSet.create(
            NostosTypes.PARAM_LIST,
            NostosTypes.BARE_PARAM_LIST,
            NostosTypes.ARG_LIST,
            NostosTypes.PATTERN_LIST,
            NostosTypes.TYPE_REF_LIST,
            NostosTypes.USE_IMPORT_LIST,
        )

        private val DECL_WITH_BODY = TokenSet.create(
            NostosTypes.FN_DECL,
            NostosTypes.BARE_FN_DECL,
            NostosTypes.VAR_DECL,
            NostosTypes.MVAR_DECL,
            NostosTypes.CONST_DECL,
            NostosTypes.TEMPLATE_DECL,
        )

        private val LAMBDA_WITH_BODY = TokenSet.create(
            NostosTypes.BACKSLASH_LAMBDA_EXPR,
            NostosTypes.FN_LAMBDA_EXPR,
        )
    }
}
