package com.css.nginx.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.util.IncorrectOperationException

/** Rewrites part of one token in a directive, e.g. the host in `proxy_pass http://app/x;` when its upstream is renamed. */
class NginxDirectiveManipulator : AbstractElementManipulator<NginxDirective>() {
    override fun handleContentChange(element: NginxDirective, range: TextRange, newContent: String): NginxDirective {
        val leaf =
            element.findElementAt(range.startOffset) ?: throw IncorrectOperationException("Nothing to rename at $range")
        val inLeaf = range.shiftLeft(leaf.startOffsetInParent)
        leaf.replace(nginxToken(element, inLeaf.replace(leaf.text, newContent)))
        return element
    }
}
