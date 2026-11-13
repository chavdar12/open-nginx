package com.css.nginx.inspection

import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.text.StringUtil

/** An annotation whose hover tooltip shows 'quoted' names as code; the plain message stays for the Problems view. */
fun AnnotationHolder.problem(severity: HighlightSeverity, message: String): AnnotationBuilder =
    newAnnotation(severity, message).tooltip(problemTooltip(message))

private val QUOTED = Regex("""(?<!\w)'([^'\s][^']*)'""")  // not the apostrophe in "isn't"
private val TAG = Regex("""^(Security|nginx -t):""")

fun problemTooltip(message: String): String {
    val html = StringBuilder()
    var at = 0
    for (m in QUOTED.findAll(message)) {
        html.append(StringUtil.escapeXmlEntities(message.substring(at, m.range.first)))
        html.append("<code>").append(StringUtil.escapeXmlEntities(m.groupValues[1])).append("</code>")
        at = m.range.last + 1
    }
    html.append(StringUtil.escapeXmlEntities(message.substring(at)))
    return "<html>" + html.toString().replace(TAG, "<b>$1</b>:") + "</html>"
}
