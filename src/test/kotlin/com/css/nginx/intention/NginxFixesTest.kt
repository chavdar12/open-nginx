package com.css.nginx.intention

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxFixesTest : BasePlatformTestCase() {
    private val d = "$"

    private fun fix(before: String, action: String, after: String, file: String = "nginx.conf") {
        myFixture.configureByText(file, before)
        myFixture.launchAction(myFixture.findSingleIntention(action))
        assertEquals(after, myFixture.editor.document.text)
    }

    fun testQuickFixes() {
        fix("http { gzip y<caret>es; }", "Replace with 'on'", "http { gzip on; }")
        fix("http { proxy_buffring<caret> on; }", "Change to 'proxy_buffering'", "http { proxy_buffering on; }")
        fix("http { server { location / { proxy_pass ba<caret>ckend; } } }", "Add http://", "http { server { location / { proxy_pass http://backend; } } }")
        fix("http {\n    server {\n        root /a;\n        root<caret> /b;\n    }\n}", "Remove duplicate", "http {\n    server {\n        root /a;\n    }\n}")
        fix("events { }\nhttp {\n    upstream u {\n        server a;\n        gzi<caret>p on;\n    }\n}", "Move to the enclosing 'http' block",
            "events { }\nhttp {\n    gzip on;\n    upstream u {\n        server a;\n    }\n}")
    }

    fun testCustomDirectiveIsRemembered() {
        myFixture.configureByText("nginx.conf", "http { my_mod<caret>ule_flag on; }")
        myFixture.launchAction(myFixture.findSingleIntention("Add 'my_module_flag' to this project's known directives"))
        assertTrue(myFixture.doHighlighting().none { it.description?.contains("my_module_flag") == true })
    }

    fun testCopyInheritedHeaders() {
        fix(
            "server {\n    add_header Strict-Transport-Security \"max-age=31536000\" always;\n    location / {\n        add_<caret>header X-Cache 1;\n    }\n}",
            "Copy the add_header lines from 'server' into this block",
            "server {\n    add_header Strict-Transport-Security \"max-age=31536000\" always;\n    location / {\n        add_header Strict-Transport-Security \"max-age=31536000\" always;\n        add_header X-Cache 1;\n    }\n}",
        )
    }

    fun testCustomVariableIsRemembered() {
        myFixture.configureByText("nginx.conf", "http { server { return 200 \$vts_ser<caret>ver; } }")
        myFixture.launchAction(myFixture.findSingleIntention("Add '\$vts_server' to this project's known variables"))
        assertTrue(myFixture.doHighlighting().none { it.description?.contains("vts_server") == true })
    }

    fun testUnknownModuleDeclaresVariable() {
        // auth_jwt_claim_set is NGINX Plus only: no data for it, but its first $argument is what it declares
        myFixture.configureByText("nginx.conf", "http { auth_jwt_claim_set \$sub sub; my_flag on; map \$sub \$u { default \$nope; } server { return 200 \$sub; } }")
        val unknown = myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("Unknown variable") }
        assertEquals(listOf("Unknown variable '\$nope': not built in and not declared by set, map, geo... in this project"), unknown)
    }

    fun testDuplicatesFromNginxSources() {
        myFixture.configureByText("nginx.conf", "http { gzip_comp_level 5; gzip_comp_level 6; ssl_protocols TLSv1.2; ssl_protocols TLSv1.3; " +
            "server { location / { limit_req zone=a; limit_req zone=b; return 200; return 301 /x; } } }")
        val dups = myFixture.doHighlighting().mapNotNull { it.description }.filter { it.endsWith("is duplicate") }
        assertEquals(listOf("'gzip_comp_level' directive is duplicate"), dups)
    }

    fun testSslRedirect() {
        fix(
            "http {\n    server {\n        listen 443 ssl;\n        server_name a.com b.com;<caret>\n    }\n}",
            "Add HTTP to HTTPS redirect server",
            "http {\n    server {\n        listen 443 ssl;\n        server_name a.com b.com;\n    }\n\n    server {\n        listen 80;\n        listen [::]:80;\n        server_name a.com b.com;\n        return 301 https://${d}host${d}request_uri;\n    }\n}",
        )
        myFixture.configureByText("nginx.conf", "http { server { listen 80; server_name a.com; } server { listen 443 ssl; server_name a.com;<caret> } }")
        assertEmpty(myFixture.filterAvailableIntentions("Add HTTP to HTTPS redirect server"))
    }

    fun testInlineAndExtractInclude() {
        myFixture.addFileToProject("snippets/tls.nginx", "ssl_protocols TLSv1.3;\nssl_session_cache shared:SSL:10m;\n")
        fix("http {\n    server {\n        include snippets/tl<caret>s.nginx;\n    }\n}", "Inline included file",
            "http {\n    server {\n        ssl_protocols TLSv1.3;\n        ssl_session_cache shared:SSL:10m;\n    }\n}")

        myFixture.configureByText("nginx.conf", "http {\n    server {\n        <selection>listen 80;\n        server_name a.com;<caret></selection>\n    }\n}")
        myFixture.launchAction(myFixture.findSingleIntention("Extract to include file"))
        assertEquals("http {\n    server {\n        include listen.conf;\n    }\n}", myFixture.editor.document.text)
        assertEquals("listen 80;\nserver_name a.com;\n", myFixture.findFileInTempDir("listen.conf").let { String(it.contentsToByteArray()) })
    }
}
