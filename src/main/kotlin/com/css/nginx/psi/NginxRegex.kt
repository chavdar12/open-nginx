package com.css.nginx.psi

import com.css.nginx.NginxConstants.MATCH_OPERATORS
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/** Where a directive holds a regular expression: the token and the regex's range inside it. */
object NginxRegex {

    fun of(d: NginxDirective): List<Pair<PsiElement, TextRange>> {
        val args = d.args
        fun text(a: List<PsiElement>) = a.joinToString("") { it.text }
        val tokens: List<Pair<PsiElement, Int>> = when (d.directiveName) {  // token, chars to skip at its start
            "location" -> if (args.size == 2 && text(args[0]) in setOf("~", "~*")) listOfNotNull(
                args[1].singleOrNull()?.let { it to 0 }) else emptyList()

            "rewrite" -> listOfNotNull(args.firstOrNull()?.singleOrNull()?.let { it to 0 })
            "if" -> args.zipWithNext().filter { text(it.first) in MATCH_OPERATORS }.map { it.second.first() to 0 }
            "server_name" -> args.mapNotNull { a ->
                a.singleOrNull()?.takeIf { it.text.startsWith("~") }?.let { it to 1 }
            }

            else -> {
                // map / split keys: `~^/api 1;` inside map { }
                val parent = d.parent?.parent as? NginxDirective
                val key = d.nameElement.text
                if (parent?.directiveName == "map" && key.startsWith("~")) listOf(d.nameElement to if (key.startsWith("~*")) 2 else 1) else emptyList()
            }
        }
        return tokens.mapNotNull { (t, skip) ->
            var range = TextRange(skip, t.textLength)
            if (t.node.elementType == NginxTypes.STRING && t.textLength >= 2 + skip) range =
                TextRange(skip + 1, t.textLength - 1)
            // `if ($uri ~ ^/x)`: the closing paren is glued to the regex
            if (d.directiveName == "if" && t.text.endsWith(")") && t.node.elementType != NginxTypes.STRING) range =
                TextRange(range.startOffset, range.endOffset - 1)
            (t to range).takeIf { !range.isEmpty }
        }
    }
}
