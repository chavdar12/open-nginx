package com.css.nginx.psi

import com.css.nginx.lang.NginxLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException

/** A fresh WORD or STRING token with the given text, parsed from a throwaway `x <text>;` file. */
fun nginxToken(context: PsiElement, text: String): PsiElement {
    val file = PsiFileFactory.getInstance(context.project).createFileFromText("dummy.nginx", NginxLanguage, "x $text;")
    // IncorrectOperationException: rename shows it as a message instead of an IDE error
    val args = PsiTreeUtil.findChildOfType(file, NginxDirective::class.java)?.args
    return args?.singleOrNull()?.singleOrNull()
        ?: throw IncorrectOperationException("'$text' is not a single nginx word")
}
