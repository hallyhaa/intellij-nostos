package org.babelserver.intellijnostos.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.*
import javax.swing.JLabel

class NostosSettingsConfigurable(private val project: Project) :
    BoundSearchableConfigurable("Nostos", "nostos.settings") {

    private lateinit var pathField: Cell<TextFieldWithBrowseButton>
    private lateinit var versionLabel: Cell<JLabel>
    private lateinit var detectedLabel: Cell<JLabel>

    override fun createPanel(): DialogPanel {
        val settings = NostosAppSettings.getInstance()

        return panel {
            group("Nostos Interpreter") {
                row {
                    detectedLabel = label("")
                }
                row("Path:") {
                    pathField = textFieldWithBrowseButton(
                        fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                    ).columns(COLUMNS_LARGE)
                        .comment("Leave empty to auto-detect from PATH")
                }
                row {
                    button("Auto-Detect") {
                        val detected = NostosAppSettings.detectNostos()
                        if (detected != null) {
                            pathField.component.text = detected
                            updateVersionLabel(detected)
                        } else {
                            versionLabel.component.text = "nostos not found"
                        }
                    }
                    versionLabel = label("")
                }
            }

            onApply {
                settings.state.nostosPath = pathField.component.text
            }
            onReset {
                pathField.component.text = settings.state.nostosPath
                val effective = settings.getEffectiveNostosPath()
                updateDetectedLabel(effective, settings.state.nostosPath.isBlank())
                updateVersionLabel(effective)
            }
        }
    }

    private fun updateVersionLabel(path: String) {
        val version = NostosAppSettings.getVersion(path)
        versionLabel.component.text = if (version != null) "Version: $version" else ""
    }

    private fun updateDetectedLabel(path: String, autoDetected: Boolean) {
        detectedLabel.component.text = if (autoDetected && path != "nostos") {
            "Auto-detected: $path"
        } else {
            ""
        }
    }
}
