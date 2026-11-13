package com.css.nginx.psi

import com.css.nginx.NginxConstants.JS_FUNCTION_ARG
import com.css.nginx.NginxConstants.NAMED_LOCATION_USERS
import com.css.nginx.NginxConstants.PASS_DIRECTIVES
import com.css.nginx.NginxConstants.VARIABLE_REF
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

object NginxReferences {

    fun of(d: NginxDirective): Array<PsiReference> {
        val refs = mutableListOf<PsiReference>()
        val declared = d.nameIdentifier
        val args = d.args
        fun inDirective(e: PsiElement) = e.textRange.shiftLeft(d.textRange.startOffset)
        // variables in every token of the directive itself (not its block, those belong to nested directives)
        var c: PsiElement? = d.firstChild
        while (c != null && c.node.elementType != NginxTypes.BLOCK) {
            when (c.node.elementType) {
                NginxTypes.VARIABLE -> if (c != declared) refs += NginxSymbolReference(
                    d,
                    inDirective(c),
                    NginxDeclarations.key(c.text)
                )

                NginxTypes.STRING -> VARIABLE_REF.findAll(c.text).forEach {
                    refs += NginxSymbolReference(
                        d,
                        TextRange.from(inDirective(c).startOffset + it.range.first, it.value.length),
                        NginxDeclarations.key(it.value)
                    )
                }
            }
            c = c.nextSibling
        }
        if (d.directiveName in PASS_DIRECTIVES) args.firstOrNull()?.let { arg ->
            val text = arg.joinToString("") { it.text }
            val host = NginxDeclarations.hostOf(StringUtil.unquoteString(text)) ?: return@let
            // the host comes right after scheme:// (or at the start of a bare stream address)
            val start = inDirective(arg.first()).startOffset + text.indexOf(
                host,
                text.indexOf("://").let { if (it < 0) 0 else it + 3 })
            refs += NginxSymbolReference(d, TextRange.from(start, host.length), host)
        }
        if (d.directiveName in NAMED_LOCATION_USERS) for (arg in args) {
            val w = arg.singleOrNull()?.takeIf { it.node.elementType == NginxTypes.WORD && it.text.startsWith("@") }
                ?: continue
            refs += NginxSymbolReference(d, inDirective(w), w.text)
        }
        JS_FUNCTION_ARG[d.directiveName]?.let { args.getOrNull(it)?.singleOrNull() }
            ?.takeIf { it.node.elementType == NginxTypes.WORD && '.' in it.text }?.let { w ->
                val dot = w.text.lastIndexOf('.')
                refs += NginxJsFunctionReference(
                    d,
                    TextRange.from(inDirective(w).startOffset + dot + 1, w.text.length - dot - 1),
                    w.text.substring(0, dot),
                    w.text.substring(dot + 1)
                )
            }
        NginxPaths.pathArg(d)?.let { arg ->
            val range = TextRange(inDirective(arg.first()).startOffset, inDirective(arg.last()).endOffset)
            val text = range.substring(d.text)
            val unquoted = StringUtil.unquoteString(text)
            val inner =
                if (unquoted.length < text.length) TextRange.from(range.startOffset + 1, unquoted.length) else range
            refs += NginxFileReference(d, inner, unquoted)
        }
        return refs.toTypedArray()
    }
}
