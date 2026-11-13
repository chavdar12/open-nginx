package com.css.nginx.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxUpstreamCheckTest : BasePlatformTestCase() {
    private fun warn(name: String) =
        "<weak_warning descr=\"Unknown upstream '$name': no upstream block with this name, so nginx will resolve '$name' as a host name\">"

    fun testUnknownUpstreamNamesAreFlagged() {
        // an upstream declared in another nginx file of the project counts too (includes)
        myFixture.addFileToProject("conf.d/upstreams.nginx", "upstream shared { server 10.0.0.9; }")
        myFixture.configureByText(
            "nginx.conf", """
            events {}
            http {
                upstream app { server 127.0.0.1:8080; }
                server {
                    listen 80;
                    location /a { proxy_pass http://app; }
                    location /b { proxy_pass http://app/x/; }
                    location /c { proxy_pass http://shared; }
                    location /d { proxy_pass ${warn("ap")}http://ap</weak_warning>; }
                    location /e { proxy_pass http://backend:8080; }
                    location /f { proxy_pass http://example.com; }
                    location /g { proxy_pass http://localhost; }
                    location /h { proxy_pass http://127.0.0.1; }
                    location /i { proxy_pass http://${'$'}host; }
                    location /j { grpc_pass ${warn("grpcbackend")}grpcbackend</weak_warning>; }
                }
            }
            stream {
                upstream db { server 10.0.0.1:3306; }
                server { listen 3306; proxy_pass db; }
                server { listen 3307; proxy_pass ${warn("dbx")}dbx</weak_warning>; }
            }
            """.trimIndent()
        )
        myFixture.checkHighlighting(false, false, true)
    }
}
