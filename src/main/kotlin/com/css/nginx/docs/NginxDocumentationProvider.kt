package com.css.nginx.docs

import com.css.nginx.data.NginxDirectives
import com.css.nginx.data.NginxHeaders
import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxTypes
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup.CONTENT_END
import com.intellij.lang.documentation.DocumentationMarkup.CONTENT_START
import com.intellij.lang.documentation.DocumentationMarkup.SECTIONS_END
import com.intellij.lang.documentation.DocumentationMarkup.SECTIONS_START
import com.intellij.lang.documentation.DocumentationMarkup.SECTION_END
import com.intellij.lang.documentation.DocumentationMarkup.SECTION_HEADER_START
import com.intellij.lang.documentation.DocumentationMarkup.SECTION_SEPARATOR
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil.escapeXmlEntities as esc
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/** Hover / quick docs for directives, their parameters, variables and HTTP header names. */
class NginxDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int): PsiElement? =
        contextElement?.takeIf {
            when (it.node?.elementType) {
                NginxTypes.NAME, NginxTypes.VARIABLE -> true
                NginxTypes.WORD -> header(it) != null || parameterDoc(it) != null
                else -> false
            }
        }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val e = element ?: return null
        return when (e.node?.elementType) {
            NginxTypes.NAME -> NginxDirectives.all[e.text]?.let {
                // one line per syntax form; syntax and doc are HTML from the generator
                val forms = it.syntax.split("<br>").joinToString("<br>") { form -> "<code><b>${esc(it.name)}</b> $form".trim() + (if (form.endsWith("}")) "" else ";") + "</code>" }
                page(
                    forms, it.doc, it.url,
                    *listOfNotNull(
                        it.default.takeIf(String::isNotEmpty)?.let { d -> "Default:" to "<code>${esc(it.name)} ${esc(d)};</code>" },
                        it.contexts.takeIf(Set<String>::isNotEmpty)?.let { c -> "Context:" to c.joinToString(", ") { x -> "<code>${esc(x)}</code>" } },
                    ).toTypedArray(),
                )
            }
            NginxTypes.VARIABLE -> {
                val name = e.text.trim('$', '{', '}')
                val v = NginxDirectives.variable(name) ?: return null
                // $http_user_agent and friends: also explain the header itself
                val header = NginxHeaders.forVariable(name)?.let { "<p><b>${esc(it.name)}</b>: ${esc(it.doc)}" } ?: ""
                page("<b>\$${esc(v.name)}</b> — variable", v.doc + header, v.url)
            }
            NginxTypes.WORD -> header(e)?.let { page("<b>${esc(it.name)}</b> — HTTP header", esc(it.doc), it.url) }
                ?: parameterDoc(e)?.let { doc ->
                    val d = e.parent as NginxDirective
                    page("<b>${esc(e.text.substringBefore('='))}</b> — parameter of <b>${esc(d.directiveName)}</b>", doc, NginxDirectives.all[d.directiveName]?.url)
                }
            else -> null
        }
    }

    private fun header(e: PsiElement) = if (NginxHeaders.isHeaderName(e)) NginxHeaders.all[e.text.lowercase()] else null

    private fun parameterDoc(e: PsiElement) = (e.parent as? NginxDirective)?.let { NginxDirectives.parameter(it.directiveName, e.text) }

    private fun page(title: String, body: String, url: String?, vararg sections: Pair<String, String>): String {
        val link = url?.takeIf { it.isNotEmpty() }?.let { "Docs:" to "<a href=\"$it\">${if ("mozilla" in it) "MDN" else "nginx.org"}</a>" }
        val all = sections.toList() + listOfNotNull(link)
        // not DEFINITION_START: its <pre> never wraps, so proxy_cache_path's syntax became one endless line
        return "<div class='definition'>" + title + "</div>" + CONTENT_START + body + CONTENT_END +
            SECTIONS_START + all.joinToString("") { (k, v) -> SECTION_HEADER_START + k + SECTION_SEPARATOR + "<p>" + v + SECTION_END } + SECTIONS_END
    }
}
