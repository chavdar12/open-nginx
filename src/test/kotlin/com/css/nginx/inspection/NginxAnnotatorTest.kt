package com.css.nginx.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxAnnotatorTest : BasePlatformTestCase() {
    fun testParsingAndChecks() {
        myFixture.configureByText(
            "nginx.conf", """
            worker_processes auto; # comment
            http {
                <warning descr="Unknown directive 'gzpi'">gzpi</warning> on;
                server {
                    listen 80;
                    location ~ \.php$ { return 200 "${'$'}uri ok"; }
                    <error descr="'worker_processes' is not allowed in 'server'">worker_processes</error> 1;
                }
                map ${'$'}host ${'$'}backend { default a; ~^www b; }
            }
            """.trimIndent()
        )
        myFixture.checkHighlighting()
    }

    fun testIncludedSnippetSkipsContextCheck() {
        myFixture.configureByText("site.nginx", "listen 80; server_name example.com;")
        myFixture.checkHighlighting()
    }
}
