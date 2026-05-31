package org.babelserver.intellijnostos.lsp

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.babelserver.intellijnostos.wizard.NostosProjectScaffold
import java.io.File

/**
 * Shown when a project contains `.nos` files but no `nostos.toml` manifest.
 *
 * Without a manifest the language server still runs (it falls back to the
 * project base path as its root), but it cannot load the project's
 * `[extensions]` dependencies, so analysis is shallower than the user expects.
 * Rather than silently writing a file the user never asked for, we offer to
 * generate one and only do so when the action is clicked.
 */
internal object NostosMissingManifestNotifier {

    private val log = Logger.getInstance(NostosMissingManifestNotifier::class.java)

    /**
     * Notifies that no manifest was found and offers to create one at
     * [targetDir]. [targetDir] is the directory the language server is using as
     * its workspace root (the project base path when no manifest exists), so a
     * manifest written there is picked up on the next server start.
     */
    fun notify(project: Project, targetDir: String) {
        val manifest = File(targetDir, NostosProjectRoot.MANIFEST_NAME)
        // Guard against a race: another process may have written one between the
        // startup scan and this notification firing.
        if (manifest.exists()) return

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Nostos")
            .createNotification(
                "No nostos.toml found",
                "This project has Nostos files but no <code>nostos.toml</code>. " +
                    "The language server is running without project configuration, " +
                    "so dependency-aware analysis is unavailable.",
                NotificationType.WARNING,
            )
            .addAction(NotificationAction.create("Generate nostos.toml") { _, notification ->
                generateManifest(project, manifest)
                // Dismiss the balloon and remove it from the event log: the
                // manifest now exists, so the prompt no longer applies.
                notification.expire()
            })
            .notify(project)
    }

    /**
     * Writes a minimal manifest named [projectName] to [manifest], unless one
     * is already there. Returns true when a file was written, false when the
     * manifest already existed and was left untouched.
     *
     * Pure filesystem logic with no IDE dependencies, so it can be unit-tested
     * directly; the IDE wiring (VFS refresh, server restart) lives in the
     * caller.
     */
    fun createManifestIfAbsent(manifest: File, projectName: String): Boolean {
        if (manifest.exists()) return false
        manifest.writeText(NostosProjectScaffold.nostosTomlContent(projectName))
        return true
    }

    /**
     * Picks the name to write into the generated manifest. Prefers the name of
     * the enclosing VCS checkout (the directory holding `.git`), which for a
     * single-project repo is the project's real name — `project.name` is just
     * the folder IDEA happened to be opened on, which may be a subdirectory.
     * Falls back to [fallback] when [startDir] is not inside a Git checkout.
     *
     * Pure filesystem logic, so it can be unit-tested without the IDE.
     */
    fun deriveProjectName(startDir: File, fallback: String): String =
        findGitRoot(startDir)?.name ?: fallback

    /** Nearest ancestor of [start] (inclusive) that contains a `.git` entry. */
    private fun findGitRoot(start: File): File? {
        var dir: File? = start
        while (dir != null) {
            // `.git` is a directory in a normal clone, a file in worktrees and
            // submodules — exists() covers both.
            if (File(dir, ".git").exists()) return dir
            dir = dir.parentFile
        }
        return null
    }

    private fun generateManifest(project: Project, manifest: File) {
        try {
            val name = deriveProjectName(manifest.parentFile ?: File("."), project.name)
            if (!createManifestIfAbsent(manifest, name)) return
            // Make the IDE aware of the new file straight away.
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(manifest)

            // Restart off the EDT: the start path blocks on the LSP init
            // handshake, and restart() is documented as off-EDT only. Re-root
            // the server at the manifest's directory so it actually picks up the
            // file we just wrote — an in-place restart would keep the old root.
            ApplicationManager.getApplication().executeOnPooledThread {
                NostosLspServerManager.getInstance(project).restart(manifest.parent)
            }
        } catch (e: Exception) {
            log.warn("Failed to generate nostos.toml at ${manifest.path}", e)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Nostos")
                .createNotification(
                    "Could not create nostos.toml",
                    "Writing the manifest to ${manifest.path} failed: ${e.message}",
                    NotificationType.ERROR,
                )
                .notify(project)
        }
    }
}
