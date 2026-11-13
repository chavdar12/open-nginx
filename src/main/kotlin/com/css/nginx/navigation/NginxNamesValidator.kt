package com.css.nginx.navigation

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.css.nginx.NginxConstants.RENAMABLE_NAME

/** Names rename accepts: `$var`, `@location`, or an upstream name; nothing that would split into several nginx tokens. */
class NginxNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?) = false

    override fun isIdentifier(name: String, project: Project?) = RENAMABLE_NAME.matches(name)
}
