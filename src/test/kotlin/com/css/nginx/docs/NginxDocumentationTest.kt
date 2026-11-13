package com.css.nginx.docs

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxDocumentationTest : BasePlatformTestCase() {
    fun testQuickDoc() {
        myFixture.configureByText("nginx.conf", "http { gzip on; server { return 200 ${'$'}http_user_agent; } }")
        val provider = NginxDocumentationProvider()
        val gzip = myFixture.file.findElementAt(myFixture.file.text.indexOf("gzip"))
        assertTrue(provider.generateDoc(gzip, gzip)!!.contains("Enables or disables gzipping"))
        val prefixVar = myFixture.file.findElementAt(myFixture.file.text.indexOf("${'$'}http_"))
        assertTrue(provider.generateDoc(prefixVar, prefixVar)!!.contains("arbitrary request header field"))
    }

    fun testSyntaxFormsAreSeparateLines() {
        myFixture.configureByText("nginx.conf", "http { server { location / { proxy_pass http://a; } } }")
        val provider = NginxDocumentationProvider()
        fun doc(word: String) = myFixture.file.findElementAt(myFixture.file.text.indexOf(word)).let { provider.generateDoc(it, it)!! }
        val location = doc("location")
        assertTrue(location, "<code><b>location</b> [ <code>=</code> | <code>~</code> | <code>~*</code> | <code>^~</code> ] <i>uri</i> { … }</code><br>" +
            "<code><b>location</b> <i>${'$'}variable</i> { … }</code><br><code><b>location</b> <code>@</code><i>name</i> { … }</code>" in location)
        assertFalse(location, "<pre>" in location.substringBefore("</div>"))  // the syntax must wrap
        assertFalse(location, "Default:" in location)  // location has none
        val proxyPass = doc("proxy_pass")
        assertTrue(proxyPass, "<pre>proxy_pass http://localhost:8000/uri/;</pre>" in proxyPass)  // examples stay code blocks
        assertTrue(proxyPass, "<code><b>proxy_pass</b> <i>URL</i>;</code><br><code><b>proxy_pass</b> <i>address</i>;</code>" in proxyPass)
    }

    fun testParameterDocs() {
        val text = "http { proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=static:10m;\n" +
            "upstream u { server 10.0.0.1 weight=5 backup; }\n" +
            "server { listen 443 ssl; location / { try_files ${'$'}uri =404; } } }"
        myFixture.configureByText("nginx.conf", text)
        val provider = NginxDocumentationProvider()
        fun doc(word: String): String? {
            val leaf = myFixture.file.findElementAt(text.indexOf(word))
            val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, text.indexOf(word))
            return target?.let { provider.generateDoc(it, it) }
        }
        assertTrue(doc("levels")!!.contains("The <code>levels</code> parameter defines hierarchy levels of a cache"))
        assertTrue(doc("keys_zone")!!.contains("shared memory zone"))
        assertTrue(doc("weight")!!.contains("sets the weight of the server"))
        assertTrue(doc("backup")!!.contains("backup server"))
        assertTrue(doc("ssl")!!.contains("SSL mode"))
        assertNull(doc("=404"))
        assertNull(doc("/var/cache/nginx"))
    }

    fun testHeaderDocs() {
        val text = "http { server { location / { proxy_set_header X-Forwarded-For ${'$'}proxy_add_x_forwarded_for;\n" +
            "add_header strict-transport-security max-age=1 always; return 200 ${'$'}http_user_agent; } } }"
        myFixture.configureByText("nginx.conf", text)
        val provider = NginxDocumentationProvider()
        fun doc(word: String): String? {
            val at = text.indexOf(word)
            val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, myFixture.file.findElementAt(at), at)
            return target?.let { provider.generateDoc(it, it) }
        }
        assertTrue(doc("X-Forwarded-For")!!.contains("Client address followed by each proxy"))
        assertTrue(doc("strict-transport-security")!!.contains("HSTS"))  // header names are case-insensitive
        assertTrue(doc("${'$'}http_user_agent")!!.contains("Identifies the client software"))
        assertNull(doc("max-age=1"))  // a header value, not a name
        myFixture.configureByText("nginx.conf", "http { proxy_ignore_headers X-Accel-Redirect Expires; }")
        val at = myFixture.file.text.indexOf("Expires")
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, myFixture.file.findElementAt(at), at)
        assertTrue(provider.generateDoc(target, target)!!.contains("stale"))  // every *_ignore_headers argument is a header
    }
}
