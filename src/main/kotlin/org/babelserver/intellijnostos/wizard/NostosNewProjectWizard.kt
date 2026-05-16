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
import com.intellij.ui.dsl.builder.bindSelected
import org.babelserver.intellijnostos.lsp.NostosProjectRoot
import org.babelserver.intellijnostos.settings.NostosAppSettings
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon

private const val NOSTOS_INSTALL_URL = "https://heynostos.tech"

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
 * Settings and scaffolding for a new Nostos project: an optional nostos.toml
 * manifest plus a main.nos entry point.
 */
class NostosNewProjectWizardStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

    private val createManifestProperty = propertyGraph.property(true)

    override fun setupUI(builder: Panel) {
        with(builder) {
            row {
                checkBox("Create nostos.toml manifest")
                    .bindSelected(createManifestProperty)
                    .comment("Recommended. Lets nostos-lsp resolve the project and its dependencies.")
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

        Files.createDirectories(baseDir)
        Files.writeString(baseDir.resolve("main.nos"), NostosProjectScaffold.mainNosContent())
        Files.writeString(baseDir.resolve(".gitignore"), NostosProjectScaffold.gitignoreContent())
        if (createManifestProperty.get()) {
            Files.writeString(
                baseDir.resolve(NostosProjectRoot.MANIFEST_NAME),
                NostosProjectScaffold.nostosTomlContent(projectName),
            )
        }
        VfsUtil.markDirtyAndRefresh(false, true, true, baseDir.toFile())

        openMainFile(project, baseDir.resolve("main.nos"))
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
