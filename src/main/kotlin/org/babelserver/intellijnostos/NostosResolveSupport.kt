package org.babelserver.intellijnostos

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * Shared support for cross-file reference resolution.
 *
 * Both [NostosReference] and [NostosTypeReference] previously re-queried the
 * file-type index and re-loaded every `.nos` PSI file on every `resolve()` /
 * `getVariants()` call — work that recurs per highlighting pass and per
 * keystroke. This caches the project's `.nos` PSI files, invalidated whenever
 * any PSI changes, so the scan happens once per edit instead of once per call.
 */
internal object NostosResolveSupport {

    /** Every `.nos` PSI file in [project], cached until the next PSI modification. */
    fun projectNosFiles(project: Project): List<PsiFile> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            val psiManager = PsiManager.getInstance(project)
            val files = FileTypeIndex
                .getFiles(NostosFileType, GlobalSearchScope.projectScope(project))
                .mapNotNull { psiManager.findFile(it) }
            CachedValueProvider.Result.create(files, PsiModificationTracker.MODIFICATION_COUNT)
        }
}
