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
class NginxFix(private val name: String, element: PsiElement, private val edit: (PsiElement, Document) -> Unit) : BaseIntentionAction() {
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
        DaemonCodeAnalyzer.getInstance(project).restart(file)
    }

    companion object {
        fun replace(name: String, element: PsiElement, text: String) =
            NginxFix(name, element) { e, doc -> doc.replaceString(e.textRange.startOffset, e.textRange.endOffset, text) }

        /** Deletes [e]'s text, and its whole line when nothing else is on it. */
        fun deleteLine(e: PsiElement, doc: Document) {
            val start = e.textRange.startOffset
            val end = e.textRange.endOffset
            val lineStart = doc.getLineStartOffset(doc.getLineNumber(start))
            val lineEnd = doc.getLineEndOffset(doc.getLineNumber(end))
            val chars = doc.charsSequence
            if (chars.subSequence(lineStart, start).isBlank() && chars.subSequence(end, lineEnd).isBlank()) {
                doc.deleteString(lineStart, minOf(lineEnd + 1, doc.textLength))
            } else doc.deleteString(start, end)
        }

        fun indentOf(e: PsiElement, doc: Document): String {
            val start = e.textRange.startOffset
            return doc.charsSequence.subSequence(doc.getLineStartOffset(doc.getLineNumber(start)), start).takeWhile { it == ' ' || it == '\t' }.toString()
        }
    }
}
