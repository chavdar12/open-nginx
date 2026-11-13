package com.css.nginx.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.css.nginx.lang.NginxLanguage

/** Rewrites part of one token in a directive, e.g. the host in `proxy_pass http://app/x;` when its upstream is renamed. */
class NginxDirectiveManipulator : AbstractElementManipulator<NginxDirective>() {
    override fun handleContentChange(element: NginxDirective, range: TextRange, newContent: String): NginxDirective {
        val leaf = element.findElementAt(range.startOffset)!!
        val inLeaf = range.shiftLeft(leaf.startOffsetInParent)
        leaf.replace(nginxToken(element, inLeaf.replace(leaf.text, newContent)))
        return element
    }
}

/** A fresh WORD or STRING token with the given text, parsed from a throwaway `x <text>;` file. */
fun nginxToken(context: PsiElement, text: String): PsiElement {
    val file = PsiFileFactory.getInstance(context.project).createFileFromText("dummy.nginx", NginxLanguage, "x $text;")
    val args = PsiTreeUtil.findChildOfType(file, NginxDirective::class.java)!!.args
    require(args.size == 1 && args[0].size == 1) { "'$text' is not a single nginx token" }
    return args[0][0]
}
