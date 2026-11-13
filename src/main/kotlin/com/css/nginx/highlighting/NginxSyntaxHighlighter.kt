package com.css.nginx.highlighting

import com.css.nginx.lexer.NginxLexer
import com.css.nginx.psi.NginxTypes
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class NginxSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = NginxLexer()
    override fun getTokenHighlights(t: IElementType?) = pack(
        when (t) {
            NginxTypes.NAME -> NginxColors.DIRECTIVE
            NginxTypes.STRING -> NginxColors.STRING
            NginxTypes.VARIABLE -> NginxColors.VARIABLE
            NginxTypes.COMMENT -> NginxColors.COMMENT
            NginxTypes.SEMI -> NginxColors.SEMICOLON
            NginxTypes.LBRACE, NginxTypes.RBRACE -> NginxColors.BRACES
            else -> null
        }
    )
}
