package com.css.nginx.inspection

import com.css.nginx.data.NginxDirectives
import com.css.nginx.inspection.fixes.NginxFix
import com.css.nginx.inspection.fixes.deleteLine
import com.css.nginx.psi.NginxDeclarations
import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxIncludes
import com.css.nginx.psi.NginxPaths
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** Directives nginx rejects as "is duplicate", clashing server blocks, and deprecated listen parameters. */
class NginxDuplicateAnnotator : Annotator {
    override fun annotate(e: PsiElement, holder: AnnotationHolder) {
        val d = e as? NginxDirective ?: return
        val siblings = siblings(d)
        if (d.directiveName in NginxDirectives.single && siblings.takeWhile { it != d }
                .any { it.directiveName == d.directiveName }) {
            holder.problem(HighlightSeverity.ERROR, "'${d.directiveName}' directive is duplicate").range(d.nameElement)
                .withFix(NginxFix("Remove duplicate", d, ::deleteLine)).create()
        }
        if (d.directiveName == "listen") for (arg in d.args.drop(1)) {
            val text = arg.joinToString("") { it.text }
            val message = when (text) {
                "http2" -> "The 'http2' parameter is deprecated since 1.25.1; use 'http2 on;' in the server block"
                "spdy" -> "SPDY was removed in nginx 1.9.5; use 'http2 on;'"
                else -> continue
            }
            holder.problem(HighlightSeverity.WARNING, message).range(arg.first()).create()
        }
        if (d.directiveName == "server") {
            val parent = PsiTreeUtil.getParentOfType(d, NginxDirective::class.java)
            // conf.d/*.conf: server blocks at the top of a file included into http
            if (parent?.directiveName == "http" || parent == null && NginxIncludes.contextOf(d.containingFile) == "http") checkServer(
                d,
                siblings,
                holder
            )
        }
    }

    /**
     * nginx picks one server block per address and name; a second one with the same pair is ignored ("conflicting server name"),
     * a second default_server for the same address is an error.
     */
    private fun checkServer(d: NginxDirective, siblings: List<NginxDirective>, holder: AnnotationHolder) {
        // other files' order depends on include order, so a clash with one is reported in both files
        val before = siblings.takeWhile { it != d }.filter { it.directiveName == "server" } + otherFileServers(d)
        if (before.isEmpty()) return
        fun where(s: NginxDirective) =
            if (s.containingFile == d.containingFile) "an earlier server block" else "a server block in ${s.containingFile.name}"

        val mine = listens(d)
        for ((addr, isDefault, listen) in mine) {
            val other =
                if (isDefault) before.firstOrNull { s -> listens(s).any { it.first == addr && it.second } } else null
            if (other != null) holder.problem(
                HighlightSeverity.ERROR,
                "A duplicate default server for $addr (${where(other)})"
            ).range(listen ?: d.nameElement).create()
        }
        val names = d.directives.filter { it.directiveName == "server_name" }.flatMap { it.args }
        for (name in names) {
            val text = name.joinToString("") { it.text }
            val (addr, other) = mine.firstNotNullOfOrNull { (addr) ->
                before.firstOrNull { s -> listens(s).any { it.first == addr } && text in serverNames(s) }
                    ?.let { addr to it }
            } ?: continue
            val message =
                if (other.containingFile == d.containingFile) "an earlier server block already has it, nginx ignores this one"
                else "${where(other)} has it too, nginx uses whichever is included first"
            holder.problem(HighlightSeverity.WARNING, "Conflicting server name '$text' on $addr: $message")
                .range(name.first()).create()
        }
    }

    /** http server blocks in the project's other files that belong to the same config (same folder holding nginx.conf). */
    private fun otherFileServers(d: NginxDirective): List<NginxDirective> {
        val file = d.containingFile.originalFile
        val base = file.virtualFile?.parent?.let(NginxPaths::baseDir) ?: return emptyList()
        if (DumbService.isDumb(d.project)) return emptyList()
        return NginxDeclarations.projectFiles(d.project)
            .filter { it != file && it.virtualFile?.parent?.let(NginxPaths::baseDir) == base }.flatMap { f ->
                val top = NginxIncludes.contextOf(f) == "http"
                PsiTreeUtil.findChildrenOfType(f, NginxDirective::class.java).filter { s ->
                    s.directiveName == "server" && PsiTreeUtil.getParentOfType(s, NginxDirective::class.java)
                        .let { it?.directiveName == "http" || it == null && top }
                }
            }
    }

    /** (normalized address, default_server, listen element) per listen; a server without listen listens on *:80. */
    private fun listens(server: NginxDirective): List<Triple<String, Boolean, PsiElement?>> {
        val all = server.directives.filter { it.directiveName == "listen" }.map { l ->
            val args = l.args.map { a -> a.joinToString("") { it.text } }
            val a = args.firstOrNull().orEmpty()
            val addr = when {
                a.startsWith("unix:") -> a
                a.all { it.isDigit() } -> "*:$a"
                a.startsWith("[") -> a.takeIf { "]:" in it } ?: "$a:80"
                ':' in a -> a.replace("0.0.0.0:", "*:")
                else -> "$a:80"
            }
            Triple(addr, "default_server" in args || "default" in args, l.args.firstOrNull()?.first())
        }
        return all.ifEmpty { listOf(Triple("*:80", false, null)) }
    }

    private fun serverNames(server: NginxDirective) =
        server.directives.filter { it.directiveName == "server_name" }
            .flatMap { it.args.map { a -> a.joinToString("") { t -> t.text } } }.toSet().ifEmpty { setOf("") }

    private fun siblings(d: NginxDirective): List<NginxDirective> =
        PsiTreeUtil.getChildrenOfTypeAsList(d.parent, NginxDirective::class.java)

}
