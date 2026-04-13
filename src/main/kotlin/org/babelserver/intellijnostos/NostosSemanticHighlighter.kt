package org.babelserver.intellijnostos

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.babelserver.intellijnostos.lsp.NostosLspServerManager
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.net.URI
import java.util.concurrent.TimeUnit

class NostosSemanticHighlighter :
    ExternalAnnotator<NostosSemanticHighlighter.Info, List<NostosSemanticHighlighter.SemanticToken>>() {

    data class Info(val fileUri: String, val document: com.intellij.openapi.editor.Document, val project: com.intellij.openapi.project.Project)
    data class SemanticToken(val startOffset: Int, val length: Int, val textAttributes: TextAttributesKey)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Info? {
        if (file !is NostosFile) return null
        val virtualFile = file.virtualFile ?: return null
        val uri = URI("file", "", virtualFile.path, null).toString()
        return Info(uri, editor.document, file.project)
    }

    override fun doAnnotate(info: Info): List<SemanticToken> {
        val server = NostosLspServerManager.getInstance(info.project).activeServer ?: return emptyList()

        val response = try {
            server.textDocumentService
                .semanticTokensFull(SemanticTokensParams(TextDocumentIdentifier(info.fileUri)))
                .get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()

        val data = response.data ?: return emptyList()

        val tokens = mutableListOf<SemanticToken>()
        val document = info.document
        var currentLine = 0
        var currentChar = 0

        var i = 0
        while (i + 4 < data.size) {
            val deltaLine = data[i]
            val deltaStartChar = data[i + 1]
            val length = data[i + 2]
            val tokenType = data[i + 3]
            val modifiers = data[i + 4]
            i += 5

            currentLine += deltaLine
            currentChar = if (deltaLine > 0) deltaStartChar else currentChar + deltaStartChar

            val textAttributes = lspTokenTypeToAttributes(tokenType, modifiers) ?: continue

            if (currentLine >= document.lineCount) continue
            val lineStart = document.getLineStartOffset(currentLine)
            val startOffset = lineStart + currentChar
            val endOffset = startOffset + length
            if (endOffset > document.textLength) continue

            tokens.add(SemanticToken(startOffset, length, textAttributes))
        }

        return tokens
    }

    override fun apply(file: PsiFile, tokens: List<SemanticToken>, holder: AnnotationHolder) {
        for (token in tokens) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(token.startOffset, token.startOffset + token.length))
                .textAttributes(token.textAttributes)
                .create()
        }
    }

    companion object {
        private const val MOD_DECLARATION = 1 // bit 0

        // LSP token type indices (matching the legend from the server)
        // 0=namespace, 1=type, 2=function, 3=variable, 4=parameter,
        // 5=property, 6=enumMember, 7=keyword, 8=string, 9=number, 10=operator, 11=comment,
        // 12=method, 13=struct, 14=enum, 15=interface, 16=typeParameter
        private fun lspTokenTypeToAttributes(tokenType: Int, modifiers: Int): TextAttributesKey? {
            val isDeclaration = modifiers and MOD_DECLARATION != 0
            return when (tokenType) {
                0 -> NostosSyntaxHighlighter.SEMANTIC_NAMESPACE
                1 -> NostosSyntaxHighlighter.SEMANTIC_TYPE
                2 -> if (isDeclaration) NostosSyntaxHighlighter.SEMANTIC_FUNCTION_DECL
                     else NostosSyntaxHighlighter.SEMANTIC_FUNCTION_CALL
                // 3=variable — skipped: LOCAL_VARIABLE is undefined in Darcula and would overwrite lexer colors
                4 -> NostosSyntaxHighlighter.SEMANTIC_PARAMETER
                5 -> NostosSyntaxHighlighter.SEMANTIC_PROPERTY
                6 -> NostosSyntaxHighlighter.SEMANTIC_ENUM_MEMBER
                // 7=keyword, 8=string, 9=number, 10=operator, 11=comment — already handled by syntax highlighter
                12 -> NostosSyntaxHighlighter.SEMANTIC_METHOD
                13 -> NostosSyntaxHighlighter.SEMANTIC_STRUCT
                14 -> NostosSyntaxHighlighter.SEMANTIC_ENUM
                15 -> NostosSyntaxHighlighter.SEMANTIC_INTERFACE
                16 -> NostosSyntaxHighlighter.SEMANTIC_TYPE_PARAMETER
                else -> null
            }
        }
    }
}
