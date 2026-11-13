package com.css.nginx.navigation

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxGotoUpstreamTest : BasePlatformTestCase() {
    private fun gotoFrom(config: String): Pair<String, Int> {
        myFixture.configureByText("nginx.conf", config)
        myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        return editor.virtualFile!!.name to editor.caretModel.offset
    }

    fun testSameFile() {
        val text =
            "http { upstream app { server 127.0.0.1; } server { location / { proxy_pass http://a<caret>pp/x/; } } }"
        val (file, offset) = gotoFrom(text)
        assertEquals("nginx.conf", file)
        assertEquals(text.indexOf("upstream app") + "upstream ".length, offset)
    }

    fun testOtherFileAndGrpc() {
        myFixture.addFileToProject("conf.d/upstreams.nginx", "upstream grpcback { server 10.0.0.1:9000; }")
        val (file, offset) = gotoFrom("http { server { location / { grpc_pass grpc://grpc<caret>back; } } }")
        assertEquals("upstreams.nginx", file)
        assertEquals("upstream ".length, offset)
    }

    fun testStreamBareName() {
        val text = "stream { upstream db { server 10.0.0.1:3306; } server { listen 1; proxy_pass d<caret>b; } }"
        val (_, offset) = gotoFrom(text)
        assertEquals(text.indexOf("upstream db") + "upstream ".length, offset)
    }

    fun testHostWithPortGoesNowhere() {
        val text =
            "http { upstream app { server 127.0.0.1; } server { location / { proxy_pass http://a<caret>pp:8080; } } }"
        val (_, offset) = gotoFrom(text)
        assertEquals(
            text.replace("<caret>", "").indexOf("a" + "pp:8080") + 1,
            offset
        )  // caret unchanged: app:8080 is a host, not the upstream
    }
}

class NginxFindUpstreamUsagesTest : BasePlatformTestCase() {
    fun testUsagesAcrossFiles() {
        myFixture.addFileToProject(
            "conf.d/site.nginx",
            "server { location / { proxy_pass http://app/; } location /b { proxy_pass http://app:8080; } }"
        )
        myFixture.configureByText(
            "nginx.conf",
            "http { upstream a<caret>pp { server 127.0.0.1; } server { location / { proxy_pass http://app; } } }"
        )
        val usages = myFixture.findUsages(myFixture.elementAtCaret)
        // app:8080 is a host, not the upstream
        assertEquals(listOf("nginx.conf", "site.nginx"), usages.map { it.file!!.name }.sorted())
    }
}

class NginxRenameUpstreamTest : BasePlatformTestCase() {
    fun testRenameFromDeclarationAndFromUsage() {
        val site = myFixture.addFileToProject(
            "conf.d/site.nginx",
            "server { location / { proxy_pass \"http://app/x\"; } location /b { proxy_pass http://app:8080; } }"
        )
        myFixture.configureByText(
            "nginx.conf",
            "http { upstream a<caret>pp { server 127.0.0.1; } server { location / { proxy_pass http://app/; } } }"
        )
        myFixture.renameElementAtCaret("backend")
        myFixture.checkResult("http { upstream backend { server 127.0.0.1; } server { location / { proxy_pass http://backend/; } } }")
        // the quoted URL follows; app:8080 is a host, not the upstream, so it stays
        assertEquals(
            "server { location / { proxy_pass \"http://backend/x\"; } location /b { proxy_pass http://app:8080; } }",
            site.text
        )

        myFixture.configureByText(
            "stream.nginx",
            "upstream db { server 10.0.0.1:3306; } server { listen 1; proxy_pass d<caret>b; }"
        )
        myFixture.renameElementAtCaret("mysql")
        myFixture.checkResult("upstream mysql { server 10.0.0.1:3306; } server { listen 1; proxy_pass mysql; }")
    }
}
