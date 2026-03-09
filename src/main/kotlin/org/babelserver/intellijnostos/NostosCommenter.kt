package org.babelserver.intellijnostos

import com.intellij.lang.Commenter

class NostosCommenter : Commenter {
    override fun getLineCommentPrefix() = "#"
    override fun getBlockCommentPrefix() = "#*"
    override fun getBlockCommentSuffix() = "*#"
    override fun getCommentedBlockCommentPrefix() = "# *"
    override fun getCommentedBlockCommentSuffix() = "* #"
}
