package org.babelserver.intellijnostos.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GitNewProjectWizardData.Companion.gitData
import com.intellij.ide.wizard.GitNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.dsl.builder.Panel
import org.babelserver.intellijnostos.lsp.NostosProjectRoot
import org.babelserver.intellijnostos.settings.NostosAppSettings
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon

private const val NOSTOS_INSTALL_URL = "https://heynostos.tech"

/** Name of the inner directory that holds the Nostos project (nostos.toml + sources). */
private const val SOURCE_DIR_NAME = "src"

/** Adds a "Nostos" entry to IntelliJ IDEA's New Project dialog. */
class NostosNewProjectWizard : GeneratorNewProjectWizard {

    override val id: String = "Nostos"

    override val name: String = "Nostos"

    override val icon: Icon = IconLoader.getIcon("/icons/nostos.svg", javaClass)

    override fun createStep(context: WizardContext): NewProjectWizardStep =
        RootNewProjectWizardStep(context)
            .nextStep(::NewProjectWizardBaseStep)
            .nextStep(::GitNewProjectWizardStep)
            .nextStep(::NostosNewProjectWizardStep)
}

/**
 * Scaffolds a new Nostos project as a two-level layout: an inner src/
 * directory holding the Nostos project (nostos.toml and main.nos), and a
 * sibling tests/ directory for test files that must stay out of the project.
 */
class NostosNewProjectWizardStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

    override fun setupUI(builder: Panel) {
        with(builder) {
            if (NostosAppSettings.detectNostos() == null) {
                row {
                    comment(
                        "Nostos toolchain not found. " +
                            "<a href=\"$NOSTOS_INSTALL_URL\">Installation instructions</a>"
                    )
                }
            }
        }
    }

    override fun setupProject(project: Project) {
        val basePath = project.basePath ?: return
        val projectName = baseData?.name ?: project.name
        val baseDir = Path.of(basePath)
        val sourceDir = baseDir.resolve(SOURCE_DIR_NAME)

        // The Nostos project lives in src/; tests/ sits beside it so test
        // files are never compiled as part of the project.
        Files.createDirectories(sourceDir)
        Files.createDirectories(baseDir.resolve("tests"))

        Files.writeString(sourceDir.resolve("main.nos"), NostosProjectScaffold.mainNosContent())
        Files.writeString(
            sourceDir.resolve(NostosProjectRoot.MANIFEST_NAME),
            NostosProjectScaffold.nostosTomlContent(projectName),
        )
        // With a Git repository, add the repo-level files.
        if (gitData?.git == true) {
            Files.writeString(baseDir.resolve(".gitignore"), NostosProjectScaffold.gitignoreContent())
            Files.writeString(baseDir.resolve("README.md"), NostosProjectScaffold.readmeContent(projectName))
            Files.writeString(baseDir.resolve("tests").resolve(".gitkeep"), "")
        }

        VfsUtil.markDirtyAndRefresh(false, true, true, baseDir.toFile())

        createModule(project, basePath, projectName)
    }

    /**
     * Creates the project's module via a ModuleBuilder committed against the
     * model the framework provides — the path the bundled IntelliJ wizard
     * uses; GeneratorNewProjectWizard creates no module on its own. The
     * module's content root covers the project directory, with src/ as a
     * sources root, tests/ as a test sources root, and the nostos caches
     * excluded.
     */
    private fun createModule(project: Project, basePath: String, projectName: String) {
        val moduleModel = context.getUserData(NewProjectWizardStep.MODIFIABLE_MODULE_MODEL_KEY) ?: return
        val moduleBuilder = EmptyModuleType.getInstance().createModuleBuilder()
        moduleBuilder.name = projectName
        moduleBuilder.moduleFilePath = "$basePath/$projectName.iml"
        moduleBuilder.contentEntryPath = basePath
        val module = moduleBuilder.commit(project, moduleModel)?.firstOrNull() ?: return

        ModuleRootModificationUtil.updateModel(module) { model ->
            val contentEntry = model.contentEntries.firstOrNull()
                ?: model.addContentEntry(VfsUtilCore.pathToUrl(basePath))
            contentEntry.sourceFolders.toList().forEach(contentEntry::removeSourceFolder)
            contentEntry.addSourceFolder(VfsUtilCore.pathToUrl("$basePath/$SOURCE_DIR_NAME"), false)
            contentEntry.addSourceFolder(VfsUtilCore.pathToUrl("$basePath/tests"), true)
            // nostos writes its caches into the project (src/) root.
            contentEntry.addExcludeFolder(VfsUtilCore.pathToUrl("$basePath/$SOURCE_DIR_NAME/.nostos"))
            contentEntry.addExcludeFolder(VfsUtilCore.pathToUrl("$basePath/$SOURCE_DIR_NAME/.nostos-cache"))
        }
    }
}
