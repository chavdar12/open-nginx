package com.css.nginx.inspection

import com.css.nginx.inspection.fixes.NginxFix
import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxDeclarations
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** Checks the URL of proxy_pass / grpc_pass / uwsgi_pass for the mistakes `nginx -t` rejects. */
class NginxUrlAnnotator : Annotator {
    override fun annotate(e: PsiElement, holder: AnnotationHolder) {
        val d = e as? NginxDirective ?: return
        val schemes = SCHEMES[d.directiveName] ?: return
        val arg = d.args.firstOrNull() ?: return
        val url = StringUtil.unquoteString(arg.joinToString("") { it.text })
        if ('$' in url) return  // nginx only parses URLs with variables at runtime (corpus: proxy_pass http:/$uri is valid)
        val range = TextRange(arg.first().textRange.startOffset, arg.last().textRange.endOffset)
        fun error(message: String) = holder.problem(HighlightSeverity.ERROR, message).range(range).create()

        val sep = url.indexOf("://")
        if (sep < 0) {
            // grpc_pass, uwsgi_pass and stream's proxy_pass take a bare address; http's proxy_pass needs a scheme
            if (d.directiveName == "proxy_pass" && ancestors(d).any { it == "location" || it == "http" }) {
                holder.problem(HighlightSeverity.ERROR, "Invalid URL prefix in '$url', expected http:// or https://").range(range)
                    .withFix(NginxFix("Add http://", arg.first()) { e, doc -> doc.insertString(e.textRange.startOffset, "http://") }).create()
            } else {
                checkUpstream(d, url, range, holder)
            }
            return
        }
        val scheme = url.substring(0, sep)
        if (scheme.lowercase() !in schemes) return error("Invalid URL scheme '$scheme://', expected ${schemes.joinToString(" or ") { "$it://" }}")
        val rest = url.substring(sep + 3)
        if (rest.startsWith("unix:")) return  // socket path: no host or port to check
        val hostPort = rest.substringBefore('/')
        val host = if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
        val port = hostPort.removePrefix(host).removePrefix(":").takeIf { hostPort.length > host.length }
        checkUpstream(d, url, range, holder)
        when {
            host.isEmpty() || host == "[]" -> error("Missing host in URL '$url'")
            port != null && port.toIntOrNull() !in 1..65535 -> error("Invalid port '$port' in URL '$url'")
            d.directiveName == "proxy_pass" && rest.length > hostPort.length && cannotHaveUri(d) ->
                // nginx's own message for the most common proxy_pass mistake
                error("proxy_pass cannot have URI part in location given by regular expression, or inside named location, or inside \"if\" statement, or inside \"limit_except\" block")
        }
    }

    /**
     * `proxy_pass http://app` means upstream "app" when one exists, otherwise a DNS lookup of "app" at startup.
     * A bare name is legal (docker service names), so only a weak warning; dotted names, IPs, ports and localhost are clearly hosts.
     */
    private fun checkUpstream(d: NginxDirective, url: String, range: TextRange, holder: AnnotationHolder) {
        val host = NginxDeclarations.hostOf(url) ?: return
        if (!PLAIN_NAME.matches(host) || host == "localhost" || host.all { it.isDigit() }) return
        val upstreams = NginxDeclarations.declarations(d.containingFile) ?: return
        if (host !in upstreams) {
            holder.problem(HighlightSeverity.WEAK_WARNING, "Unknown upstream '$host': no upstream block with this name, so nginx will resolve '$host' as a host name")
                .range(range).create()
        }
    }

    /** Regex (~, ~*) or named (@) location, or an if / limit_except block: nginx can't map a URI part there. */
    private fun cannotHaveUri(d: NginxDirective): Boolean {
        val parent = PsiTreeUtil.getParentOfType(d, NginxDirective::class.java) ?: return false
        if (parent.directiveName == "if" || parent.directiveName == "limit_except") return true
        if (parent.directiveName != "location") return false
        val first = parent.args.firstOrNull()?.joinToString("") { it.text } ?: return false
        return first.startsWith("@") || first.startsWith("~")
    }

    private fun ancestors(d: NginxDirective) =
        generateSequence(PsiTreeUtil.getParentOfType(d, NginxDirective::class.java)) { PsiTreeUtil.getParentOfType(it, NginxDirective::class.java) }
            .map { it.directiveName }

}

private val PLAIN_NAME = Regex("[A-Za-z0-9_-]+")

private val SCHEMES = mapOf(
    "proxy_pass" to listOf("http", "https"),
    "grpc_pass" to listOf("grpc", "grpcs"),
    "uwsgi_pass" to listOf("uwsgi", "suwsgi"),
)
