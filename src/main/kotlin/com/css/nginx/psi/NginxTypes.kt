package com.css.nginx.psi

import com.css.nginx.lang.NginxLanguage
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.ILeafElementType

/** Tokens that can carry another language (regex in location ~, Lua in *_by_lua_block); see NginxInjector. */
class NginxHostTokenType(name: String) : IElementType(name, NginxLanguage), ILeafElementType {
    override fun createLeafNode(leafText: CharSequence): ASTNode = NginxHostLeaf(this, leafText)
}

object NginxTypes {
    val NAME = NginxHostTokenType("NAME")                   // first word of a directive (map keys can be ~regex)
    val WORD = NginxHostTokenType("WORD")                   // unquoted argument
    val VARIABLE = IElementType("VARIABLE", NginxLanguage)  // $var or ${var}
    val STRING = NginxHostTokenType("STRING")
    val LUA = NginxHostTokenType("LUA")                     // body of content_by_lua_block { ... }
    val COMMENT = IElementType("COMMENT", NginxLanguage)
    val SEMI = IElementType(";", NginxLanguage)
    val LBRACE = IElementType("{", NginxLanguage)
    val RBRACE = IElementType("}", NginxLanguage)
    val DIRECTIVE = IElementType("DIRECTIVE", NginxLanguage)
    val BLOCK = IElementType("BLOCK", NginxLanguage)
    val FILE = IFileElementType(NginxLanguage)
}
