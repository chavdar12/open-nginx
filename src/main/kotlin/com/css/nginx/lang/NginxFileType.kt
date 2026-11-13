package com.css.nginx.lang

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType

object NginxFileType : LanguageFileType(NginxLanguage) {
    override fun getName() = "Nginx"
    override fun getDescription() = "nginx configuration"
    override fun getDefaultExtension() = "nginx"
    override fun getIcon() = AllIcons.FileTypes.Config
}
