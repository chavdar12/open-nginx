package com.css.nginx.psi

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/** Which `include` directives pull in a file, so a snippet knows it is inside http, server... */
object NginxIncludes {
    fun includers(file: PsiFile): List<NginxDirective> {
        val vf = file.originalFile.virtualFile ?: return emptyList()
        val project = file.project
        if (DumbService.isDumb(project)) return emptyList()
        val map = CachedValuesManager.getManager(project).getCachedValue(project) {
            val m = HashMap<VirtualFile, MutableList<NginxDirective>>()
            for (f in NginxDeclarations.projectFiles(project)) {
                for (d in PsiTreeUtil.findChildrenOfType(f, NginxDirective::class.java)) {
                    if (d.directiveName != "include") continue
                    val arg = NginxPaths.pathArg(d) ?: continue
                    NginxPaths.resolve(f, StringUtil.unquoteString(arg.joinToString("") { it.text })).forEach { m.getOrPut(it, ::mutableListOf).add(d) }
                }
            }
            CachedValueProvider.Result.create(m, PsiModificationTracker.getInstance(project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
        }
        return map[vf].orEmpty()
    }

    /** The block every includer sits in, when they all agree; null when unknown or ambiguous. */
    fun contextOf(file: PsiFile, seen: Set<VirtualFile> = emptySet()): String? {
        val vf = file.originalFile.virtualFile ?: return null
        if (vf in seen) return null  // include cycle
        return includers(file).map { d ->
            PsiTreeUtil.getParentOfType(d, NginxDirective::class.java)?.directiveName
                ?: if (d.containingFile.name == "nginx.conf") "main" else contextOf(d.containingFile, seen + vf)
        }.distinct().singleOrNull()
    }
}
