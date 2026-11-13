package com.css.nginx.psi

import com.css.nginx.lang.NginxLanguage
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException

/** Rewrites part of one token in a directive, e.g. the host in `proxy_pass http://app/x;` when its upstream is renamed. */
class NginxDirectiveManipulator : AbstractElementManipulator<NginxDirective>() {
    override fun handleContentChange(element: NginxDirective, range: TextRange, newContent: String): NginxDirective {
        val leaf = element.findElementAt(range.startOffset) ?: throw IncorrectOperationException("Nothing to rename at $range")
        val inLeaf = range.shiftLeft(leaf.startOffsetInParent)
        leaf.replace(nginxToken(element, inLeaf.replace(leaf.text, newContent)))
        return element
    }
}

/** A fresh WORD or STRING token with the given text, parsed from a throwaway `x <text>;` file. */
fun nginxToken(context: PsiElement, text: String): PsiElement {
    val file = PsiFileFactory.getInstance(context.project).createFileFromText("dummy.nginx", NginxLanguage, "x $text;")
    // IncorrectOperationException: rename shows it as a message instead of an IDE error
    val args = PsiTreeUtil.findChildOfType(file, NginxDirective::class.java)?.args
    return args?.singleOrNull()?.singleOrNull() ?: throw IncorrectOperationException("'$text' is not a single nginx word")
}
