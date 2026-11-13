package com.css.nginx.lang

import com.css.nginx.data.NginxDirectives
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile

/**
 * Recognizes nginx configs by content when nothing else claims the file: test.conf, any .conf in conf.d,
 * sites-enabled/default. The platform only asks for files whose name maps to no file type.
 */
class NginxFileTypeDetector : FileTypeRegistry.FileTypeDetector {
    override fun detect(file: VirtualFile, firstBytes: ByteSequence, firstCharsIfText: CharSequence?): FileType? =
        NginxFileType.takeIf { firstCharsIfText != null && looksLikeNginx(firstCharsIfText) }

    override fun getDesiredContentPrefixLength() = 4096

}

/** Most lines end in ; { or }, and most lines that aren't a lone } start with a real nginx directive. */
fun looksLikeNginx(text: CharSequence): Boolean {
    val lines = text.lineSequence().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.take(50).toList()
    val shaped = lines.count { it.endsWith(';') || it.endsWith('{') || it == "}" }
    val statements = lines.filter { it != "}" }
    val known = statements.count { it.split(' ', '\t', ';', '{')[0] in NginxDirectives.all }
    return known * 2 > statements.size && shaped * 2 >= lines.size
}
