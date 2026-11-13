package com.css.nginx.navigation

import com.css.nginx.editor.NginxFoldingBuilder
import com.css.nginx.structure.NginxStructureViewFactory
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxCrossFileTest : BasePlatformTestCase() {
    private val d = "$"

    fun testVariableRenameAcrossFilesKeepsBraces() {
        val vars = myFixture.addFileToProject("conf.d/vars.nginx", "map ${d}http_x ${d}tenant { default a; }")
        myFixture.configureByText("nginx.conf", "http { server { return 200 \"${d}{tena<caret>nt}-${d}tenant\"; } }")
        myFixture.renameElementAtCaret("${d}org")
        assertEquals("http { server { return 200 \"${d}{org}-${d}org\"; } }", myFixture.editor.document.text)
        assertEquals("map ${d}http_x ${d}org { default a; }", vars.text)
    }

    fun testNamedLocationUsages() {
        myFixture.addFileToProject("conf.d/site.nginx", "server { error_page 404 @fallback; }")
        myFixture.configureByText("nginx.conf", "http { server { try_files ${d}uri @fallback; location @fall<caret>back { return 404; } } }")
        assertEquals(2, myFixture.findUsages(myFixture.elementAtCaret).size)
    }

    fun testIncludeGlobResolvesToEveryMatch() {
        myFixture.addFileToProject("conf.d/a.nginx", "server { }")
        myFixture.addFileToProject("conf.d/b.nginx", "server { }")
        myFixture.configureByText("nginx.conf", "http { include conf.d/*.ng<caret>inx; }")
        val targets = (myFixture.getReferenceAtCaretPositionWithAssertion() as PsiPolyVariantReference).multiResolve(false)
        assertEquals(listOf("a.nginx", "b.nginx"), targets.map { (it.element as com.intellij.psi.PsiFile).name })
    }

    fun testMissingIncludeAndUnknownVariable() {
        myFixture.configureByText(
            "nginx.conf",
            "events { } http { include <weak_warning descr=\"Cannot find included file 'snippets/missing.conf'\">snippets/missing.conf</weak_warning>; include mime.types;\n" +
                "server { location ~ ^/(?<id>[0-9]+)$ { return 200 ${d}id; } location / { return 200 <warning descr=\"Unknown variable '${d}nope': not built in and not declared by set, map, geo... in this project\">${d}nope</warning>; } } }",
        )
        myFixture.checkHighlighting(true, false, true)
    }

    fun testSnippetGetsContextFromItsInclude() {
        myFixture.addFileToProject("nginx.conf", "events { } http { server { include snippets/tls.nginx; } }")
        myFixture.addFileToProject("snippets/tls.nginx", "worker_processes 1;\nlist")
        myFixture.configureFromTempProjectFile("snippets/tls.nginx")
        assertTrue(myFixture.doHighlighting().any { it.description == "'worker_processes' is not allowed in 'server'" })
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        val items = myFixture.completeBasic()?.map { it.lookupString }
        // "listen" is valid in server; "list" alone would auto-complete to it, so look at the list instead
        assertTrue((items == null && myFixture.editor.document.text.endsWith("listen")) || items!!.contains("listen"))
    }

    fun testCompletionFromOtherFiles() {
        myFixture.addFileToProject("conf.d/defs.nginx", "upstream backend { server 10.0.0.1; } map ${d}http_x ${d}tenant { } server { location @fallback { } }")
        myFixture.configureByText("nginx.conf", "http { server { location / { proxy_pass http://back<caret>; } } }")
        myFixture.completeBasic()
        myFixture.checkResult("http { server { location / { proxy_pass http://backend<caret>; } } }")
        myFixture.configureByText("nginx.conf", "http { server { return 200 ${d}tena<caret>; } }")
        myFixture.completeBasic()
        myFixture.checkResult("http { server { return 200 ${d}tenant<caret>; } }")
        myFixture.configureByText("nginx.conf", "http { server { try_files ${d}uri @fa<caret>; } }")
        myFixture.completeBasic()
        myFixture.checkResult("http { server { try_files ${d}uri @fallback<caret>; } }")
        myFixture.configureByText("nginx.conf", "http { include conf.d/de<caret>; }")
        myFixture.completeBasic()
        myFixture.checkResult("http { include conf.d/defs.nginx<caret>; }")
    }

    fun testSafeDelete() {
        myFixture.configureByText("nginx.conf", "http { upstream unu<caret>sed { server a; } upstream used { server b; } server { location / { proxy_pass http://used; } } }")
        SafeDeleteHandler.invoke(project, arrayOf(myFixture.elementAtCaret), false)
        assertEquals("http { upstream used { server b; } server { location / { proxy_pass http://used; } } }", myFixture.editor.document.text.replace(Regex("\\s+"), " "))
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("used {") + 1)
        assertThrows(BaseRefactoringProcessor.ConflictsInTestsException::class.java) {
            SafeDeleteHandler.invoke(project, arrayOf(myFixture.elementAtCaret), false)
        }
    }

    fun testStructureFoldingAndBraces() {
        myFixture.configureByText("nginx.conf", "http {\n    include mime.types;\n    server {\n        listen 80;\n        root /srv;\n        location /api {\n        }\n    }\n}\n")
        val model = (NginxStructureViewFactory().getStructureViewBuilder(myFixture.file) as TreeBasedStructureViewBuilder).createStructureViewModel(null)
        fun dump(e: TreeElement, depth: Int): List<String> = listOf("  ".repeat(depth) + e.presentation.presentableText) + e.children.flatMap { dump(it, depth + 1) }
        assertEquals(listOf("nginx.conf", "  http", "    include mime.types", "    server", "      listen 80", "      location /api"), dump(model.root, 0))
        assertEquals(3, NginxFoldingBuilder().buildFoldRegions(myFixture.file, myFixture.editor.document, false).size)

        myFixture.configureByText("nginx.conf", "http {<caret>")
        myFixture.type('\n')
        myFixture.checkResult("http {\n    <caret>\n}")
    }

    fun testServerClashesAcrossIncludedFiles() {
        myFixture.addFileToProject("conf.d/a.nginx", "server { listen 80 default_server; server_name a.com; }")
        myFixture.addFileToProject("other/nginx.conf", "http { server { listen 80 default_server; server_name a.com; } }")  // a separate config
        myFixture.configureByText("nginx.conf", "http { include conf.d/*.nginx; server { listen 80 default_server; server_name a.com b.com; } }")
        val found = myFixture.doHighlighting().mapNotNull { it.description }.filter { "duplicate default" in it || "Conflicting" in it }
        assertEquals(listOf(
            "A duplicate default server for *:80 (a server block in a.nginx)",
            "Conflicting server name 'a.com' on *:80: a server block in a.nginx has it too, nginx uses whichever is included first",
        ), found)
    }

    fun testJsFunctionNavigation() {
        myFixture.addFileToProject("js/main.js", "function other(r) {}\nasync function hello(r) {}\nexport default { hello };")
        myFixture.configureByText("nginx.conf", "http { js_import app from js/main.js; js_set ${d}v app.other; server { location / { js_content app.hel<caret>lo; } } }")
        val target = myFixture.getReferenceAtCaretPositionWithAssertion().resolve()!!
        assertEquals("main.js", target.containingFile.name)
        assertEquals(target.containingFile.text.indexOf("hello"), target.textRange.startOffset)
    }

}
