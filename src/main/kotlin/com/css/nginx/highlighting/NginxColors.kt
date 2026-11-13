package com.css.nginx.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as C

object NginxColors {
    val DIRECTIVE = createTextAttributesKey("NGINX_DIRECTIVE", C.KEYWORD)
    val STRING = createTextAttributesKey("NGINX_STRING", C.STRING)
    val VARIABLE = createTextAttributesKey("NGINX_VARIABLE", C.INSTANCE_FIELD)
    // METADATA: the one unused standard color that stands out in both light and dark schemes
    val PARAMETER = createTextAttributesKey("NGINX_PARAMETER", C.METADATA)
    val COMMENT = createTextAttributesKey("NGINX_COMMENT", C.LINE_COMMENT)
    val SEMICOLON = createTextAttributesKey("NGINX_SEMICOLON", C.SEMICOLON)
    val BRACES = createTextAttributesKey("NGINX_BRACES", C.BRACES)
    val REGEX_META = createTextAttributesKey("NGINX_REGEX_META", C.PREDEFINED_SYMBOL)       // ^ $ ( ) [ ] | * + ? . { }
    val REGEX_ESCAPE = createTextAttributesKey("NGINX_REGEX_ESCAPE", C.VALID_STRING_ESCAPE) // \. \d
}
