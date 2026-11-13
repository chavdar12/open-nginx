package com.css.nginx.highlighting

import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory

class NginxSyntaxHighlighterFactory : SingleLazyInstanceSyntaxHighlighterFactory() {
    override fun createHighlighter() = NginxSyntaxHighlighter()
}
