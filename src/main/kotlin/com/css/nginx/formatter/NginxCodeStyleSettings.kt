package com.css.nginx.formatter

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

class NginxCodeStyleSettings(container: CodeStyleSettings) :
    CustomCodeStyleSettings("NginxCodeStyleSettings", container) {
    /** `listen       80;` / `server_name  a.com;` — the layout of nginx's bundled nginx.conf. */
    @JvmField
    var ALIGN_VALUES_IN_COLUMNS = false

    /** Wrapped arguments (log_format strings...) line up under the last argument of the first line, as in the nginx docs. */
    @JvmField
    var ALIGN_MULTILINE_ARGUMENTS = true
}
