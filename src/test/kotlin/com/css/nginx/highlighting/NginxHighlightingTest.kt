package com.css.nginx.highlighting

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxHighlightingTest : BasePlatformTestCase() {
    fun testVariablesInsideStringsAreHighlighted() {
        myFixture.configureByText("nginx.conf", "http { server { return 200 \"${'$'}host and ${'$'}{uri}\"; } }")
        val vars = myFixture.doHighlighting().filter { it.forcedTextAttributesKey == NginxColors.VARIABLE }
        assertSameElements(
            vars.map { myFixture.editor.document.text.substring(it.startOffset, it.endOffset) },
            "${'$'}host",
            "${'$'}{uri}"
        )
    }

    fun testParameterNamesAreHighlighted() {
        val text = "http { proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=static:10m use_temp_path=off;\n" +
                "server { location / { try_files ${'$'}uri =404; return 301 /?a=b; set ${'$'}x ${'$'}y=z; } } }"
        myFixture.configureByText("nginx.conf", text)
        val params = myFixture.doHighlighting().filter { it.forcedTextAttributesKey == NginxColors.PARAMETER }
        assertSameElements(
            params.map { text.substring(it.startOffset, it.endOffset) },
            "levels",
            "keys_zone",
            "use_temp_path"
        )
    }
}
