package com.css.nginx.lang

import com.css.nginx.NginxConstants.SNIFFED
import com.css.nginx.data.NginxDirectives
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException

/** Sniffs the open editor's text when there is one (unsaved edits count), otherwise the first 4 KB on disk. */
fun isNginx(file: VirtualFile): Boolean {
    if (file.isDirectory || !file.isValid || !"conf".equals(file.extension, ignoreCase = true)) return false
    val doc = FileDocumentManager.getInstance().getCachedDocument(file)
    val stamp = doc?.modificationStamp ?: file.modificationStamp
    file.getUserData(SNIFFED)?.let { if (it.first == stamp) return it.second }
    val verdict = try {
        val text = doc?.charsSequence?.let { it.subSequence(0, minOf(it.length, 4096)) }
        // not file.charset: that asks for the file type and recurses back here; nginx configs are ASCII/UTF-8
            ?: String(file.inputStream.use { it.readNBytes(4096) }, Charsets.UTF_8)
        looksLikeNginx(text)
    } catch (_: IOException) {
        false
    }
    file.putUserData(SNIFFED, stamp to verdict)
    return verdict
}

/** Most lines end in ; { or }, and most lines that aren't a lone } start with a real nginx directive. */
fun looksLikeNginx(text: CharSequence): Boolean {
    val lines = text.lineSequence().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.take(50).toList()
    val shaped = lines.count { it.endsWith(';') || it.endsWith('{') || it == "}" }
    val statements = lines.filter { it != "}" }
    val known = statements.count { it.split(' ', '\t', ';', '{')[0] in NginxDirectives.all }
    return known * 2 > statements.size && shaped * 2 >= lines.size
}
