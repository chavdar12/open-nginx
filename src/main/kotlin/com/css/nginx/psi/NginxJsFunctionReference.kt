package com.css.nginx.psi

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil

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
