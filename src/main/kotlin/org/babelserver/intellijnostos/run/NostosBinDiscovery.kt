package org.babelserver.intellijnostos.run

import org.babelserver.intellijnostos.lsp.NostosProjectRoot
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Locates and reads the `[[bin]]` entry points of the Nostos project that a
 * given file belongs to.
 *
 * Parsed manifests are cached and refreshed when the nostos.toml changes, so
 * this is cheap enough to call repeatedly during highlighting.
 */
object NostosBinDiscovery {

    private data class CacheEntry(val timestamp: Long, val bins: List<NostosBin>)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /** The directory of the nearest nostos.toml at or above [start], or null. */
    fun projectRoot(start: File): File? {
        var dir: File? = if (start.isDirectory) start else start.parentFile
        while (dir != null) {
            if (File(dir, NostosProjectRoot.MANIFEST_NAME).isFile) return dir
            dir = dir.parentFile
        }
        return null
    }

    /** The `[[bin]]` entries declared for the project containing [file]. */
    fun binsFor(file: File): List<NostosBin> {
        val root = projectRoot(file) ?: return emptyList()
        val manifest = File(root, NostosProjectRoot.MANIFEST_NAME)
        if (!manifest.isFile) return emptyList()

        val stamp = manifest.lastModified()
        cache[manifest.path]?.let { if (it.timestamp == stamp) return it.bins }

        val bins = runCatching { NostosManifest.parseBins(manifest.readText()) }
            .getOrDefault(emptyList())
        cache[manifest.path] = CacheEntry(stamp, bins)
        return bins
    }

    /** The bin to run by default: the one flagged `default`, else the sole bin. */
    fun defaultBin(bins: List<NostosBin>): NostosBin? =
        bins.firstOrNull { it.isDefault } ?: bins.singleOrNull()

    /** The Nostos module name of [file], derived from its path under the project root. */
    fun moduleOf(file: File): String? {
        val root = projectRoot(file) ?: return null
        val relative = runCatching { file.relativeTo(root) }.getOrNull() ?: return null
        return relative.path.removeSuffix(".nos").replace(File.separatorChar, '.')
    }

    /** The `[[bin]]` entries whose entry point lives in [file]'s module. */
    fun binsInModule(file: File): List<NostosBin> {
        val module = moduleOf(file) ?: return emptyList()
        return binsFor(file).filter { it.entry.substringBeforeLast('.', "") == module }
    }
}
