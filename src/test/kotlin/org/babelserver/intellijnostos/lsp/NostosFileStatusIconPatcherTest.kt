package org.babelserver.intellijnostos.lsp

import com.intellij.icons.AllIcons
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NostosFileStatusIconPatcherTest : BasePlatformTestCase() {

    private val patcher = NostosFileStatusIconPatcher()
    private val base = AllIcons.FileTypes.Text

    override fun tearDown() {
        try {
            // The light fixture shares one project across test classes.
            // Leave no statuses behind for other tests.
            NostosFileStatusCache.getInstance(project).updateStatuses(emptyList())
        } finally {
            super.tearDown()
        }
    }

    private fun nostosFileWithStatus(status: String?): com.intellij.openapi.vfs.VirtualFile {
        val file = myFixture.configureByText("a.nos", "fn main() = 1").virtualFile
        val statuses = if (status == null) emptyList() else listOf(file.path to status)
        NostosFileStatusCache.getInstance(project).updateStatuses(statuses)
        return file
    }

    fun testDirtyFileGetsBadge() {
        val file = nostosFileWithStatus("dirty")
        val patched = patcher.patchIcon(base, file, 0, project)
        assertNotSame(base, patched)
        assertEquals(base.iconWidth, patched.iconWidth)
        assertEquals(base.iconHeight, patched.iconHeight)
    }

    fun testStaleFileGetsBadge() {
        val file = nostosFileWithStatus("stale")
        assertNotSame(base, patcher.patchIcon(base, file, 0, project))
    }

    fun testOkErrorAndUnknownStatusesKeepBaseIcon() {
        // error keeps the project-tree ✗ from NostosFileStatusDecorator instead.
        for (status in listOf("ok", "error", null)) {
            val file = nostosFileWithStatus(status)
            assertSame("status: $status", base, patcher.patchIcon(base, file, 0, project))
        }
    }

    fun testNonNostosFileKeepsBaseIcon() {
        val file = myFixture.configureByText("a.txt", "hello").virtualFile
        NostosFileStatusCache.getInstance(project).updateStatuses(listOf(file.path to "dirty"))
        assertSame(base, patcher.patchIcon(base, file, 0, project))
    }

    fun testNullProjectKeepsBaseIcon() {
        val file = nostosFileWithStatus("dirty")
        assertSame(base, patcher.patchIcon(base, file, 0, null))
    }

    fun testEqualBadgedIconsAreDeduplicatable() {
        val file = nostosFileWithStatus("dirty")
        val first = patcher.patchIcon(base, file, 0, project)
        val second = patcher.patchIcon(base, file, 0, project)
        // Platform icon caches rely on equals.
        // Two patches of the same base icon and status must compare equal.
        assertEquals(first, second)
    }
}
