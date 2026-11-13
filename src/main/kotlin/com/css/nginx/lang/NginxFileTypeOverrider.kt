package com.css.nginx.lang

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

/**
 * `*.conf` is claimed by the bundled TextMate plugin, so the content detector never sees those files.
 * This claims a .conf file for nginx when its content looks like nginx; everything else stays as it was.
 */
class NginxFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? = NginxFileType.takeIf { isNginx(file) }

}
