package com.css.nginx.lang

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
