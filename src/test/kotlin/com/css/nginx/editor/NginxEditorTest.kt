package com.css.nginx.editor

import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NginxEditorTest : BasePlatformTestCase() {
    fun testSpellcheckInComments() {
        myFixture.enableInspections(GrazieSpellCheckingInspection())
        myFixture.configureByText("nginx.conf", "# hello wrold\nhttp {}")
        assertTrue(myFixture.doHighlighting().any { it.description?.contains("wrold") == true })
    }

    fun testToggleComment() {
        myFixture.configureByText("nginx.conf", "gzip<caret> on;")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult("#gzip on;")
    }
}
