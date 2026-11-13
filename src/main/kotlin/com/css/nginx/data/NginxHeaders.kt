package com.css.nginx.data

import com.css.nginx.psi.NginxDirective
import com.intellij.psi.PsiElement

/** Common HTTP headers for hover and completion; hand-written in src/main/resources/nginx/headers.tsv. */
object NginxHeaders {
    class Header(val name: String, val url: String, val doc: String)

    /** Keyed by lower-case name: header names are case-insensitive. */
    val all: Map<String, Header> = javaClass.getResourceAsStream("/nginx/headers.tsv")!!.bufferedReader().useLines { lines ->
        lines.filterNot { it.startsWith("#") }.map { it.split('\t') }.associate { it[0].lowercase() to Header(it[0], it[1], it[2]) }
    }

    /** Directives whose first argument is a header name. */
    private val DIRECTIVES = setOf(
        "add_header", "add_trailer", "proxy_set_header", "grpc_set_header",
        "proxy_hide_header", "proxy_pass_header", "grpc_hide_header", "grpc_pass_header",
        "fastcgi_hide_header", "fastcgi_pass_header", "uwsgi_hide_header", "uwsgi_pass_header",
        "scgi_hide_header", "scgi_pass_header",
    )

    /** True when [e] is a header-name argument (the Host in proxy_set_header Host $host, any of proxy_ignore_headers). */
    fun isHeaderName(e: PsiElement): Boolean {
        val d = e.parent as? NginxDirective ?: return false
        if (d.directiveName.endsWith("_ignore_headers")) return d.args.any { it.firstOrNull() == e }  // every argument is a header
        return d.directiveName in DIRECTIVES && d.args.firstOrNull()?.firstOrNull() == e
    }

    /** The header behind $http_user_agent, $sent_http_..., $upstream_http_... variables. */
    fun forVariable(name: String): Header? =
        listOf("sent_http_", "upstream_http_", "http_").firstOrNull { name.startsWith(it) }
            ?.let { all[name.removePrefix(it).replace('_', '-')] }
}
