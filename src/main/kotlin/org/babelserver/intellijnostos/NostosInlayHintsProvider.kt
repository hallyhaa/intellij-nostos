package org.babelserver.intellijnostos

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.babelserver.intellijnostos.lsp.NostosLspServerManager
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintLabelPart
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Renders inlay type hints supplied by nostos-lsp.
 *
 * The LSP gives us a position-and-label for every hint it wants drawn (inferred
 * type at a binding, parameter type at a call site, etc.). We make a single
 * `textDocument/inlayHint` request per file and place each hint as an inline
 * element at the corresponding offset.
 */
@Suppress("UnstableApiUsage")
class NostosInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey("nostos.inlay.hints")
    override val name: String = "Type hints"
    override val previewText: String =
        """
        pub answer() = 42

        main() = {
            n = answer()
            n
        }
        """.trimIndent()

    override fun createSettings(): NoSettings = NoSettings()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent = JPanel()
        }
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink,
    ): InlayHintsCollector? {
        if (file.fileType != NostosFileType) return null
        return NostosCollector(editor)
    }

    private class NostosCollector(editor: Editor) : FactoryInlayHintsCollector(editor) {

        private val log = Logger.getInstance(NostosCollector::class.java)

        /** The collector is invoked once per PSI element; we only need one LSP call per file. */
        private var processed = false

        override fun collect(
            element: PsiElement,
            editor: Editor,
            sink: InlayHintsSink,
        ): Boolean {
            if (processed) return false
            if (element !is PsiFile) return true
            processed = true

            val virtualFile = element.virtualFile ?: return false
            val server = NostosLspServerManager.getInstance(element.project).activeServer ?: return false
            val document = editor.document

            val params = InlayHintParams().apply {
                textDocument = TextDocumentIdentifier(URI("file", "", virtualFile.path, null).toString())
                range = Range(
                    Position(0, 0),
                    Position(document.lineCount.coerceAtLeast(1), 0),
                )
            }

            val hints: List<InlayHint> = try {
                server.textDocumentService.inlayHint(params)
                    .get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    ?: return false
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                log.debug("inlayHint request failed", e)
                return false
            }

            for (hint in hints) {
                val offset = lspPositionToOffset(document, hint.position.line, hint.position.character)
                if (offset < 0) continue
                val text = labelToString(hint.label) ?: continue
                if (text.isEmpty()) continue
                // Wrap small text in a rounded background so it sits on the
                // editor baseline. Bare factory.smallText renders aligned to
                // the top of the line, which makes hints look like superscript.
                val presentation = withPadding(
                    factory.roundWithBackground(factory.smallText(text)),
                    left = hint.paddingLeft == true,
                    right = hint.paddingRight == true,
                )
                sink.addInlineElement(offset, false, presentation, false)
            }
            return false
        }

        private fun withPadding(presentation: InlayPresentation, left: Boolean, right: Boolean): InlayPresentation {
            if (!left && !right) return presentation
            return factory.inset(
                presentation,
                left = if (left) PADDING_PX else 0,
                right = if (right) PADDING_PX else 0,
            )
        }

        private fun labelToString(label: Either<String, MutableList<InlayHintLabelPart>>?): String? {
            if (label == null) return null
            return if (label.isLeft) {
                label.left
            } else {
                label.right.joinToString("") { it.value ?: "" }
            }
        }

        private fun lspPositionToOffset(document: Document, line: Int, character: Int): Int {
            if (line < 0 || line >= document.lineCount) return -1
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            return (lineStart + character).coerceAtMost(lineEnd)
        }

        companion object {
            private const val REQUEST_TIMEOUT_MS = 500L
            private const val PADDING_PX = 4
        }
    }
}
