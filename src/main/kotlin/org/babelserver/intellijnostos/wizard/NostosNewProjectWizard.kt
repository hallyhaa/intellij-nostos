package org.babelserver.intellijnostos.wizard

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
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
            row {
                comment(
                    "Creates the Nostos project in <b>src/</b>, with a <b>tests/</b> directory " +
                        "beside it for test files that must stay out of the project."
                )
            }
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
        Files.writeString(baseDir.resolve(".gitignore"), NostosProjectScaffold.gitignoreContent())
        // Keep the otherwise-empty tests/ directory under version control.
        Files.writeString(baseDir.resolve("tests").resolve(".gitkeep"), "")

        VfsUtil.markDirtyAndRefresh(false, true, true, baseDir.toFile())

        openMainFile(project, sourceDir.resolve("main.nos"))
    }

    /** Opens the generated main.nos once the project has finished opening. */
    private fun openMainFile(project: Project, mainFile: Path) {
        StartupManager.getInstance(project).runAfterOpened {
            ApplicationManager.getApplication().invokeLater({
                val vf = LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(mainFile.toString()) ?: return@invokeLater
                FileEditorManager.getInstance(project).openFile(vf, true)
            }, project.disposed)
        }
    }
}
