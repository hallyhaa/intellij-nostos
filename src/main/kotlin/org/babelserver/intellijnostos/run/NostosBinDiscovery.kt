package org.babelserver.intellijnostos.run

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.babelserver.intellijnostos.lsp.NostosProjectRoot
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Locates and reads the `[[bin]]` entry points of the Nostos project that a
 * given file belongs to.
 *
 * Parsed manifests are cached and refreshed when the nostos.toml changes, so
 * this is cheap enough to call repeatedly during highlighting.
 *
 * Two flavours of the lookup exist: the [File] overloads are used by run
 * configuration code (and are pure enough to unit-test without the platform),
 * while the [VirtualFile] overloads are for callers that run inside a read
 * action or highlighting pass — they walk and read through the VFS, which is
 * served from the in-memory snapshot and avoids blocking disk traversal there.
 */
object NostosBinDiscovery {

    private data class CacheEntry(val timestamp: Long, val bins: List<NostosBin>)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    // Kept separate from [cache]: File.lastModified() and
    // VirtualFile.modificationStamp live in different number spaces, so sharing
    // one map keyed by the same path would thrash the cache when both the run
    // and highlighting paths touch the same manifest.
    private val vfsCache = ConcurrentHashMap<String, CacheEntry>()

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

    /** VFS variant of [projectRoot]: the directory of the nearest nostos.toml. */
    fun projectRoot(file: VirtualFile): VirtualFile? = findManifest(file)?.parent

    /** VFS variant of [binsInModule], safe to call from a highlighting pass. */
    fun binsInModule(file: VirtualFile): List<NostosBin> {
        val manifest = findManifest(file) ?: return emptyList()
        val root = manifest.parent ?: return emptyList()
        val module = moduleOf(root, file) ?: return emptyList()
        return binsFor(manifest).filter { it.entry.substringBeforeLast('.', "") == module }
    }

    private fun findManifest(file: VirtualFile): VirtualFile? {
        var dir: VirtualFile? = if (file.isDirectory) file else file.parent
        while (dir != null) {
            dir.findChild(NostosProjectRoot.MANIFEST_NAME)?.let { if (!it.isDirectory) return it }
            dir = dir.parent
        }
        return null
    }

    private fun binsFor(manifest: VirtualFile): List<NostosBin> {
        val stamp = manifest.modificationStamp
        vfsCache[manifest.path]?.let { if (it.timestamp == stamp) return it.bins }

        val bins = runCatching { NostosManifest.parseBins(VfsUtilCore.loadText(manifest)) }
            .getOrDefault(emptyList())
        vfsCache[manifest.path] = CacheEntry(stamp, bins)
        return bins
    }

    private fun moduleOf(root: VirtualFile, file: VirtualFile): String? {
        val relative = VfsUtilCore.getRelativePath(file, root) ?: return null
        return relative.removeSuffix(".nos").replace('/', '.')
    }
}
