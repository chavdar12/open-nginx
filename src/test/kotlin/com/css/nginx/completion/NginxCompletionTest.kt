package com.css.nginx.completion

import com.css.nginx.inspection.problemTooltip
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxCompletionTest : BasePlatformTestCase() {
    fun testCompletionIsContextAware() {
        myFixture.configureByText("nginx.conf", "events { <caret> }")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings!!
        assertTrue("worker_connections" in items)
        assertFalse("listen" in items)
    }

    fun testValueCompletion() {
        myFixture.configureByText("nginx.conf", "http { gzip <caret> }")
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, "on", "off")
    }

    fun testBuiltinVariableCompletion() {
        myFixture.configureByText("nginx.conf", "http { server { return 200 ${'$'}request_m<caret>; } }")
        myFixture.completeBasic()
        myFixture.checkResult("http { server { return 200 ${'$'}request_method<caret>; } }")
    }

    fun testUserVariableCompletionInsideStringAndBraces() {
        myFixture.configureByText(
            "nginx.conf",
            "http { server { set ${'$'}my_var 1; return 200 \"${'$'}{my_v<caret>}\"; } }"
        )
        myFixture.completeBasic()
        myFixture.checkResult("http { server { set ${'$'}my_var 1; return 200 \"${'$'}{my_var<caret>}\"; } }")
    }

    fun testHeaderNameCompletion() {
        myFixture.configureByText("nginx.conf", "http { server { location / { proxy_set_header X-Forw<caret> } } }")
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto")
    }

    fun testDirectiveLookupShowsModuleAndObsolete() {
        myFixture.configureByText("nginx.conf", "http { <caret> }")
        myFixture.completeBasic()
        fun look(name: String) =
            LookupElementPresentation.renderElement(myFixture.lookupElements!!.first { it.lookupString == name })
        assertEquals("http_gzip", look("gzip_comp_level").typeText)
        assertEquals("headers-more", look("more_set_headers").typeText)
        assertTrue(look("ssl").isStrikeout)  // obsolete since 1.25.1
        assertFalse(look("gzip").isStrikeout)
    }

    fun testProblemTooltipFormatsNames() {
        assertEquals(
            "<html><b>Security</b>: <code>X-A</code> isn&#39;t &lt;sent&gt; from <code>server</code></html>",
            problemTooltip("Security: 'X-A' isn't <sent> from 'server'")
        )
    }

}
