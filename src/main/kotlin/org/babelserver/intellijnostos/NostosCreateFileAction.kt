package org.babelserver.intellijnostos

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

/** Adds a "Nostos File" entry to the New menu in the Project view. */
class NostosCreateFileAction : CreateFileFromTemplateAction(), DumbAware {

    override fun buildDialog(
        project: Project,
        directory: PsiDirectory,
        builder: CreateFileFromTemplateDialog.Builder,
    ) {
        builder
            .setTitle("New Nostos File")
            .addKind("Nostos file", NostosFileType.icon, "Nostos File")
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String): String =
        "Create Nostos File"
}
