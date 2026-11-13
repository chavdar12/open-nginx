package com.css.nginx.formatter

import com.css.nginx.lang.NginxLanguage
import com.intellij.formatting.*

class NginxFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(context: FormattingContext): FormattingModel {
        val settings = context.codeStyleSettings
        val style = NginxBlock.Style(
            settings.getCommonSettings(NginxLanguage),
            settings.getCustomSettings(NginxCodeStyleSettings::class.java)
        )
        val root = NginxBlock(context.containingFile.node, null, Indent.getNoneIndent(), style)
        return FormattingModelProvider.createFormattingModelForPsiFile(context.containingFile, root, settings)
    }
}
