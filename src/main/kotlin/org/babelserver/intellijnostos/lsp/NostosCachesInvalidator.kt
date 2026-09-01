package org.babelserver.intellijnostos.lsp

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * Adds an optional "Clear Nostos module caches" checkbox to
 * File | Invalidate Caches. It runs as the IDE is about to restart, when the
 * language servers are going down anyway, so it deletes the cache directories
 * on disk directly instead of asking a server (nostos.clearCache).
 */
internal class NostosCachesInvalidator : CachesInvalidator() {

    override fun getDescription(): String = "Clear Nostos module caches"

    override fun getComment(): String =
        "Deletes .nostos-cache and .nostos in open Nostos projects; the next engine start recompiles everything"

    // Optional and off by default: IDE cache problems are rarely Nostos cache
    // problems, and clearing costs a full recompile on next startup.
    override fun optionalCheckboxDefaultValue(): Boolean = false

    override fun invalidateCaches() {
        for (project in ProjectManager.getInstance().openProjects) {
            val root = NostosLspServerManager.getInstance(project).cacheRoot ?: continue
            for (name in CACHE_DIRECTORY_NAMES) {
                val dir = File(root, name)
                if (dir.isDirectory) FileUtil.delete(dir)
            }
        }
    }
}

private val CACHE_DIRECTORY_NAMES = listOf(".nostos-cache", ".nostos")
