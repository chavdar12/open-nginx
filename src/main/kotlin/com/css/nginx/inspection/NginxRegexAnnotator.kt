package com.css.nginx.inspection

import com.css.nginx.highlighting.NginxColors
import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxRegex
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Colors regexes in location ~, rewrite, if, server_name ~ and map keys, and flags ones that do not compile. */
class NginxRegexAnnotator : Annotator {
    override fun annotate(e: PsiElement, holder: AnnotationHolder) {
        val d = e as? NginxDirective ?: return
        for ((token, range) in NginxRegex.of(d)) {
            val re = range.substring(token.text)
            val base = token.textRange.startOffset + range.startOffset
            fun color(from: Int, len: Int, key: com.intellij.openapi.editor.colors.TextAttributesKey) =
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(TextRange.from(base + from, len))
                    .textAttributes(key).create()

            var i = 0
            var inClass = false
            while (i < re.length) {
                val c = re[i]
                when {
                    c == '\\' && i + 1 < re.length -> {
                        color(i, 2, NginxColors.REGEX_ESCAPE); i += 2; continue
                    }

                    inClass -> if (c == ']') {
                        color(i, 1, NginxColors.REGEX_META); inClass = false
                    }

                    c == '[' -> {
                        color(i, 1, NginxColors.REGEX_META); inClass = true
                    }

                    c in META -> color(i, 1, NginxColors.REGEX_META)
                }
                i++
            }
            // shortcut: Java's regex engine stands in for PCRE (same syntax for what configs use); PCRE-only constructs are skipped
            if (PCRE_ONLY.containsMatchIn(re)) continue
            try {
                Pattern.compile(re.replace("(?P<", "(?<"))
            } catch (ex: PatternSyntaxException) {
                holder.problem(HighlightSeverity.WARNING, "Invalid regular expression: ${ex.description}")
                    .range(TextRange.from(base, re.length)).create()
            }
        }
    }
}

private const val META = "^$|()*+?.{}"
private val PCRE_ONLY = Regex("""\(\?\||\[\[:|\\K|\(\*|\(\?R\)|\(\?\d""")
