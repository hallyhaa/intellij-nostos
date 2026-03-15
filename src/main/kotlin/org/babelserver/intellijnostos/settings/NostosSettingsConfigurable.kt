package org.babelserver.intellijnostos.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class NostosSettingsConfigurable(private val project: Project) : Configurable {

    private lateinit var pathField: TextFieldWithBrowseButton
    private lateinit var versionLabel: JLabel
    private lateinit var detectedLabel: JLabel
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Nostos"

    override fun createComponent(): JComponent {
        pathField = TextFieldWithBrowseButton()
        pathField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        )
        versionLabel = JLabel("")
        detectedLabel = JLabel("")

        panel = panel {
            group("Nostos Interpreter") {
                row {
                    cell(detectedLabel)
                }
                row("Path:") {
                    cell(pathField).columns(COLUMNS_LARGE)
                        .comment("Leave empty to auto-detect from PATH")
                }
                row {
                    button("Auto-Detect") {
                        val detected = NostosAppSettings.detectNostos()
                        if (detected != null) {
                            pathField.text = detected
                            updateVersionLabel(detected)
                        } else {
                            versionLabel.text = "nostos not found"
                        }
                    }
                    cell(versionLabel)
                }
            }
        }

        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = NostosAppSettings.getInstance()
        return pathField.text != settings.state.nostosPath
    }

    override fun apply() {
        val settings = NostosAppSettings.getInstance()
        settings.state.nostosPath = pathField.text
    }

    override fun reset() {
        val settings = NostosAppSettings.getInstance()
        pathField.text = settings.state.nostosPath
        val effective = settings.getEffectiveNostosPath()
        updateDetectedLabel(effective, settings.state.nostosPath.isBlank())
        updateVersionLabel(effective)
    }

    private fun updateVersionLabel(path: String) {
        val version = NostosAppSettings.getVersion(path)
        versionLabel.text = if (version != null) "Version: $version" else ""
    }

    private fun updateDetectedLabel(path: String, autoDetected: Boolean) {
        detectedLabel.text = if (autoDetected && path != "nostos") {
            "Auto-detected: $path"
        } else {
            ""
        }
    }
}
