package com.css.nginx.inspection

import com.css.nginx.highlighting.NginxColors
import com.css.nginx.lang.NginxFileType
import com.css.nginx.psi.NginxDirective
import com.css.nginx.psi.NginxTypes
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Third-party modules, Lua bodies, regexes, docker templates and live templates. */
class NginxModulesTest : BasePlatformTestCase() {
    private val d = "$"

    fun testLuaBlockIsOneTokenAndSurvivesFormatting() {
        val lua = "local t = {a = 1}\n        ngx.say(\"} {\") -- } not the end\n        ngx.say([[ } ]])"
        val text =
            "http {\n    server {\n        location / {\n            set_by_lua_block ${d}x { return 1 }\n            content_by_lua_block {\n        $lua\n            }\n            gzip on;\n        }\n    }\n}\n"
        myFixture.configureByText("nginx.conf", text)
        assertNull(PsiTreeUtil.findChildOfType(myFixture.file, PsiErrorElement::class.java))
        val names = PsiTreeUtil.findChildrenOfType(myFixture.file, NginxDirective::class.java).map { it.directiveName }
        assertEquals(listOf("http", "server", "location", "set_by_lua_block", "content_by_lua_block", "gzip"), names)
        val luaTokens =
            PsiTreeUtil.collectElements(myFixture.file) { it.node.elementType == NginxTypes.LUA }.map { it.text }
        assertEquals(listOf("return 1", lua), luaTokens)
        myFixture.checkHighlighting(true, false, true)  // no unknown directives, no unknown $x
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        assertTrue(myFixture.editor.document.text, lua in myFixture.editor.document.text)
    }

    fun testLuaIsInjectedWhenALuaPluginIsInstalled() {
        myFixture.configureByText(
            "nginx.conf", "http { server { location / {\n" +
                    "content_by_lua_block { local n = 1\n ngx.say(n) }\n set_by_lua ${d}y 'return 2'; } } }"
        )
        val hosts = PsiTreeUtil.collectElements(myFixture.file) { it is PsiLanguageInjectionHost }
        val injected = hosts.flatMap { InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(it).orEmpty() }
            .map { it.first }
        assertEquals(listOf("Lua", "Lua"), injected.map { it.language.id })
        assertEquals(listOf("local n = 1\n ngx.say(n)", "return 2"), injected.map { it.text })
        myFixture.checkHighlighting(true, false, true)
    }

    fun testThirdPartyDirectivesAndNjsImport() {
        myFixture.addFileToProject("js/main.js", "function hello(r) {}")
        myFixture.configureByText(
            "nginx.conf",
            "events { }\nhttp { brotli on; brotli_comp_level 6; js_import main from js/main.js;\n" +
                    "server { location / { more_set_headers 'X-A: b'; brotli <error descr=\"Invalid value 'maybe' for 'brotli', expected on | off\">maybe</error>; } } }",
        )
        myFixture.checkHighlighting(true, false, true)
        val js = PsiTreeUtil.findChildrenOfType(myFixture.file, NginxDirective::class.java)
            .first { it.directiveName == "js_import" }
        assertEquals("main.js", (js.references.single().resolve() as com.intellij.psi.PsiFile).name)
    }

    fun testRegexColorsValidityAndRedos() {
        myFixture.configureByText(
            "nginx.conf",
            "events { }\nhttp { server {\n" +
                    "location ~ \\.php$ { }\n" +
                    "location ~ \"<warning descr=\"Invalid regular expression: Unclosed group\">^/(a</warning>\" { }\n" +
                    "location ~ <warning descr=\"Security: ReDoS: a repeated group that itself repeats ('^/(a+)+$') can take exponential time on a crafted URL\">^/(a+)+$</warning> { }\n" +
                    "} }",
        )
        myFixture.checkHighlighting(true, false, true)
        val escapes =
            myFixture.doHighlighting().filter { it.forcedTextAttributesKey == NginxColors.REGEX_ESCAPE }.map { it.text }
        assertTrue(escapes.toString(), "\\." in escapes)
    }

    fun testDockerTemplate() {
        val file = myFixture.configureByText(
            "default.conf.template",
            "server { listen ${d}{NGINX_PORT}; server_name ${d}{NGINX_HOST}; return 200 <warning descr=\"Unknown variable '${d}lower': not built in and not declared by set, map, geo... in this project\">${d}{lower}</warning>; }"
        )
        assertEquals(NginxFileType, file.fileType)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testLiveTemplate() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByText("nginx.conf", "http {\n    server {\n        ws<caret>\n    }\n}\n")
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
        val text = myFixture.editor.document.text
        assertTrue(text, "proxy_set_header Upgrade ${d}http_upgrade;" in text && "location /ws/ {" in text)
    }
}
