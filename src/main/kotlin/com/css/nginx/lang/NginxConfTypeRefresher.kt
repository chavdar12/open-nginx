package com.css.nginx.lang

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.Alarm
import com.intellij.util.FileContentUtilCore
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.css.nginx.NginxConstants.SNIFFED

/** A new test.conf starts empty (not nginx); when typing makes it nginx (or not), re-detect so the open editor switches. */
class NginxConfTypeRefresher : DocumentListener {
    // debounce: keystrokes only queue a re-check per file; it runs once typing pauses for 300 ms
    private val queue = MergingUpdateQueue(
        "nginx file type refresh",
        300,
        true,
        null,
        ApplicationManager.getApplication(),
        null,
        Alarm.ThreadToUse.SWING_THREAD,
    )

    override fun documentChanged(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
        if (!"conf".equals(file.extension, ignoreCase = true)) return
        // nginx by name (nginx.conf, a user's *.conf mapping) never depends on content
        if (FileTypeManager.getInstance().getFileTypeByFileName(file.nameSequence) == NginxFileType) return
        queue.queue(Update.create(file) { refresh(file, event.document) })
    }

    private fun refresh(file: VirtualFile, document: Document) {
        if (!file.isValid) return
        val before = file.getUserData(SNIFFED)?.second
        val now = isNginx(file)
        // the file's PSI is what highlighting and checks use; the type lookup alone may already be up to date
        val stalePsi = ProjectManager.getInstance().openProjects.any { project ->
            PsiDocumentManager.getInstance(project).getCachedPsiFile(document)
                ?.let { (it.language == NginxLanguage) != now } == true
        }
        if (!stalePsi && (before == null || before == now)) return
        // what "Override File Type" does: drops the cached type and PSI for the file
        FileContentUtilCore.reparseFiles(file)
        // open editors keep the highlighter they were created with, so recolor them explicitly
        for (editor in EditorFactory.getInstance().getEditors(document)) {
            (editor as? EditorEx)?.highlighter =
                EditorHighlighterFactory.getInstance().createEditorHighlighter(editor.project, file)
        }
    }
}
