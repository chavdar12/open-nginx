package com.css.nginx.intention

import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxPaths
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import java.io.IOException

/**
 * Moves the selected directives (or the one at the caret) into a new `<first directive>.conf` next to this file and includes it
 * instead. Rename the new file with Shift+F6 afterwards; the include follows.
 */
class NginxExtractIncludeIntention : NginxIntention("Extract to include file") {
    override fun target(at: NginxDirective, editor: Editor): NginxDirective? =
        at.takeIf { it.containingFile.virtualFile?.parent != null }

    // creates a file: nothing meaningful to show in a preview
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile) = IntentionPreviewInfo.EMPTY

    override fun apply(at: NginxDirective, editor: Editor) {
        val doc = editor.document
        val sel = editor.selectionModel
        // the caret sits at one end of a selection, often after it; the selection's first directive decides the block
        val first = if (!sel.hasSelection()) null else {
            val offset =
                (sel.selectionStart until sel.selectionEnd).firstOrNull { !doc.charsSequence[it].isWhitespace() }
            offset?.let {
                PsiTreeUtil.getParentOfType(
                    at.containingFile.findElementAt(it),
                    NginxDirective::class.java,
                    false
                )
            }
        }
        val d = first ?: at
        // with a selection: every directive of d's block that the selection touches
        val siblings = PsiTreeUtil.getChildrenOfTypeAsList(d.parent, NginxDirective::class.java)
        val picked =
            if (sel.hasSelection()) siblings.filter { it.textRange.intersects(sel.selectionStart, sel.selectionEnd) }
                .ifEmpty { listOf(d) } else listOf(d)
        val start = picked.first().textRange.startOffset
        val end = picked.last().textRange.endOffset
        val dir = d.containingFile.virtualFile.parent
        val stem = picked.first().directiveName
        val name = generateSequence(1) { it + 1 }.map { if (it == 1) "$stem.conf" else "$stem-$it.conf" }
            .first { dir.findChild(it) == null }
        val lines = doc.charsSequence.subSequence(start, end).toString().lines()
        val common = lines.drop(1).filter { it.isNotBlank() }.minOfOrNull { it.length - it.trimStart().length } ?: 0
        val file = try {
            dir.createChildData(this, name).also {
                VfsUtil.saveText(
                    it,
                    (listOf(lines[0]) + lines.drop(1).map { l -> l.drop(common) }).joinToString("\n") + "\n"
                )
            }
        } catch (e: IOException) {  // read-only folder, disk full: leave the config as it was
            HintManager.getInstance().showErrorHint(editor, "Cannot create $name: ${e.message}")
            return
        }
        doc.replaceString(start, end, "include ${VfsUtilCore.getRelativePath(file, NginxPaths.baseDir(dir)) ?: name};")
    }
}
