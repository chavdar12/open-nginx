package com.css.nginx.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*

/** The file(s) or folder an include, root, ssl_certificate... path points to; globs give several. */
class NginxFileReference(d: NginxDirective, range: TextRange, private val path: String) :
    PsiPolyVariantReferenceBase<NginxDirective>(d, range, true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val psi = PsiManager.getInstance(element.project)
        return NginxPaths.resolve(element.containingFile, path)
            .mapNotNull { if (it.isDirectory) psi.findDirectory(it) else psi.findFile(it) }
            .map(::PsiElementResolveResult).toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement =
        // a renamed file only changes the last path segment
        super.handleElementRename(
            path.substringBeforeLast('/', "")
                .let { if (it.isEmpty() && !path.startsWith("/")) newElementName else "$it/$newElementName" })
}
