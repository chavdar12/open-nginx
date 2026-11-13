package com.css.nginx.lang

import com.css.nginx.psi.NginxTypes
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxFileTypeDetectorTest : BasePlatformTestCase() {
    fun testNginxContentInAnyFileIsDetected() {
        val conf = "# my site\nserver {\n    listen 80;\n    log_format main '\$a'\n                    '\$b';\n}\n"
        assertEquals(NginxFileType, myFixture.configureByText("test.conf", conf).virtualFile.fileType)
        assertEquals(
            NginxFileType,
            myFixture.configureByText("default", "location / {\n    return 204;\n}\n").virtualFile.fileType
        )
    }

    fun testOtherConfigFormatsAreLeftAlone() {
        assertFalse(looksLikeNginx("key=value\nother = 2\n"))         // ini/properties
        assertFalse(looksLikeNginx("[section]\nname = x\n"))
        assertFalse(looksLikeNginx("foo {\n  bar = 1\n}\n"))         // HOCON-ish, no nginx directive
        assertFalse(looksLikeNginx("int main() {\n  return 0;\n}\n")) // C-like
        assertFalse(looksLikeNginx(""))
    }

    fun testNewEmptyConfFileIsDetectedOnceNginxContentIsWritten() {
        val file = myFixture.tempDirFixture.createFile("new.conf", "")
        assertNotSame(NginxFileType, file.fileType)
        WriteAction.runAndWait<Throwable> { file.setBinaryContent("server {\n    listen 80;\n}\n".toByteArray()) }
        assertEquals(NginxFileType, file.fileType)
    }

    fun testNewFileSwitchesToNginxWhileTypingWithoutSaving() {
        val psi = myFixture.configureByText("typed.conf", "")
        assertNotSame(NginxFileType, psi.virtualFile.fileType)
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(0, "server {\n    listen 80;\n}\n")
        }
        // debounced: the re-check runs once typing pauses
        PlatformTestUtil.waitWithEventsDispatching("PSI never switched to nginx", {
            PsiManager.getInstance(project).findFile(psi.virtualFile)?.language == NginxLanguage
        }, 5)
        assertEquals(NginxFileType, psi.virtualFile.fileType)
        // the open file's PSI (what highlighting and checks run on) must switch too, not just the type lookup
        assertEquals(NginxLanguage, PsiManager.getInstance(project).findFile(psi.virtualFile)!!.language)
        // and the already open editor must recolor with the nginx lexer (the platform updates it asynchronously)
        PlatformTestUtil.waitWithEventsDispatching("editor kept the old highlighter", {
            (myFixture.editor as EditorEx).highlighter.createIterator(0).tokenType == NginxTypes.NAME
        }, 5)
        val token = (myFixture.editor as EditorEx).highlighter.createIterator(0).tokenType
        assertEquals(NginxTypes.NAME, token)
    }

    fun testTypingIntoNginxConfNeverRefreshes() {
        // nginx.conf is nginx by name: keystrokes must not drop and rebuild its PSI
        val psi = myFixture.configureByText("nginx.conf", "")
        for (c in "events {}\n") WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(
                myFixture.editor.document.textLength,
                c.toString()
            )
        }
        Thread.sleep(500)  // longer than the 300 ms debounce
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertTrue(psi.isValid)
        assertEquals(NginxFileType, psi.virtualFile.fileType)
    }
}
