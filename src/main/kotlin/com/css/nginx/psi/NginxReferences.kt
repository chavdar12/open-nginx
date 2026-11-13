package com.css.nginx.psi

import com.css.nginx.lexer.VARIABLE_REF
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/** A use of something declared by name: an upstream in proxy_pass, a $variable, a @named location. */
class NginxSymbolReference(d: NginxDirective, range: TextRange, private val key: String) :
    PsiPolyVariantReferenceBase<NginxDirective>(
        d,
        range,
        true
    ) {  // soft: built-in variables, bare hosts... resolve to nothing

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        NginxDeclarations.declarations(element.containingFile)?.get(key).orEmpty().map(::PsiElementResolveResult)
            .toTypedArray()

    override fun handleElementRename(newElementName: String): PsiElement {
        // keep the ${x} form: "${x}suffix" must not become "$ysuffix"
        val old = rangeInElement.substring(element.text)
        val name = newElementName.removePrefix("$")
        return super.handleElementRename(if (old.startsWith("\${")) "\${$name}" else if (old.startsWith("$")) "$$name" else newElementName)
    }
}

/** The file(s) or folder an include, root, ssl_certificate... path points to; globs give several. */
class NginxFileReference(d: NginxDirective, range: TextRange, private val path: String) :
    PsiPolyVariantReferenceBase<NginxDirective>(d, range, true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val psi = PsiManager.getInstance(element.project)
        return NginxPaths.resolve(element.containingFile, path)
            .mapNotNull { if (it.isDirectory) psi.findDirectory(it) else psi.findFile(it) }
            .map(::PsiElementResolveResult).toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement =
        // a renamed file only changes the last path segment
        super.handleElementRename(
            path.substringBeforeLast('/', "")
                .let { if (it.isEmpty() && !path.startsWith("/")) newElementName else "$it/$newElementName" })
}

/** `js_content main.hello`: the function `hello` in the file that `js_import main from x.js` (or `js_import main.js`) loads. */
class NginxJsFunctionReference(
    d: NginxDirective,
    range: TextRange,
    private val module: String,
    private val function: String
) :
    PsiPolyVariantReferenceBase<NginxDirective>(d, range, true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val psi = PsiManager.getInstance(element.project)
        // shortcut: finds the function by text so the JavaScript plugin stays optional; misses functions built at runtime
        val def = Regex(
            """(?:\bfunction\s*\*?\s*|\b(?:const|let|var)\s+|^\s*(?:async\s+)?)(${Regex.escape(function)})\s*[=(]""",
            RegexOption.MULTILINE
        )
        return NginxDeclarations.projectFiles(element.project).flatMap { f ->
            PsiTreeUtil.findChildrenOfType(f, NginxDirective::class.java)
                .filter { it.directiveName == "js_import" && jsModule(it) == module }
                .flatMap {
                    NginxPaths.resolve(
                        f,
                        StringUtil.unquoteString(it.args.last().joinToString("") { t -> t.text })
                    )
                }
        }.distinct().mapNotNull { vf ->
            val js = psi.findFile(vf) ?: return@mapNotNull null
            def.find(js.text)?.let { js.findElementAt(it.groups[1]!!.range.first) ?: js }
        }.map(::PsiElementResolveResult).toTypedArray()
    }

    private fun jsModule(d: NginxDirective): String {
        val args = d.args.map { a -> StringUtil.unquoteString(a.joinToString("") { it.text }) }
        return if (args.size == 3 && args[1] == "from") args[0] else args.last().substringAfterLast('/')
            .removeSuffix(".js")
    }
}

object NginxReferences {
    /** directive -> index of its module.function argument */
    private val JS_FUNCTION = mapOf(
        "js_access" to 0, "js_body_filter" to 0, "js_content" to 0, "js_filter" to 0, "js_header_filter" to 0,
        "js_periodic" to 0, "js_preread" to 0, "js_set" to 1,
    )
    private val PASS = setOf("proxy_pass", "grpc_pass", "uwsgi_pass")
    private val NAMED_LOCATION_USERS = setOf("try_files", "error_page", "post_action")

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
        if (d.directiveName in PASS) args.firstOrNull()?.let { arg ->
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
        JS_FUNCTION[d.directiveName]?.let { args.getOrNull(it)?.singleOrNull() }
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
