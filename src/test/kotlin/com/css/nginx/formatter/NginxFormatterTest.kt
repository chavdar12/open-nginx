package com.css.nginx.formatter

import com.css.nginx.lang.NginxLanguage
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxFormatterTest : BasePlatformTestCase() {
    private val common: CommonCodeStyleSettings get() = CodeStyle.getSettings(project).getCommonSettings(NginxLanguage)
    private val nginx: NginxCodeStyleSettings
        get() = CodeStyle.getSettings(project).getCustomSettings(NginxCodeStyleSettings::class.java)

    private fun assertFormats(input: String, expected: String) {
        myFixture.configureByText("nginx.conf", input)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        assertEquals(expected, myFixture.file.text)
    }

    fun testOfficialStyleByDefault() = assertFormats(
        """
        |user  nginx;
        |worker_processes    auto;   # trailing comment kept as is
        |
        |
        |
        |events
        |{
        |worker_connections 1024 ;
        |}
        |http {
        |	include mime.types;
        |  server { listen 80; server_name example.com;
        |        location / { root /var/www; }
        |    location ~ \.php$ {
        |            # comment line
        |            fastcgi_param SCRIPT_FILENAME ${'$'}document_root${'$'}fastcgi_script_name;
        |    }
        |  }
        |  log_format main '${'$'}remote_addr'
        |    '${'$'}status';
        |  location_like_word{}
        |}
        """.trimMargin(),
        """
        |user nginx;
        |worker_processes auto;   # trailing comment kept as is
        |
        |events {
        |    worker_connections 1024;
        |}
        |http {
        |    include mime.types;
        |    server {
        |        listen 80;
        |        server_name example.com;
        |        location / {
        |            root /var/www;
        |        }
        |        location ~ \.php$ {
        |            # comment line
        |            fastcgi_param SCRIPT_FILENAME ${'$'}document_root${'$'}fastcgi_script_name;
        |        }
        |    }
        |    log_format main '${'$'}remote_addr'
        |                    '${'$'}status';
        |    location_like_word {}
        |}
        """.trimMargin(),
    )

    fun testAlignValuesInColumns() {
        nginx.ALIGN_VALUES_IN_COLUMNS = true
        assertFormats(
            """
            |server {
            |    listen 80;
            |    server_name example.com;  # comment
            |    root /var/www;
            |
            |    index index.html;
            |    gzip_types  text/css;
            |    location / {
            |        try_files ${'$'}uri =404;
            |    }
            |}
            """.trimMargin(),
            """
            |server {
            |    listen      80;
            |    server_name example.com;  # comment
            |    root        /var/www;
            |
            |    index      index.html;
            |    gzip_types text/css;
            |    location / {
            |        try_files ${'$'}uri =404;
            |    }
            |}
            """.trimMargin(),
        )
    }

    fun testKeepSimpleBlocksInOneLine() {
        common.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
        assertFormats(
            "location = /favicon.ico {   access_log off;  log_not_found off;   }\nlocation /a {\nreturn 204;\n}",
            "location = /favicon.ico { access_log off; log_not_found off; }\nlocation /a {\n    return 204;\n}",
        )
    }

    fun testSpacesWithinOneLineBraces() {
        common.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
        common.SPACE_WITHIN_BRACES = false
        assertFormats("location /a { return 204; }\nlocation /b {}", "location /a {return 204;}\nlocation /b {}")
        common.SPACE_WITHIN_BRACES = true
        assertFormats("location /a {return 204;  add_header X 1;}", "location /a { return 204; add_header X 1; }")
    }

    fun testTabsAndIndentSize() {
        common.indentOptions!!.USE_TAB_CHARACTER = true
        assertFormats("http {\nserver {\nlisten 80;\n}\n}", "http {\n\tserver {\n\t\tlisten 80;\n\t}\n}")
        common.indentOptions!!.USE_TAB_CHARACTER = false
        common.indentOptions!!.INDENT_SIZE = 2
        assertFormats("http {\nserver {\nlisten 80;\n}\n}", "http {\n  server {\n    listen 80;\n  }\n}")
    }

    fun testBlankLinesLimit() {
        common.KEEP_BLANK_LINES_IN_CODE = 0
        assertFormats("a 1;\n\n\nb 2;", "a 1;\nb 2;")
        common.KEEP_BLANK_LINES_IN_CODE = 2
        assertFormats("a 1;\n\n\n\n\nb 2;", "a 1;\n\n\nb 2;")
    }

    fun testWrappedArgumentsWithoutAlignmentUseContinuationIndent() {
        nginx.ALIGN_MULTILINE_ARGUMENTS = false
        assertFormats("http {\nlog_format main 'a'\n'b';\n}", "http {\n    log_format main 'a'\n        'b';\n}")
    }

    fun testEnterIndentsInsideBlock() {
        myFixture.configureByText("nginx.conf", "http {\n    server {\n        listen 80;<caret>\n    }\n}")
        myFixture.type("\n")
        myFixture.checkResult("http {\n    server {\n        listen 80;\n        <caret>\n    }\n}")
    }
}
