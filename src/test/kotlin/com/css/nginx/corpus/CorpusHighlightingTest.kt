package com.css.nginx.corpus

import com.intellij.lang.annotation.HighlightSeverity
import java.io.File

/** Real-world configs must come out clean; hand-written ones carry expected problems as <error>/<warning> markup. */
class CorpusHighlightingTest : NginxCorpusTestCase() {
    private val ADVICE = listOf("Security: ", "deprecated", "obsolete", "SPDY")

    /** 500+ configs from nginx's own test suite (tools/extract_nginx_tests.py), covering every module. */
    fun testNginxTestsCorpusHasNoFalsePositives() {
        val files = File(testDataPath, "nginx-tests").listFiles { f -> f.extension == "conf" }!!.sorted()
        assertTrue(files.size > 400)
        val problems = files.flatMap { f ->
            myFixture.configureByText("nginx.conf", f.readText())
            val doc = myFixture.editor.document
            // advice (security, deprecations) is not a false positive: nginx's tests use weak TLS, $uri in headers... on purpose
            myFixture.doHighlighting(HighlightSeverity.WARNING).filter { ADVICE.none(it.description::contains) }.map {
                "${f.name}:${doc.getLineNumber(it.startOffset) + 1}: ${it.description}  [${
                    doc.text.substring(
                        it.startOffset,
                        it.endOffset
                    )
                }]"
            }
        }
        assertTrue("${problems.size} problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    fun testHandWrittenConfigs() {
        for (f in File(testDataPath, "configs").listFiles { f -> f.extension == "conf" }!!.sorted()) {
            myFixture.configureByText("nginx.conf", f.readText())
            try {
                myFixture.checkHighlighting()
            } catch (e: Throwable) {
                throw AssertionError("${f.name}: ${e.message}", e)
            }
        }
    }
}
