package com.css.nginx.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Each line: the config, and the start of the expected warning (null = must stay clean). */
class NginxSecurityTest : BasePlatformTestCase() {
    private val d = "$"

    private fun check(config: String, expected: String?) {
        myFixture.configureByText("nginx.conf", "events { }\nhttp {\n$config\n}")
        val found = myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("Security") || "duplicate" in it || "Conflicting" in it || "deprecated" in it || "obsolete" in it }
        if (expected == null) assertEmpty(config, found) else assertTrue("$config\n$found", found.any { it.startsWith(expected) })
    }

    fun testChecks() {
        check("server { location /img { alias /data/img/; } }", "Security: path traversal")
        check("server { location /img/ { alias /data/img/; } }", null)
        check("server { add_header X-Frame-Options DENY; location / { add_header X-A 1; } }", "Security: X-Frame-Options from 'server' is not sent here")
        check("server { add_header X-Frame-Options DENY; location / { add_header_inherit merge; add_header X-A 1; } }", null)
        check("server { location / { proxy_set_header Host ${d}http_host; proxy_pass http://a; } }", "Security: Host spoofing")
        check("server { location / { add_header X-Path ${d}uri; } }", "Security: HTTP splitting")
        check("server { location / { return 302 https://a.com${d}uri; } }", "Security: HTTP splitting")
        check("server { location / { return 200 \"${d}uri\"; } }", null)
        check("server { location / { if (${d}http_origin ~ \"^https://example.com$\") { } } }", "Security: origin check")
        check("server { location / { if (${d}http_origin ~ \"^https://example\\.com$\") { } } }", null)
        check("map ${d}http_origin ${d}cors { ~^https://a\\.com 1; }", "Security: origin check")
        check("server { location / { add_header Access-Control-Allow-Origin ${d}http_origin; } }", "Security: CORS")
        check("server { ssl_protocols TLSv1 TLSv1.2; }", "Security: TLSv1 is insecure")
        check("server { ssl_ciphers HIGH:!aNULL:!MD5:RC4-SHA; }", "Security: weak ciphers enabled: RC4-SHA")
        check("server { ssl_ciphers HIGH:!aNULL:!MD5; }", null)
        check("server { location / { proxy_pass http://${d}arg_target; } }", "Security: SSRF")
        check("server { location / { proxy_pass http://backend/${d}arg_x; } }", null)
        check("server { server_tokens on; }", "Security: reveals")
    }

    fun testDuplicatesAndDeprecations() {
        check("server { listen 80 default_server; } server { listen 80 default_server; }", "A duplicate default server for *:80")
        check("server { listen 80; server_name a.com; } server { listen 80; server_name a.com; }", "Conflicting server name 'a.com' on *:80")
        check("server { listen 80; server_name a.com; } server { listen 443; server_name a.com; }", null)
        check("server { listen 443 ssl http2; }", "The 'http2' parameter is deprecated")
        check("server { ssl on; }", "'ssl' is obsolete")
        check("server { root /a; location / { root /b; } }", null)
    }
}
