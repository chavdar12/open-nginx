package com.css.nginx.psi

import com.css.nginx.lang.NginxFileType
import com.css.nginx.lang.NginxLanguage
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class NginxFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NginxLanguage) {
    override fun getFileType() = NginxFileType
}
