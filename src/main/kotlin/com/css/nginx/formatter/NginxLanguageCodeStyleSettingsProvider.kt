package com.css.nginx.formatter

import com.css.nginx.lang.NginxLanguage
import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.psi.codeStyle.*

class NginxLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage() = NginxLanguage
    override fun getConfigurableDisplayName() = "Nginx"
    override fun createCustomSettings(settings: CodeStyleSettings) = NginxCodeStyleSettings(settings)
    override fun getIndentOptionsEditor() = SmartIndentOptionsEditor()

    override fun customizeDefaults(
        common: CommonCodeStyleSettings,
        indentOptions: CommonCodeStyleSettings.IndentOptions
    ) {
        indentOptions.INDENT_SIZE = 4
        indentOptions.CONTINUATION_INDENT_SIZE = 4
        indentOptions.TAB_SIZE = 4
        common.KEEP_BLANK_LINES_IN_CODE = 1
        common.SPACE_WITHIN_BRACES = true
    }

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        when (settingsType) {
            SettingsType.SPACING_SETTINGS -> {
                consumer.showStandardOptions("SPACE_WITHIN_BRACES")
                consumer.renameStandardOption("SPACE_WITHIN_BRACES", "Braces of one-line blocks")
            }

            SettingsType.BLANK_LINES_SETTINGS -> consumer.showStandardOptions("KEEP_BLANK_LINES_IN_CODE")
            SettingsType.WRAPPING_AND_BRACES_SETTINGS -> {
                consumer.showStandardOptions("KEEP_SIMPLE_BLOCKS_IN_ONE_LINE")
                consumer.showCustomOption(
                    NginxCodeStyleSettings::class.java,
                    "ALIGN_VALUES_IN_COLUMNS",
                    "Align directive values in columns",
                    "Alignment"
                )
                consumer.showCustomOption(
                    NginxCodeStyleSettings::class.java,
                    "ALIGN_MULTILINE_ARGUMENTS",
                    "Align wrapped arguments (log_format style)",
                    "Alignment"
                )
            }

            else -> {}
        }
    }

    override fun getCodeSample(settingsType: SettingsType) = """
        # nginx.conf
        worker_processes auto;
        events {
            worker_connections 1024;
        }

        http {
            log_format main '${'$'}remote_addr [${'$'}time_local] "${'$'}request"'
                            '${'$'}status ${'$'}body_bytes_sent';

            server {
                listen 80;
                server_name example.com www.example.com;
                root /var/www/html;

                location = /favicon.ico { access_log off; log_not_found off; }

                location / {
                    try_files ${'$'}uri ${'$'}uri/ =404;  # trailing comment
                }
            }
        }
    """.trimIndent()

    override fun createConfigurable(
        baseSettings: CodeStyleSettings,
        modelSettings: CodeStyleSettings
    ): CodeStyleConfigurable =
        object : CodeStyleAbstractConfigurable(baseSettings, modelSettings, "Nginx") {
            override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel =
                object : TabbedLanguageCodeStylePanel(NginxLanguage, currentSettings, settings) {
                    override fun initTabs(settings: CodeStyleSettings) {
                        addIndentOptionsTab(settings)
                        addSpacesTab(settings)
                        addWrappingAndBracesTab(settings)
                        addBlankLinesTab(settings)
                    }
                }
        }
}
