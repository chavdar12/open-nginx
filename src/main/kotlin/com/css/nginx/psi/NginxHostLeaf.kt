package com.css.nginx.psi

import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

/** A word, string or Lua body that another language (RegExp, Lua) can be injected into; edits in the injected editor come back via updateText. */
class NginxHostLeaf(type: IElementType, text: CharSequence) : LeafPsiElement(type, text), PsiLanguageInjectionHost {
    override fun isValidHost() = true
    override fun updateText(text: String): PsiLanguageInjectionHost = replaceWithText(text) as PsiLanguageInjectionHost
    override fun createLiteralTextEscaper(): LiteralTextEscaper<NginxHostLeaf> = LiteralTextEscaper.createSimple(this)
}
