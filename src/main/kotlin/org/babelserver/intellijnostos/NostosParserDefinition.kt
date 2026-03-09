package org.babelserver.intellijnostos

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.babelserver.intellijnostos.parser.NostosParser
import org.babelserver.intellijnostos.psi.NostosTypes

private val FILE = IFileElementType(NostosLanguage)
private val COMMENTS = TokenSet.create(
    NostosTypes.COMMENT,
    NostosTypes.BLOCK_COMMENT
)
private val STRINGS = TokenSet.create(
    NostosTypes.STRING,
    NostosTypes.CHAR
)

class NostosParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = NostosLexerAdapter()
    override fun createParser(project: Project?): PsiParser = NostosParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getCommentTokens(): TokenSet = COMMENTS
    override fun getStringLiteralElements(): TokenSet = STRINGS

    override fun createElement(node: ASTNode): PsiElement =
        NostosTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile =
        NostosFile(viewProvider)
}
