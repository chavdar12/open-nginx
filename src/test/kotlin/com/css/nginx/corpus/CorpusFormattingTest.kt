package com.css.nginx.corpus

import com.css.nginx.formatter.NginxCodeStyleSettings
import com.css.nginx.lang.NginxLanguage
import com.intellij.application.options.CodeStyle

/** Formatting must never change what nginx reads, and formatting twice must change nothing. */
class CorpusFormattingTest : NginxCorpusTestCase() {
    fun testFormattingCorpusWithDefaultStyle() = checkFormattingIsSafe()

    fun testFormattingCorpusWithAllOptionsOn() {
        val settings = CodeStyle.getSettings(project)
        settings.getCustomSettings(NginxCodeStyleSettings::class.java).ALIGN_VALUES_IN_COLUMNS = true
        settings.getCommonSettings(NginxLanguage).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
        settings.getCommonSettings(NginxLanguage).SPACE_WITHIN_BRACES = false
        settings.getCommonSettings(NginxLanguage).indentOptions!!.USE_TAB_CHARACTER = true
        checkFormattingIsSafe()
    }
}
