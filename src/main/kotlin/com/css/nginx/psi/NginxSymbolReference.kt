package com.css.nginx.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult

/** A use of something declared by name: an upstream in proxy_pass, a $variable, a @named location. */
class NginxSymbolReference(d: NginxDirective, range: TextRange, private val key: String) :
    PsiPolyVariantReferenceBase<NginxDirective>(
        d,
        range,
        true
    ) {  // soft: built-in variables, bare hosts... resolve to nothing

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        NginxDeclarations.declarations(element.containingFile)?.get(key).orEmpty().map(::PsiElementResolveResult)
            .toTypedArray()

    override fun handleElementRename(newElementName: String): PsiElement {
        // keep the ${x} form: "${x}suffix" must not become "$ysuffix"
        val old = rangeInElement.substring(element.text)
        val name = newElementName.removePrefix("$")
        return super.handleElementRename(if (old.startsWith("\${")) "\${$name}" else if (old.startsWith("$")) "$$name" else newElementName)
    }
}
