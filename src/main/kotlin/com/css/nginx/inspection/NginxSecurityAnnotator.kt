package com.css.nginx.inspection

import com.css.nginx.inspection.fixes.NginxFix
import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxRegex
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Security mistakes in the spirit of gixy (github.com/yandex/gixy): alias traversal, lost add_header, Host spoofing,
 * HTTP splitting, origin checks, reflected CORS, weak TLS, SSRF, version disclosure.
 */
class NginxSecurityAnnotator : Annotator {
    override fun annotate(e: PsiElement, holder: AnnotationHolder) {
        val d = e as? NginxDirective ?: return
        // `if ($a ~ "re")`: the closing paren is glued to the last argument
        val args = d.args.map { a ->
            StringUtil.unquoteString(a.joinToString("") { it.text }
                .let { if (d.directiveName == "if") it.removeSuffix(")") else it })
        }

        fun range(i: Int) = d.args[i].let { TextRange(it.first().textRange.startOffset, it.last().textRange.endOffset) }
        fun warn(message: String, i: Int? = null, severity: HighlightSeverity = HighlightSeverity.WARNING) =
            holder.problem(severity, "Security: $message").range(if (i == null) d.nameElement.textRange else range(i))
                .create()

        val parent = PsiTreeUtil.getParentOfType(d, NginxDirective::class.java)

        when (d.directiveName) {
            "alias" -> {
                val loc = parent?.takeIf { it.directiveName == "location" && it.args.size == 1 }?.args?.get(0)
                    ?.joinToString("") { it.text }
                if (loc != null && !loc.startsWith("~") && !loc.startsWith("@") && !loc.endsWith("/") && args.firstOrNull()
                        ?.endsWith("/") == true
                )
                    warn(
                        "path traversal: location '$loc' lacks the trailing slash its alias has, so '$loc../' reads the folder above '${args[0]}'",
                        0
                    )
            }

            "add_header" -> {
                lostHeaders(d, parent)?.let { (block, headers) ->
                    val names =
                        headers.joinToString(", ") { it.args.firstOrNull()?.joinToString("") { t -> t.text }.orEmpty() }
                    // nginx copies a parent's add_header lines only into blocks that have none of their own
                    holder.problem(
                        HighlightSeverity.WARNING,
                        "Security: $names from '$block' is not sent here, because this block has its own add_header"
                    )
                        .range(d.nameElement)
                        .withFix(NginxFix("Copy the add_header lines from '$block' into this block", d) { e, doc ->
                            val start = e.textRange.startOffset
                            val indent =
                                doc.charsSequence.subSequence(doc.getLineStartOffset(doc.getLineNumber(start)), start)
                            doc.insertString(start, headers.joinToString("") { "${it.text}\n$indent" })
                        }).create()
                }
                if (args.getOrNull(0)
                        .equals("Access-Control-Allow-Origin", true) && args.getOrNull(1) == "\$http_origin" &&
                    generateSequence(parent) {
                        PsiTreeUtil.getParentOfType(
                            it,
                            NginxDirective::class.java
                        )
                    }.none { it.directiveName == "if" }
                )
                    warn("CORS: echoing \$http_origin allows any site; check it against a map or if first", 1)
                splitting(d, args, 1, ::warn)
            }

            "proxy_set_header" -> {
                if (args.getOrNull(0).equals("Host", true) && args.getOrNull(1) == "\$http_host")
                    warn(
                        "Host spoofing: \$http_host is whatever the client sent, \$host is validated against server_name",
                        1
                    )
                splitting(d, args, 1, ::warn)
            }
            // only redirects put the value into a header (Location); return 200 "..." is a body
            "return" -> if (args.size == 1 || args[0] in REDIRECTS) splitting(d, args, args.lastIndex, ::warn)
            "if" -> {
                val i = args.indexOfFirst { it == "~" || it == "~*" }
                if (i > 0 && (args[i - 1].endsWith("\$http_origin") || args[i - 1].endsWith("\$http_referer")))
                    weakRegex(args.getOrNull(i + 1))?.let { warn("origin check can be bypassed: $it", i + 1) }
            }

            "ssl_protocols" -> args.forEachIndexed { i, p ->
                if (p in WEAK_PROTOCOLS) warn("$p is insecure; use TLSv1.2 TLSv1.3", i)
            }

            "ssl_ciphers" -> {
                val weak = args.firstOrNull()?.split(':')
                    ?.filter { c -> !c.startsWith("!") && !c.startsWith("-") && WEAK_CIPHER.containsMatchIn(c) }
                if (!weak.isNullOrEmpty()) warn("weak ciphers enabled: ${weak.joinToString(", ")}", 0)
            }

            "proxy_pass", "grpc_pass", "fastcgi_pass", "uwsgi_pass", "scgi_pass" -> {
                val host = args.firstOrNull()?.substringAfter("://")?.substringBefore('/') ?: return
                USER_CONTROLLED.find(host)
                    ?.let { warn("SSRF: the client controls '${it.value}', so it decides where nginx connects", 0) }
            }

            "server_tokens" -> if (args.firstOrNull() == "on") warn(
                "reveals the nginx version in headers and error pages",
                0,
                HighlightSeverity.WEAK_WARNING
            )

            "valid_referers" -> args.indexOf("none").takeIf { it >= 0 }?.let {
                warn(
                    "'none' accepts requests with no Referer, which anyone can send",
                    it,
                    HighlightSeverity.WEAK_WARNING
                )
            }
        }
        for ((token, range) in NginxRegex.of(d)) {
            val re = range.substring(token.text)
            if (NESTED_QUANTIFIER.containsMatchIn(re)) holder.problem(
                HighlightSeverity.WARNING,
                "Security: ReDoS: a repeated group that itself repeats ('$re') can take exponential time on a crafted URL"
            )
                .range(range.shiftRight(token.textRange.startOffset)).create()
        }
        // map $http_origin $cors { ~^https://example.com 1; }
        if (parent?.directiveName == "map" && parent.args.firstOrNull()?.joinToString("") { it.text }
                .let { it == "\$http_origin" || it == "\$http_referer" }) {
            val key = StringUtil.unquoteString(d.directiveName)
            if (key.startsWith("~")) weakRegex(key.removePrefix("~*").removePrefix("~"))?.let {
                holder.problem(HighlightSeverity.WARNING, "Security: origin check can be bypassed: $it")
                    .range(d.nameElement).create()
            }
        }
    }

    /** The nearest enclosing block that has add_header, when [d] is the first add_header of its own block. */
    private fun lostHeaders(d: NginxDirective, parent: NginxDirective?): Pair<String, List<NginxDirective>>? {
        val siblings = parent?.directives ?: return null
        if (siblings.first { it.directiveName == "add_header" } != d || siblings.any { it.directiveName == "add_header_inherit" }) return null
        var p = PsiTreeUtil.getParentOfType(parent, NginxDirective::class.java)
        while (p != null) {
            if (p.directives.any { it.directiveName == "add_header_inherit" }) return null
            val headers = p.directives.filter { it.directiveName == "add_header" }
            if (headers.isNotEmpty()) return p.directiveName to headers
            p = PsiTreeUtil.getParentOfType(p, NginxDirective::class.java)
        }
        return null
    }

    /** $uri and $document_uri are decoded, so %0d%0a becomes a real line break inside the header. */
    private fun splitting(
        d: NginxDirective,
        args: List<String>,
        i: Int,
        warn: (String, Int?, HighlightSeverity) -> Unit
    ) {
        val v = args.getOrNull(i) ?: return
        DECODED.find(v)?.let {
            warn(
                "HTTP splitting: ${it.value} is URL-decoded and can carry a line break into the response; use \$request_uri",
                i,
                HighlightSeverity.WARNING
            )
        }
    }

    /** Why an origin regex is too loose, or null. */
    private fun weakRegex(re: String?): String? = when {
        re == null -> null
        !re.startsWith("^") -> "'$re' has no ^ anchor, so https://evil.com/?https://example.com matches"
        !re.endsWith("$") -> "'$re' has no $ anchor, so https://example.com.evil.com matches"
        Regex("""(?<!\\)\.(?![*+?])""").containsMatchIn(re.substringAfter("://")) -> "the unescaped '.' in '$re' also matches any character, e.g. examplexcom"
        else -> null
    }
}

// (a+)+ (\w+\s?)* (.*)*: a group with + or * inside, itself followed by + * or {n,}
private val NESTED_QUANTIFIER = Regex("""\((?:[^()\\]|\\.)*[+*](?:[^()\\]|\\.)*\)(?:[+*]|\{\d+,\d*})""")
private val REDIRECTS = setOf("301", "302", "303", "307", "308")
private val WEAK_PROTOCOLS = setOf("SSLv2", "SSLv3", "TLSv1", "TLSv1.1")
private val WEAK_CIPHER = Regex("NULL|RC4|MD5|EXPORT|EXP-|DES|IDEA|SEED", RegexOption.IGNORE_CASE)
private val DECODED = Regex("""\$(?:\{)?(uri|document_uri)\b""")
private val USER_CONTROLLED =
    Regex("""\$\{?(http_\w+|arg_\w+|cookie_\w+|host|request_uri|uri|args|query_string|[1-9])\b""")
