package com.css.nginx.corpus

import com.css.nginx.lexer.NginxLexer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/** Shared corpus: 500+ configs from nginx's own test suite plus hand-written ones (src/test/testData). */
abstract class NginxCorpusTestCase : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData"

    protected fun corpus() = File(testDataPath, "nginx-tests").listFiles { f -> f.extension == "conf" }!!.sorted() +
            File(testDataPath, "configs").listFiles { f -> f.extension == "conf" }!!.sorted()

    /** What nginx reads: tokens, whether whitespace separates them, and that every comment still ends its line. */
    protected fun tokens(text: String): List<String> {
        val lexer = NginxLexer().apply { start(text) }
        val out = mutableListOf<String>()
        while (lexer.tokenType != null) {
            out += if (lexer.tokenType == TokenType.WHITE_SPACE) {
                if (out.lastOrNull()
                        ?.startsWith("COMMENT") == true && '\n' !in lexer.tokenText
                ) "comment-joined" else " "
            } else "${lexer.tokenType}:${lexer.tokenText}"
            lexer.advance()
        }
        // whitespace at the file edges or next to ; { } means nothing to nginx
        val structural = setOf(";:;", "{:{", "}:}")
        return out.filterIndexed { i, t -> t != " " || (i > 0 && i < out.size - 1 && out[i - 1] !in structural && out[i + 1] !in structural) }
    }

    protected fun reformat(text: String): String {
        myFixture.configureByText("nginx.conf", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        return myFixture.file.text
    }

    private fun firstDifference(a: String, b: String): String {
        val i = a.indices.firstOrNull { it >= b.length || a[it] != b[it] } ?: a.length
        val show = { s: String -> s.substring(maxOf(0, i - 80), minOf(s.length, i + 80)).replace("\t", "→") }
        return "first pass:  ...${show(a)}...\nsecond pass: ...${show(b)}..."
    }

    protected fun checkFormattingIsSafe() {
        val markup = Regex("""</?(error|warning|EOLError)[^>]*>""")
        val problems = corpus().mapNotNull { f ->
            val original = f.readText().replace(markup, "")
            val once = reformat(original)
            when {
                tokens(once) != tokens(original) -> {
                    val (a, b) = tokens(original) to tokens(once)
                    val i = a.indices.firstOrNull { it >= b.size || a[it] != b[it] } ?: a.size
                    "${f.name}: formatting changed what nginx reads at token $i: ${
                        a.subList(
                            maxOf(0, i - 3),
                            minOf(a.size, i + 3)
                        )
                    } -> ${b.subList(maxOf(0, i - 3), minOf(b.size, i + 3))}"
                }

                reformat(once) != once -> "${f.name}: formatting is not idempotent:\n" + firstDifference(
                    once,
                    reformat(once)
                )

                else -> null
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
