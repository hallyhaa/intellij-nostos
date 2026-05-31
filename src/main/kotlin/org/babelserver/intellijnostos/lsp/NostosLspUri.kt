package org.babelserver.intellijnostos.lsp

import com.intellij.openapi.vfs.VirtualFile
import java.net.URI

/**
 * Single source of truth for the file URIs the plugin sends to nostos-lsp.
 *
 * Every request and the workspace-folder root must use the SAME spelling, or a
 * server that compares document URIs against the root by string prefix will
 * treat open files as living outside the workspace. We use the canonical LSP
 * `file://` form (empty authority, so three slashes: `file:///home/...`), not
 * `java.io.File.toURI()` which yields `file:/home/...` (single slash).
 */
internal object NostosLspUri {

    /** The `file://` URI for a virtual file, as nostos-lsp expects it. */
    fun of(file: VirtualFile): String = of(file.path)

    /** The `file://` URI for an absolute path (use [of] with a VirtualFile when possible). */
    fun of(path: String): String = URI("file", "", path, null).toString()
}
