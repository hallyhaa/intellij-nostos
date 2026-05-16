package org.babelserver.intellijnostos.lsp

import java.io.File

/**
 * Chooses the workspace root to hand to nostos-lsp.
 *
 * nostos-lsp loads the project configuration and its `[extensions]`
 * dependencies from a `nostos.toml` located in the root it is given, so the
 * root should be the directory containing that manifest rather than the
 * IntelliJ project base path.
 */
internal object NostosProjectRoot {

    /** Name of the Nostos project manifest file. */
    const val MANIFEST_NAME = "nostos.toml"

    /**
     * Picks the directory to use as the LSP root.
     *
     * - No manifest: falls back to [basePath] so loose `.nos` files still work
     *   via the language server's file-parent fallback.
     * - A manifest sitting directly in [basePath]: uses [basePath].
     * - Manifests only in subdirectories: uses the directory of the manifest
     *   closest to the filesystem root (shallowest path), with ties broken by
     *   path so the choice is deterministic.
     *
     * nostos-lsp is single-root, so at most one directory can be returned even
     * when the project contains several manifests.
     *
     * @param manifestPaths absolute paths of every `nostos.toml` found in the project
     * @param basePath the IntelliJ project base path, or null if unknown
     * @return the directory to use as the LSP root, or null if it cannot be determined
     */
    fun choose(manifestPaths: Collection<String>, basePath: String?): String? {
        val dirs = manifestPaths.mapNotNull { File(it).parentFile?.absolutePath }
        if (dirs.isEmpty()) return basePath
        if (basePath != null && basePath in dirs) return basePath
        return dirs.minWithOrNull(
            compareBy({ it.count { c -> c == File.separatorChar } }, { it })
        )
    }
}
