package com.css.nginx.navigation

import com.css.nginx.psi.NginxDirective
import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement

/** Shift+F6 renames upstreams, variables and named locations in place; Safe Delete (Alt+Delete) warns while they are used. */
class NginxRefactoringSupport : RefactoringSupportProvider() {
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?) = isNamed(element)
    override fun isSafeDeleteAvailable(element: PsiElement) = isNamed(element)
    private fun isNamed(element: PsiElement) = element is NginxDirective && element.name != null
}
