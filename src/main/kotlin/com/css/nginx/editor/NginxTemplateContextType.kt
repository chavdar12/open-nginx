package com.css.nginx.editor

import com.css.nginx.psi.NginxFile
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

/** Live templates (proxy, ssl, ws, php, cors, cache...) apply in nginx files; see liveTemplates/Nginx.xml. */
class NginxTemplateContextType : TemplateContextType("Nginx") {
    override fun isInContext(context: TemplateActionContext) = context.file is NginxFile
}
