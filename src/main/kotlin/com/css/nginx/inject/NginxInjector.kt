package com.css.nginx.inject

import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxHostLeaf
import com.css.nginx.psi.NginxTypes
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * Lua in *_by_lua_block bodies and *_by_lua strings, when a Lua plugin (EmmyLua, Luanalysis...) is installed.
 * Regexes are not injected: the platform stops formatting after an injected fragment whose leading whitespace changes
 * (column alignment); NginxRegexAnnotator colors and checks them instead.
 */
class NginxInjector : MultiHostInjector {
    override fun elementsToInjectIn() = listOf(NginxHostLeaf::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val leaf = context as? NginxHostLeaf ?: return
        if (leaf.elementType == NginxTypes.LUA) return inject(registrar, leaf, TextRange(0, leaf.textLength))
        val d = leaf.parent as? NginxDirective ?: return
        if (leaf.elementType == NginxTypes.STRING && d.directiveName.endsWith("_by_lua") && leaf.textLength >= 2) {
            return inject(registrar, leaf, TextRange(1, leaf.textLength - 1))
        }
    }

    private fun inject(registrar: MultiHostRegistrar, host: NginxHostLeaf, range: TextRange) {
        // via the .lua file type: Lua plugins create their Language lazily, so findLanguageByID("Lua") is null until a .lua file is used
        val language =
            (FileTypeManager.getInstance().getFileTypeByExtension("lua") as? LanguageFileType)?.language ?: return
        registrar.startInjecting(language).addPlace(null, null, host as PsiLanguageInjectionHost, range).doneInjecting()
    }
}
