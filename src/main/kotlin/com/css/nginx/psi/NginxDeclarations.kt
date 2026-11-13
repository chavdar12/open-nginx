package com.css.nginx.psi

import com.css.nginx.data.NginxDirectives
import com.css.nginx.lang.NginxFileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/**
 * Everything a config declares by name, across all nginx files of the project:
 * `upstream app` (key "app"), variables from set/map/geo/... (key "$x") and named locations (key "@x").
 */
object NginxDeclarations {
    class Index(val byName: Map<String, List<NginxDirective>>, val captures: Set<String>)

    /** directive -> index of the argument holding the declared variable, e.g. `map $uri $new` declares $new */
    private val VARIABLE_ARG = mapOf(
        "set" to 0,
        "map" to 1,
        "split_clients" to 1,
        "js_var" to 0,
        "set_by_lua" to 0,
        "set_by_lua_block" to 0,
        "set_by_lua_file" to 0,
    )

    /** blocks whose entries are `key value;` pairs, not directives: `map $a $b { foo $c; }` uses $c */
    private val KEYED_BLOCKS = setOf("map", "geo", "split_clients", "types", "charset_map", "match")
    private val CAPTURE = Regex("""\(\?P?<([A-Za-z_]\w*)>""")

    fun nameIdentifier(d: NginxDirective): PsiElement? {
        val name = d.directiveName
        val args = d.args
        fun variable(arg: List<PsiElement>?) =
            arg?.singleOrNull()?.takeIf { it.node.elementType == NginxTypes.VARIABLE }
        return when {
            name == "upstream" -> args.firstOrNull()?.singleOrNull()?.takeIf { it.node.elementType == NginxTypes.WORD }
            name == "location" -> args.firstOrNull()?.singleOrNull()
                ?.takeIf { it.node.elementType == NginxTypes.WORD && it.text.startsWith("@") }

            name == "geo" -> variable(args.lastOrNull())  // geo [$address] $variable
            name in VARIABLE_ARG -> variable(args.getOrNull(VARIABLE_ARG.getValue(name)))
            // set-misc's set_unescape_uri $x ..., and perl_set, js_set, json_set, auth_request_set $x ...
            name.startsWith("set_") || name.endsWith("_set") -> variable(args.firstOrNull())
            // geoip2 /db.mmdb { $geoip2_country country iso_code; }
            d.nameElement.node.elementType == NginxTypes.VARIABLE && (d.parent?.parent as? NginxDirective)?.directiveName == "geoip2" -> d.nameElement
            // a module we have no data for, e.g. `auth_jwt_claim_set $sub sub;`: a lone $variable first is almost always what it declares
            name !in NginxDirectives.all && d.nameElement.node.elementType == NginxTypes.NAME &&
                    (d.parent?.parent as? NginxDirective)?.directiveName !in KEYED_BLOCKS -> variable(args.firstOrNull())

            else -> null
        }
    }

    /** `${x}` and `$x` are the same variable */
    fun key(text: String) =
        if (text.startsWith("\${") && text.endsWith("}")) "$" + text.substring(2, text.length - 1) else text

    /** null while indexing */
    fun index(file: PsiFile): Index? {
        val project = file.project
        if (DumbService.isDumb(project)) return null
        val inProject = CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(
                projectFiles(project).map(::declaredIn),
                PsiModificationTracker.getInstance(project)
            )
        }
        // the current file counts even when it is outside the project or not indexed yet
        val all = (inProject + declaredIn(file.originalFile)).distinct()
        return Index(
            all.flatMap { it.first }.groupBy({ it.first }, { it.second }),
            all.flatMapTo(HashSet()) { it.second })
    }

    fun declarations(file: PsiFile): Map<String, List<NginxDirective>>? = index(file)?.byName

    fun projectFiles(project: Project): List<PsiFile> =
        FileTypeIndex.getFiles(NginxFileType, GlobalSearchScope.projectScope(project))
            .mapNotNull { PsiManager.getInstance(project).findFile(it) }

    private fun declaredIn(file: PsiFile) = CachedValuesManager.getCachedValue(file) {
        val named = PsiTreeUtil.findChildrenOfType(file, NginxDirective::class.java)
            .mapNotNull { d -> d.declaredKey?.let { it to d } }
        // regex named captures (?<name>...) define $name too; they only silence the unknown-variable check
        val captures = CAPTURE.findAll(file.text).map { it.groupValues[1] }.toSet()
        CachedValueProvider.Result.create(named to captures, file)
    }

    /** Host of a plain `http://app/x` or `app` URL; null when it has a port, a variable, a socket or an IPv6 literal. */
    fun hostOf(url: String): String? {
        if ('$' in url) return null
        val rest = url.substringAfter("://")
        if (rest.startsWith("unix:")) return null
        val host = rest.substringBefore('/')
        return host.takeIf { it.isNotEmpty() && ':' !in it && !it.startsWith("[") }
    }
}
