package com.css.nginx.navigation

import com.css.nginx.lexer.NginxLexer
import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxTypes
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet

/** Find Usages on an upstream, variable or named location lists every place that uses it, across files. */
class NginxFindUsagesProvider : FindUsagesProvider {
    // strings count as code: "$var" inside a string is a real use
    override fun getWordsScanner() = DefaultWordsScanner(
        NginxLexer(), TokenSet.create(NginxTypes.WORD, NginxTypes.NAME, NginxTypes.VARIABLE, NginxTypes.STRING),
        TokenSet.create(NginxTypes.COMMENT), TokenSet.EMPTY,
    )

    override fun canFindUsagesFor(element: PsiElement) = element is NginxDirective && element.name != null
    override fun getHelpId(element: PsiElement): String? = null
    override fun getType(element: PsiElement) = when ((element as? NginxDirective)?.declaredKey?.firstOrNull()) {
        '$' -> "variable"
        '@' -> "named location"
        else -> "upstream"
    }
    override fun getDescriptiveName(element: PsiElement) = (element as NginxDirective).declaredKey.orEmpty()
    override fun getNodeText(element: PsiElement, useFullName: Boolean) = getDescriptiveName(element)
}
