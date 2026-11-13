package com.css.nginx.psi

import com.css.nginx.lang.NginxLanguage
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILeafElementType

/** Tokens that can carry another language (regex in location ~, Lua in *_by_lua_block); see NginxInjector. */
class NginxHostTokenType(name: String) : IElementType(name, NginxLanguage), ILeafElementType {
    override fun createLeafNode(leafText: CharSequence): ASTNode = NginxHostLeaf(this, leafText)
}
