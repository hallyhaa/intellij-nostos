package org.babelserver.intellijnostos.lsp

import com.intellij.ide.FileIconPatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/**
 * Badges the icon of a Nostos file whose content the running live system is
 * not up to date with, everywhere the file's icon is shown (editor tabs,
 * project tree, Switcher, Search Everywhere):
 *
 * - `dirty`: edited in the editor but not committed to the live system yet
 *   (blue dot). The cure is Commit File to Live System, Ctrl+Alt+C
 * - `stale`: the file itself is fine, but something it depends on is broken
 *   or deleted, so its code in the live system is outdated (yellow dot)
 *
 * Compile errors keep their ✗ marker from [NostosFileStatusDecorator].
 * The error squiggles and project-tree marker cover that state.
 */
class NostosFileStatusIconPatcher : FileIconPatcher {

    override fun patchIcon(baseIcon: Icon, file: VirtualFile, flags: Int, project: Project?): Icon {
        if (project == null || file.extension != "nos") return baseIcon
        val status = NostosFileStatusCache.getInstance(project).statuses[file.path] ?: return baseIcon
        return when (status) {
            "dirty" -> BadgedIcon(baseIcon, DIRTY_COLOR)
            "stale" -> BadgedIcon(baseIcon, STALE_COLOR)
            else -> baseIcon
        }
    }
}

/** Blue, like VCS-modified files: pending work of your own. */
private val DIRTY_COLOR = JBColor(0x3574F0, 0x548AF7)

/** Warning yellow: something this file depends on broke elsewhere. */
private val STALE_COLOR = JBColor(0xDFA800, 0xF2C55C)

/**
 * [base] with a small filled dot in the bottom-right corner. Equality follows
 * (base, colour) so platform icon caches can deduplicate patched icons.
 */
private data class BadgedIcon(val base: Icon, val color: JBColor) : Icon {

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        base.paintIcon(c, g, x, y)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            // Scales with the icon so HiDPI/user-scaled icons keep proportions.
            val diameter = (iconWidth * 0.45).toInt().coerceAtLeast(5)
            g2.color = color
            g2.fillOval(x + iconWidth - diameter, y + iconHeight - diameter, diameter, diameter)
        } finally {
            g2.dispose()
        }
    }

    override fun getIconWidth(): Int = base.iconWidth

    override fun getIconHeight(): Int = base.iconHeight
}
