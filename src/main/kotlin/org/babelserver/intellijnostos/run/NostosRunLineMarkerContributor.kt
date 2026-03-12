package org.babelserver.intellijnostos.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.babelserver.intellijnostos.NostosFile

class NostosRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        if (element !is LeafPsiElement) return null
        val file = element.containingFile as? NostosFile ?: return null

        // Show play button on the very first token of the file
        if (element.textRange.startOffset == 0) {
            return Info(
                AllIcons.RunConfigurations.TestState.Run,
                ExecutorAction.getActions(1),
            ) { "Run '${file.name}'" }
        }

        return null
    }
}
