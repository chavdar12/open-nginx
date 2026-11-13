package com.css.nginx.psi

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/** File paths in directives (include, root, ssl_certificate...) and how nginx would find them in this project. */
object NginxPaths {
    private val SINGLE = setOf(
        "include", "root", "alias", "auth_basic_user_file", "ssl_certificate", "ssl_certificate_key", "ssl_trusted_certificate",
        "ssl_client_certificate", "ssl_dhparam", "ssl_crl", "ssl_password_file", "ssl_stapling_file", "ssl_session_ticket_key",
        "proxy_ssl_certificate", "proxy_ssl_certificate_key", "proxy_ssl_trusted_certificate", "js_path", "geoip2",
        "geoip_country", "geoip_city", "geoip_org",
    )
    val DIRECTORIES = setOf("root", "alias", "js_path")

    /** The path argument of [d], or null when it has none or it contains variables (only known at runtime). */
    fun pathArg(d: NginxDirective): List<PsiElement>? {
        val name = d.directiveName
        val args = d.args
        val arg = when {
            name in SINGLE -> args.firstOrNull()
            name == "js_import" -> args.lastOrNull()  // js_import [name from] path
            name.endsWith("_by_lua_file") -> args.firstOrNull { it.singleOrNull()?.node?.elementType != NginxTypes.VARIABLE }
            else -> null
        } ?: return null
        if (arg.any { it.node.elementType == NginxTypes.VARIABLE } || arg.first().text.startsWith("data:")) return null
        return arg
    }

    fun isGlob(path: String) = path.any { it in "*?[" }

    /**
     * nginx resolves relative paths against its conf folder; that is approximated as the closest folder up the tree that
     * holds nginx.conf. Absolute paths (/etc/nginx/conf.d/x.conf) match their longest tail found in the project (conf.d/x.conf).
     */
    fun resolve(file: PsiFile, path: String): List<VirtualFile> {
        val own = file.originalFile.virtualFile?.parent ?: return emptyList()
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return emptyList()
        val bases = listOf(baseDir(own), own).distinct()
        if (!path.startsWith("/")) return bases.firstNotNullOfOrNull { b -> match(b, segments).ifEmpty { null } }.orEmpty()
        for (drop in segments.indices) {
            val tail = segments.drop(drop)
            bases.firstNotNullOfOrNull { b -> match(b, tail).ifEmpty { null } }?.let { return it }
        }
        return emptyList()
    }

    fun baseDir(dir: VirtualFile): VirtualFile =
        generateSequence(dir) { it.parent }.firstOrNull { it.findChild("nginx.conf") != null } ?: dir

    private fun match(dir: VirtualFile, segments: List<String>): List<VirtualFile> {
        if (segments.isEmpty()) return listOf(dir)
        val seg = segments[0]
        val rest = segments.drop(1)
        return when {
            seg == "." -> match(dir, rest)
            seg == ".." -> dir.parent?.let { match(it, rest) }.orEmpty()
            isGlob(seg) -> {
                val re = Regex(seg.map { if (it == '*') "[^/]*" else if (it == '?') "." else if (it in "[]") "$it" else Regex.escape("$it") }.joinToString(""))
                dir.children.filter { re.matches(it.name) }.sortedBy { it.name }.flatMap { match(it, rest) }
            }
            else -> dir.findChild(seg)?.let { match(it, rest) }.orEmpty()
        }
    }
}
