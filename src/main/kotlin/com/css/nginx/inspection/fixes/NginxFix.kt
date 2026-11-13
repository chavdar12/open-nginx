package com.css.nginx.inspection.fixes

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager

/** An Alt+Enter fix that edits the document around [element]; the element is re-found at invoke time, so stale fixes do nothing. */
class NginxFix(private val name: String, element: PsiElement, private val edit: (PsiElement, Document) -> Unit) :
    BaseIntentionAction() {
    private val pointer = SmartPointerManager.createPointer(element)

    override fun getText() = name
    override fun getFamilyName() = "Nginx"
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = pointer.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val e = pointer.element ?: return
        val docs = PsiDocumentManager.getInstance(project)
        val doc = docs.getDocument(file) ?: return
        docs.doPostponedOperationsAndUnblockDocument(doc)
        edit(e, doc)
        docs.commitDocument(doc)
        DaemonCodeAnalyzer.getInstance(project).restart(file, "nginx quick fix: $name")
    }
}
