package com.css.nginx.inspection

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxConfigTestAnnotatorTest : BasePlatformTestCase() {
    fun testNginxTestOutputParsing() {
        val stderr = """
            nginx: [emerg] unknown directive "gzpi" in /etc/nginx/nginx.conf:12
            nginx: [warn] the "ssl" directive is deprecated in /etc/nginx/nginx.conf:30
            nginx: [emerg] invalid number of arguments in "listen" directive in /etc/nginx/conf.d/site.conf:3
            nginx: [emerg] open() "/var/log/nginx/error.log" failed (13: Permission denied)
            nginx: configuration file /etc/nginx/nginx.conf test failed
        """.trimIndent()
        assertEquals(
            listOf(
                NginxConfigTestAnnotator.Problem(12, HighlightSeverity.ERROR, "unknown directive \"gzpi\""),
                NginxConfigTestAnnotator.Problem(30, HighlightSeverity.WARNING, "the \"ssl\" directive is deprecated"),
            ),
            parseNginxTestOutput(stderr, "/etc/nginx/nginx.conf"),
        )
    }
}
