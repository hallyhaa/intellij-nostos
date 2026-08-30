package org.babelserver.intellijnostos.wizard

import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.HeavyPlatformTestCase

/**
 * Verifies the module that the New Project wizard commits. This must be
 * an empty-type module whose content root covers the project directory,
 * with `src/` as a sources root, `tests/` as a test sources root,
 * and the nostos caches excluded.
 */
class NostosModuleSetupTest : HeavyPlatformTestCase() {

    fun testCreatesEmptyModuleWithNostosRoots() {
        val basePath = FileUtil.createTempDirectory("nostosWizard", null, true).absolutePath

        setupNostosModule(project, basePath, "my-app")

        val module = ModuleManager.getInstance(project).findModuleByName("my-app")
        assertNotNull("module should be committed under the project name", module)
        assertEquals(EmptyModuleType.EMPTY_MODULE, ModuleType.get(module!!).id)

        val contentEntry = ModuleRootManager.getInstance(module).contentEntries
            .single { it.url == VfsUtilCore.pathToUrl(basePath) }

        val sourceRoot = contentEntry.sourceFolders.single { !it.isTestSource }
        assertEquals(VfsUtilCore.pathToUrl("$basePath/src"), sourceRoot.url)

        val testRoot = contentEntry.sourceFolders.single { it.isTestSource }
        assertEquals(VfsUtilCore.pathToUrl("$basePath/tests"), testRoot.url)

        assertEquals(
            setOf(
                VfsUtilCore.pathToUrl("$basePath/src/.nostos"),
                VfsUtilCore.pathToUrl("$basePath/src/.nostos-cache"),
            ),
            contentEntry.excludeFolderUrls.toSet(),
        )
    }
}
