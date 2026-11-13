package com.css.nginx.parser

import com.css.nginx.psi.NginxTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

class NginxParser : PsiParser {
    private val heads = TokenSet.create(NginxTypes.NAME, NginxTypes.STRING, NginxTypes.VARIABLE)
    private val args = TokenSet.create(NginxTypes.WORD, NginxTypes.STRING, NginxTypes.VARIABLE)

    override fun parse(root: IElementType, b: PsiBuilder): ASTNode {
        val file = b.mark()
        while (!b.eof()) directive(b)
        file.done(root)
        return b.treeBuilt
    }

    private fun directive(b: PsiBuilder) {
        if (b.tokenType !in heads) {
            b.error("Directive expected"); b.advanceLexer(); return
        }
        val d = b.mark()
        b.advanceLexer()
        while (b.tokenType in args) b.advanceLexer()
        when (b.tokenType) {
            NginxTypes.SEMI -> b.advanceLexer()
            NginxTypes.LBRACE -> {
                val block = b.mark()
                b.advanceLexer()
                while (!b.eof() && b.tokenType != NginxTypes.RBRACE) {
                    if (b.tokenType == NginxTypes.LUA) b.advanceLexer() else directive(b)  // Lua body: one token, not directives
                }
                if (b.tokenType == NginxTypes.RBRACE) b.advanceLexer() else b.error("'}' expected")
                block.done(NginxTypes.BLOCK)
            }

            else -> b.error("';' or '{' expected")
        }
        d.done(NginxTypes.DIRECTIVE)
    }
}
