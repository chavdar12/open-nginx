package com.css.nginx.intention

import com.css.nginx.inspection.fixes.NginxFix
import com.css.nginx.psi.NginxDirective
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil

/** On an HTTPS server block: adds a port-80 server that redirects the same names to https. */
class NginxSslRedirectIntention : NginxIntention("Add HTTP to HTTPS redirect server") {
    override fun target(at: NginxDirective, editor: Editor): NginxDirective? {
        val server = generateSequence(at) {
            PsiTreeUtil.getParentOfType(
                it,
                NginxDirective::class.java
            )
        }.firstOrNull { it.directiveName == "server" } ?: return null
        val https = server.directives.any {
            it.directiveName == "listen" && it.args.any { a ->
                a.joinToString("") { t -> t.text }.let { t -> t == "ssl" || t.endsWith("443") }
            }
        }
        if (!https) return null
        val names = names(server)
        // already redirected: another server in the same block listens on 80 for one of these names
        val siblings = PsiTreeUtil.getChildrenOfTypeAsList(server.parent, NginxDirective::class.java)
            .filter { it != server && it.directiveName == "server" }
        val covered = siblings.any { s ->
            s.directives.any {
                it.directiveName == "listen" && it.args.firstOrNull()?.joinToString("") { t -> t.text }
                    ?.endsWith("80") == true
            } && names(s).intersect(names.toSet()).isNotEmpty()
        }
        return server.takeUnless { covered }
    }

    override fun apply(d: NginxDirective, editor: Editor) {
        val doc = editor.document
        val i = NginxFix.indentOf(d, doc)
        val names = names(d).ifEmpty { listOf("_") }.joinToString(" ")
        doc.insertString(
            d.textRange.endOffset,
            "\n\n${i}server {\n$i    listen 80;\n$i    listen [::]:80;\n$i    server_name $names;\n$i    return 301 https://\$host\$request_uri;\n$i}",
        )
    }

    private fun names(server: NginxDirective) =
        server.directives.filter { it.directiveName == "server_name" }
            .flatMap { it.args.map { a -> a.joinToString("") { t -> t.text } } }
}
