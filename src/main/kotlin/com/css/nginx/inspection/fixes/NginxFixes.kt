package com.css.nginx.inspection.fixes

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement

/** A fix that replaces [element]'s text with [text] */
fun replaceFix(name: String, element: PsiElement, text: String) =
    NginxFix(name, element) { e, doc ->
        doc.replaceString(
            e.textRange.startOffset,
            e.textRange.endOffset,
            text
        )
    }

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

/** The spaces and tabs before [e] on its line */
fun indentOf(e: PsiElement, doc: Document): String {
    val start = e.textRange.startOffset
    return doc.charsSequence.subSequence(doc.getLineStartOffset(doc.getLineNumber(start)), start)
        .takeWhile { it == ' ' || it == '\t' }.toString()
}
