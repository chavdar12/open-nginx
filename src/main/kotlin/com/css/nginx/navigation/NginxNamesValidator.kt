package com.css.nginx.navigation

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project

/** Names rename accepts: `$var`, `@location`, or an upstream name; nothing that would split into several nginx tokens. */
class NginxNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?) = false

    override fun isIdentifier(name: String, project: Project?) = VALID.matches(name)
}

// $1 is a regex capture, not a name; quotes, ; { } # and whitespace end an nginx token
private val VALID = Regex("""\$[A-Za-z_]\w*|@[^\s;{}'"#$]+|[^\s;{}'"#$@][^\s;{}'"#$]*""")
