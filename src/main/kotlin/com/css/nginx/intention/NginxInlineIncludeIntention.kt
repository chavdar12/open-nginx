package com.css.nginx.intention

import com.css.nginx.inspection.fixes.NginxFix
import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxFileReference
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/** Replaces `include x.conf;` by the content of x.conf (only when it names exactly one file). */
class NginxInlineIncludeIntention : NginxIntention("Inline included file") {
    override fun target(at: NginxDirective, editor: Editor) = at.takeIf { it.directiveName == "include" && included(it) != null }

    override fun apply(d: NginxDirective, editor: Editor) {
        val file = included(d) ?: return
        val indent = NginxFix.indentOf(d, editor.document)
        val lines = file.text.trimEnd().lines()
        val common = lines.filter { it.isNotBlank() }.minOfOrNull { it.length - it.trimStart().length } ?: 0
        val text = lines.mapIndexed { n, l -> if (n == 0 || l.isBlank()) l.drop(common) else indent + l.drop(common) }.joinToString("\n")
        editor.document.replaceString(d.textRange.startOffset, d.textRange.endOffset, text)
    }

    private fun included(d: NginxDirective): PsiFile? =
        d.references.filterIsInstance<NginxFileReference>().singleOrNull()?.multiResolve(false)?.singleOrNull()?.element as? PsiFile
}
