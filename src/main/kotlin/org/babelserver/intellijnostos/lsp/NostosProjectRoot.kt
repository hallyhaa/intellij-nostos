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

    /**
     * Chooses which directory a freshly generated `nostos.toml` should go in,
     * for a project that has `.nos` files but no manifest yet.
     *
     * Preference order:
     *  1. A marked source root whose subtree contains `.nos` files — the user's
     *     declared "this is my code" directory. When several qualify, the one
     *     holding the most `.nos` files wins.
     *  2. Otherwise the directory directly holding the most `.nos` files, so
     *     loose sources get a manifest beside them rather than one dumped at the
     *     project root.
     *  3. Otherwise [basePath].
     *
     * Ties are broken by shallowest path, then alphabetically, so the choice is
     * deterministic.
     *
     * @param sourceRoots absolute paths of the project's marked source roots
     * @param nosFiles absolute paths of every `.nos` file in the project
     * @param basePath the IntelliJ project base path, or null if unknown
     * @return the directory to create the manifest in, or null when it cannot
     *   be determined (no `.nos` files and no base path)
     */
    fun chooseManifestTarget(
        sourceRoots: Collection<String>,
        nosFiles: Collection<String>,
        basePath: String?,
    ): String? {
        if (nosFiles.isEmpty()) return basePath

        // Priority 1: a source root whose subtree contains .nos files.
        val sourceRootHits = sourceRoots
            .map { root -> root to nosFiles.count { it.isUnder(root) } }
            .filter { it.second > 0 }
        if (sourceRootHits.isNotEmpty()) {
            return sourceRootHits.minWithOrNull(byCountThenDepthThenPath)?.first
        }

        // Priority 2: the directory directly holding the most .nos files.
        val byParent = nosFiles
            .mapNotNull { File(it).parent }
            .groupingBy { it }
            .eachCount()
            .toList()
        if (byParent.isNotEmpty()) {
            return byParent.minWithOrNull(byCountThenDepthThenPath)?.first
        }

        return basePath
    }

    /**
     * Orders (directory, count) candidates so the *first* by this comparator is
     * the best pick: most `.nos` files, then shallowest path, then alphabetical.
     */
    private val byCountThenDepthThenPath: Comparator<Pair<String, Int>> =
        compareBy(
            { -it.second },
            { it.first.count { c -> c == File.separatorChar } },
            { it.first },
        )

    /** True when [this] path lies at or under directory [dir]. */
    private fun String.isUnder(dir: String): Boolean {
        val d = dir.trimEnd(File.separatorChar)
        return this == d || startsWith("$d${File.separatorChar}")
    }

    /**
     * Scans [baseDir] and its subdirectories for nostos.toml files.
     *
     * The scan is bounded to [maxDepth] levels and skips dot-directories, so
     * it stays cheap and never descends into .git, .idea or build caches. It
     * works straight off the filesystem, independent of the IDE's indices —
     * which can be stale right after a project is created.
     */
    fun findManifests(baseDir: File, maxDepth: Int = 3): List<String> {
        val found = mutableListOf<String>()

        fun scan(dir: File, depth: Int) {
            val entries = dir.listFiles() ?: return
            for (entry in entries) {
                when {
                    entry.isFile && entry.name == MANIFEST_NAME ->
                        found.add(entry.absolutePath)
                    entry.isDirectory && depth < maxDepth && !entry.name.startsWith(".") ->
                        scan(entry, depth + 1)
                }
            }
        }

        scan(baseDir, 0)
        return found
    }
}
