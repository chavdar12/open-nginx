package com.css.nginx.formatter

import com.css.nginx.psi.NginxTypes
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType

/** The whitespace right before [n], or "" */
fun whitespaceBefore(n: ASTNode) = n.treePrev?.takeIf { it.elementType == TokenType.WHITE_SPACE }?.text ?: ""

/** [n] is the first token on its line (a comment line counts as a line break) */
fun startsLine(n: ASTNode) =
    n.treePrev == null || '\n' in whitespaceBefore(n) || n.treePrev.elementType == NginxTypes.COMMENT

/** An empty line separates [n] from what came before */
fun blankLineBefore(n: ASTNode) = whitespaceBefore(n).count { it == '\n' } > 1
