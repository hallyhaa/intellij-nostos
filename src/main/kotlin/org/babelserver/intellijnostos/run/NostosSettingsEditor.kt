package org.babelserver.intellijnostos.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.util.ui.FormBuilder
import org.babelserver.intellijnostos.settings.NostosAppSettings
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel

class NostosSettingsEditor(private val project: Project) :
    SettingsEditor<NostosRunConfiguration>() {

    private val scriptPathField = TextFieldWithBrowseButton()
    private val nostosExecutableField = TextFieldWithBrowseButton()
    private val argumentsField = RawCommandLineEditor()
    private val workingDirectoryField = TextFieldWithBrowseButton()
    private val binNameField = ComboBox<String>().apply { isEditable = true }

    private val panel: JPanel

    init {
        scriptPathField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor("nos")
        )
        val effective = NostosAppSettings.getInstance().getEffectiveNostosPath()
        nostosExecutableField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        )
        workingDirectoryField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Script file:", scriptPathField)
            .addLabeledComponent("Entry point (--bin):", binNameField)
            .addComponentToRightColumn(
                javax.swing.JLabel("<html><small>Leave empty to run the script file directly</small></html>")
            )
            .addLabeledComponent("Program arguments:", argumentsField)
            .addLabeledComponent("Working directory:", workingDirectoryField)
            .addSeparator()
            .addLabeledComponent("Nostos executable override:", nostosExecutableField)
            .addComponentToRightColumn(
                javax.swing.JLabel("<html><small>Leave empty to use global setting ($effective)</small></html>")
            )
            .panel
    }

    override fun resetEditorFrom(config: NostosRunConfiguration) {
        scriptPathField.text = config.scriptPath
        nostosExecutableField.text = config.nostosExecutable
        argumentsField.text = config.arguments
        workingDirectoryField.text = config.workingDirectory

        // Offer the [[bin]] entry points declared in the project's nostos.toml.
        val anchor = config.scriptPath.ifBlank { config.workingDirectory }
        val bins = if (anchor.isNotBlank()) NostosBinDiscovery.binsFor(File(anchor)) else emptyList()
        binNameField.removeAllItems()
        binNameField.addItem("")
        bins.forEach { binNameField.addItem(it.name) }
        binNameField.selectedItem = config.binName
    }

    override fun applyEditorTo(config: NostosRunConfiguration) {
        config.scriptPath = scriptPathField.text
        config.nostosExecutable = nostosExecutableField.text
        config.arguments = argumentsField.text
        config.workingDirectory = workingDirectoryField.text
        config.binName = (binNameField.selectedItem as? String)?.trim().orEmpty()
    }

    override fun createEditor(): JComponent = panel
}
