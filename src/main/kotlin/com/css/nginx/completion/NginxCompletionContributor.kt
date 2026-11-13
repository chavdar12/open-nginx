package com.css.nginx.completion

import com.css.nginx.data.NginxDirectives
import com.css.nginx.data.NginxHeaders
import com.css.nginx.data.NginxMimeTypes
import com.css.nginx.lang.NginxLanguage
import com.css.nginx.psi.NginxDeclarations
import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxPaths
import com.css.nginx.psi.NginxTypes
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext

/**
 * Directive names for the current block, values, headers, MIME types, and project-wide names: variables, upstreams,
 * named locations and file paths declared or present in any file of the project.
 */
class NginxCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(NginxLanguage),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    p: CompletionParameters,
                    c: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val leaf = p.position
                    val beforeCaret =
                        leaf.text.substring(0, (p.offset - leaf.textRange.startOffset).coerceIn(0, leaf.textLength))
                    val varPrefix = Regex("""\$\{?(\w*)$""").find(beforeCaret)
                    val d = leaf.parent as? NginxDirective
                    val type = leaf.node.elementType
                    when {
                        varPrefix != null -> completeVariables(
                            p.originalFile,
                            result.withPrefixMatcher(varPrefix.groupValues[1])
                        )

                        d == null -> return
                        type == NginxTypes.NAME && NginxDirectives.contextOf(d) == "types" -> mimeTypes(result)
                        type == NginxTypes.NAME -> completeDirectives(d, result)
                        type != NginxTypes.WORD -> return
                        NginxHeaders.isHeaderName(leaf) -> NginxHeaders.all.values.forEach {
                            result.addElement(
                                LookupElementBuilder.create(it.name).withCaseSensitivity(false).withTypeText("header")
                            )
                        }

                        d.directiveName == "default_type" -> mimeTypes(result)
                        beforeCaret.startsWith("@") -> names(
                            p.originalFile,
                            result.withPrefixMatcher(beforeCaret),
                            '@',
                            "named location"
                        )

                        d.directiveName in PASS -> names(p.originalFile, result, null, "upstream")
                        NginxPaths.pathArg(d) != null && leaf in NginxPaths.pathArg(d)!! ->
                            completePaths(
                                p.originalFile,
                                d.directiveName in NginxPaths.DIRECTORIES,
                                result.withPrefixMatcher(beforeCaret)
                            )

                        else -> NginxDirectives.all[d.directiveName]?.values?.forEach {
                            result.addElement(
                                LookupElementBuilder.create(it)
                            )
                        }
                    }
                }
            })
    }

    // a reference (variable, upstream, path) would make the platform replace up to its end, e.g. the `}` of ${var}, and
    // treat the caret as mid-word (no auto-insert of a single match); setting the default end explicitly keeps word behavior
    override fun beforeCompletion(context: CompletionInitializationContext) {
        context.offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, context.identifierEndOffset)
    }

    private fun completeDirectives(d: NginxDirective, result: CompletionResultSet) {
        val ctx = NginxDirectives.contextOf(d)
        if (ctx != null && ctx !in NginxDirectives.contexts) return
        for (info in NginxDirectives.all.values) {
            if (ctx == null || info.contexts.isEmpty() || ctx in info.contexts)
                result.addElement(
                    LookupElementBuilder.create(info.name).withTailText(" " + info.plainSyntax, true)
                        .withTypeText(info.module)
                        .withStrikeoutness(info.name in NginxDirectives.obsolete),
                )
        }
    }

    private fun mimeTypes(result: CompletionResultSet) =
        NginxMimeTypes.all.forEach { result.addElement(LookupElementBuilder.create(it)) }

    private fun completeVariables(file: PsiFile, result: CompletionResultSet) {
        for (v in NginxDirectives.variables.values) {
            result.addElement(
                LookupElementBuilder.create(v.name).withPresentableText("$" + v.name).withTypeText("built-in")
            )
        }
        val index = NginxDeclarations.index(file) ?: return
        for ((key, decls) in index.byName) if (key.startsWith("$")) {
            val name = key.substring(1)
            result.addElement(
                LookupElementBuilder.create(name).withPresentableText(key)
                    .withTypeText(decls.first().containingFile.name)
            )
        }
        index.captures.forEach {
            result.addElement(
                LookupElementBuilder.create(it).withPresentableText("$$it").withTypeText("regex capture")
            )
        }
    }

    /** Upstreams (prefix null) or @named locations from every file of the project. */
    private fun names(file: PsiFile, result: CompletionResultSet, prefix: Char?, kind: String) {
        val all = NginxDeclarations.declarations(file) ?: return
        for ((key, decls) in all) {
            if (prefix == null && (key[0] == '$' || key[0] == '@') || prefix != null && key[0] != prefix) continue
            result.addElement(
                LookupElementBuilder.create(key).withTypeText("$kind · ${decls.first().containingFile.name}")
            )
        }
    }

    /** Paths relative to the nginx conf folder (where nginx.conf is), as nginx resolves them. */
    private fun completePaths(file: PsiFile, dirsOnly: Boolean, result: CompletionResultSet) {
        val base = file.originalFile.virtualFile?.parent?.let(NginxPaths::baseDir) ?: return
        var count = 0
        VfsUtilCore.iterateChildrenRecursively(
            base,
            { !it.name.startsWith(".") && it.name != "node_modules" }) { f: VirtualFile ->
            if (f != base && (!dirsOnly || f.isDirectory)) {
                val rel = VfsUtilCore.getRelativePath(f, base) ?: return@iterateChildrenRecursively true
                result.addElement(
                    LookupElementBuilder.create(rel)
                        .withIcon(if (f.isDirectory) AllIcons.Nodes.Folder else AllIcons.FileTypes.Any_type)
                )
            }
            ++count < 2000  // shortcut: stops after 2000 entries, enough for config folders; index by name if users have huge trees
        }
    }
}

private val PASS = setOf("proxy_pass", "grpc_pass", "uwsgi_pass")
