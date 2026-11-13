package com.css.nginx.inspection

import com.css.nginx.data.NginxCustomDirectives
import com.css.nginx.data.NginxDirectives
import com.css.nginx.highlighting.NginxColors
import com.css.nginx.inspection.fixes.NginxFix
import com.css.nginx.lexer.VARIABLE_REF
import com.css.nginx.psi.NginxDeclarations
import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxPaths
import com.css.nginx.psi.NginxTypes
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.EditDistance

/** Files every nginx package ships next to nginx.conf; projects rarely keep copies. */
private val SHIPPED = setOf(
    "mime.types",
    "fastcgi_params",
    "fastcgi.conf",
    "proxy_params",
    "scgi_params",
    "uwsgi_params",
    "koi-utf",
    "koi-win",
    "win-utf",
    "snippets/fastcgi-php.conf"
)
private val PARAMETER = Regex("""^([a-z_][a-z0-9_]*)=""")

class NginxAnnotator : Annotator {
    override fun annotate(e: PsiElement, holder: AnnotationHolder) {
        if (e.node.elementType == NginxTypes.STRING) {
            // perl "sub { my $r = shift; }" and inline lua/js: their $names are not nginx variables
            if ((e.parent as? NginxDirective)?.directiveName?.let {
                    it.startsWith("perl") || "lua" in it || it.startsWith(
                        "js_"
                    )
                } == true) return
            for (m in VARIABLE_REF.findAll(e.text)) {
                val range = TextRange.from(e.textRange.startOffset + m.range.first, m.value.length)
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(range)
                    .textAttributes(NginxColors.VARIABLE).create()
                checkVariable(e, m.value, range, holder)
            }
            return
        }
        if (e.node.elementType == NginxTypes.VARIABLE) return checkVariable(e, e.text, e.textRange, holder)
        if (e.node.elementType == NginxTypes.WORD && e.parent is NginxDirective) {
            // name of a name=value option (levels=1:2, zone=perip, weight=5); only at the start of an argument
            val glued = e.prevSibling !is PsiWhiteSpace
            val name = PARAMETER.find(e.text)?.groupValues?.get(1)
            if (!glued && name != null) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(TextRange.from(e.textRange.startOffset, name.length))
                    .textAttributes(NginxColors.PARAMETER).create()
            }
            return
        }
        val d = e as? NginxDirective ?: return
        if (d.nameElement.node.elementType != NginxTypes.NAME) return
        val ctx = NginxDirectives.contextOf(d)
        if (ctx != null && ctx !in NginxDirectives.contexts) return
        val name = d.directiveName
        val info = NginxDirectives.all[name]
        if (info == null) {
            if (name in d.project.service<NginxCustomDirectives>().names) return
            // warning, not error: third-party modules add their own directives
            val b = holder.problem(HighlightSeverity.WARNING, "Unknown directive '$name'").range(d.nameElement)
            closest(name, ctx)?.let { b.withFix(NginxFix.replace("Change to '$it'", d.nameElement, it)) }
            b.withFix(
                NginxFix(
                    "Add '$name' to this project's known directives",
                    d
                ) { e, _ -> e.project.service<NginxCustomDirectives>().add(name) })
            b.create()
            return
        }
        NginxDirectives.obsolete[name]?.let {
            holder.problem(HighlightSeverity.WARNING, "'$name' is obsolete. $it").range(d.nameElement).create()
        }
        if (ctx != null && info.contexts.isNotEmpty() && ctx !in info.contexts) {
            val b = holder.problem(HighlightSeverity.ERROR, "'$name' is not allowed in '$ctx'").range(d.nameElement)
            moveFix(d, info.contexts)?.let(b::withFix)
            b.create()
        }
        val args = d.args
        if (name == "include") checkInclude(d, holder)
        if (args.size < info.min || info.max != null && args.size > info.max) {
            val expected = when (info.max) {
                info.min -> "${info.min}"
                null -> "at least ${info.min}"
                else -> "${info.min} to ${info.max}"
            }
            holder.problem(HighlightSeverity.ERROR, "'$name' takes $expected argument(s), got ${args.size}")
                .range(d.nameElement).create()
        } else if (info.values.isNotEmpty() && args.size == 1 && args[0].none { it.node.elementType == NginxTypes.VARIABLE }) {
            val value = StringUtil.unquoteString(args[0].joinToString("") { it.text })
            if (info.values.none { it.equals(value, ignoreCase = true) }) {  // nginx compares these case-insensitively
                val b = holder.problem(
                    HighlightSeverity.ERROR,
                    "Invalid value '$value' for '$name', expected ${info.values.joinToString(" | ")}"
                )
                    .range(TextRange(args[0].first().textRange.startOffset, args[0].last().textRange.endOffset))
                if (args[0].size == 1) info.values.take(8)
                    .forEach { b.withFix(NginxFix.replace("Replace with '$it'", args[0][0], it)) }
                b.create()
            }
        }
    }

    /** nginx refuses to start on an unknown variable; set/map/geo/... anywhere in the project and regex captures count as declared. */
    private fun checkVariable(e: PsiElement, text: String, range: TextRange, holder: AnnotationHolder) {
        val key = NginxDeclarations.key(text)
        val name = key.removePrefix("$")
        if (name.all { it.isDigit() } || NginxDirectives.variable(name) != null || name in e.project.service<NginxCustomDirectives>().variables) return
        // docker's nginx image runs envsubst on *.template files: ${NGINX_HOST} is an environment variable
        if (e.containingFile.name.endsWith(".template") && name.none { it.isLowerCase() }) return
        val index = NginxDeclarations.index(e.containingFile) ?: return
        if (key in index.byName || name in index.captures) return
        holder.problem(
            HighlightSeverity.WARNING,
            "Unknown variable '$key': not built in and not declared by set, map, geo... in this project"
        )
            .range(range)
            // modules the plugin doesn't know (vts, ja3, njs builds...) add variables without declaring them
            .withFix(
                NginxFix(
                    "Add '$key' to this project's known variables",
                    e
                ) { el, _ -> el.project.service<NginxCustomDirectives>().addVariable(name) })
            .create()
    }

    /** Only relative paths without wildcards: absolute ones usually live outside the project, and globs may match nothing. */
    private fun checkInclude(d: NginxDirective, holder: AnnotationHolder) {
        val arg = NginxPaths.pathArg(d) ?: return
        val path = StringUtil.unquoteString(arg.joinToString("") { it.text })
        if (path.startsWith("/") || NginxPaths.isGlob(path) || path in SHIPPED || NginxPaths.resolve(
                d.containingFile,
                path
            ).isNotEmpty()
        ) return
        holder.problem(HighlightSeverity.WEAK_WARNING, "Cannot find included file '$path'")
            .range(TextRange(arg.first().textRange.startOffset, arg.last().textRange.endOffset)).create()
    }

    /** A known directive one or two typos away that is valid here: "proxy_pas" -> "proxy_pass". */
    private fun closest(name: String, ctx: String?): String? {
        if (name.length < 4) return null
        return NginxDirectives.all.values.asSequence()
            .filter { ctx == null || it.contexts.isEmpty() || ctx in it.contexts }
            .map { it.name to EditDistance.levenshtein(name, it.name, false) }.filter { it.second <= 2 }
            .minByOrNull { it.second }?.first
    }

    /** Moves a misplaced directive up to the nearest enclosing block where it is allowed, just before the block it was in. */
    private fun moveFix(d: NginxDirective, allowed: Set<String>): NginxFix? {
        var inner: PsiElement = d
        var outer = PsiTreeUtil.getParentOfType(d, NginxDirective::class.java)
        while (true) {
            inner = outer ?: return null
            outer = PsiTreeUtil.getParentOfType(inner, NginxDirective::class.java)
            val ctx = outer?.directiveName ?: if (d.containingFile.name == "nginx.conf") "main" else return null
            if (ctx in allowed) return NginxFix("Move to the enclosing '$ctx' block", d) { e, doc ->
                val target = inner
                val text = e.text
                val indent = NginxFix.indentOf(target, doc)
                NginxFix.deleteLine(e, doc)  // after the target start, so the insert offset below stays valid
                doc.insertString(target.textRange.startOffset, "$text\n$indent")
            }
        }
    }
}
