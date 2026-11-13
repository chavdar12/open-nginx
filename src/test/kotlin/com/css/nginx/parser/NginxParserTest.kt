package com.css.nginx.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxParserTest : BasePlatformTestCase() {
    fun testMissingSemicolon() {
        myFixture.configureByText("nginx.conf", "http { gzip on<error descr=\"';' or '{' expected\"> </error>}")
        myFixture.checkHighlighting()
    }

    fun testUnbalancedBraces() {
        myFixture.configureByText(
            "nginx.conf",
            "events {}\nhttp { server {}<EOLError descr=\"'}' expected\"></EOLError>"
        )
        myFixture.checkHighlighting()
    }
}
