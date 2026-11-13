package com.css.nginx.data

import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxIncludes
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil

/** Directive, parameter and variable data generated from the official nginx docs, see tools/gen_directives.py. */
object NginxDirectives {
    class Info(
        val name: String,
        /** empty = allowed anywhere (include) */
        val contexts: Set<String>, val min: Int, val max: Int?, val values: Set<String>,
        /** HTML; each syntax form is one line, separated by <br> */
        val url: String, val default: String, val syntax: String, val doc: String,
    ) {
        /** First syntax form as plain text, for completion */
        val plainSyntax get() = StringUtil.unescapeXmlEntities(StringUtil.removeHtmlTags(syntax.substringBefore("<br>")))

        /** ngx_http_gzip_module.html -> http_gzip, github.com/openresty/headers-more-nginx-module -> headers-more */
        val module get() = url.substringBefore('#').substringAfterLast('/').substringBefore('.')
            .removePrefix("ngx_").removeSuffix("_module").removeSuffix("-nginx-module")
    }

    class Variable(val name: String, val url: String, val doc: String)

    private fun rows(file: String) = javaClass.getResourceAsStream(file)!!.bufferedReader().useLines { lines ->
        lines.filterNot { it.startsWith("#") }.map { it.split('\t') }.toList()
    }

    /** Core nginx directives, plus popular third-party modules (OpenResty, headers-more, brotli, geoip2...); core wins on clashes. */
    val all: Map<String, Info> = (rows("/nginx/thirdparty.tsv").map(::escapeDocs) + rows("/nginx/directives.tsv")).associate {
        it[0] to Info(
            it[0],
            it[1].split(',').filter(String::isNotEmpty).toSet(),
            it[2].toInt(),
            it[3].toIntOrNull(),
            it[4].split(',').filter(String::isNotEmpty).toSet(),
            it[5],
            it[6],
            it[7],
            it[8],
        )
    }

    /** thirdparty.tsv is plain text taken from READMEs, directives.tsv is HTML already */
    private fun escapeDocs(row: List<String>) = row.mapIndexed { i, v -> if (i >= 7) StringUtil.escapeXmlEntities(v) else v }

    /** Built-in variables; a name ending in _ is a prefix family such as arg_ for $arg_name. */
    val variables: Map<String, Variable> =
        rows("/nginx/variables.tsv").associate { it[0] to Variable(it[0], it[1], it[2]) }

    fun variable(name: String): Variable? =
        variables[name] ?: variables.values.filter { it.name.endsWith("_") && name.startsWith(it.name) }
            .maxByOrNull { it.name.length }

    /** Parameter docs per directive, e.g. proxy_cache_path -> levels -> "The levels parameter defines...". */
    val parameters: Map<String, Map<String, String>> =
        rows("/nginx/parameters.tsv").groupBy { it[0] }.mapValues { (_, r) -> r.associate { it[1] to it[2] } }

    /** Doc for an argument such as `levels=1:2` or the flag `ssl`. */
    fun parameter(directive: String, arg: String): String? = parameters[directive]?.get(arg.substringBefore('='))

    /** Directives the docs mark obsolete -> the docs' note ("...should be used instead"). */
    val obsolete: Map<String, String> = rows("/nginx/obsolete.tsv").associate { it[0] to it[1] }

    /** Directives nginx rejects as "is duplicate" when repeated in one block (tools/gen_single.py). */
    val single: Set<String> = rows("/nginx/single.tsv").mapTo(HashSet()) { it[0] }

    /** Every block that holds directives; other blocks (map, types, geo...) hold free-form key/value pairs. */
    val contexts: Set<String> = all.values.flatMapTo(HashSet()) { it.contexts }

    /** Enclosing block name, "main" at the top of nginx.conf; at the top of a snippet, where its `include`s sit (null if unknown). */
    fun contextOf(d: NginxDirective): String? =
        PsiTreeUtil.getParentOfType(d, NginxDirective::class.java)?.directiveName
            ?: if (d.containingFile.name == "nginx.conf") "main" else NginxIncludes.contextOf(d.containingFile)
}
