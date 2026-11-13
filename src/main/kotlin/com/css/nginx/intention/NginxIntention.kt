package com.css.nginx.intention

import com.css.nginx.psi.NginxDirective
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** Base for Alt+Enter actions on the directive at the caret; edits go through the document and are committed after. */
abstract class NginxIntention(private val text: String) : PsiElementBaseIntentionAction() {
    override fun getText() = text
    override fun getFamilyName() = text

    /** The directive this intention works on, or null when it is not available here. */
    protected abstract fun target(at: NginxDirective, editor: Editor): NginxDirective?
    protected abstract fun apply(d: NginxDirective, editor: Editor)

    private fun directiveAt(e: PsiElement) = PsiTreeUtil.getParentOfType(e, NginxDirective::class.java, false)

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement) =
        directiveAt(element)?.let { target(it, editor) } != null

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val d = directiveAt(element)?.let { target(it, editor) } ?: return
        apply(d, editor)
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    }
}
