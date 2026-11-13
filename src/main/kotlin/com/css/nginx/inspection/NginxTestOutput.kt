package com.css.nginx.inspection

import com.intellij.lang.annotation.HighlightSeverity
import com.css.nginx.NginxConstants.NGINX_TEST_LINE

/** Problems `nginx -t` reported for [path]; messages without a location (e.g. log file permissions) are dropped. */
fun parseNginxTestOutput(stderr: String, path: String): List<NginxConfigTestAnnotator.Problem> = NGINX_TEST_LINE.findAll(stderr)
    .filter { it.groupValues[3] == path }
    .map {
        val severity = if (it.groupValues[1] in setOf(
                "emerg",
                "alert",
                "crit",
                "error"
            )
        ) HighlightSeverity.ERROR else HighlightSeverity.WARNING
        NginxConfigTestAnnotator.Problem(it.groupValues[4].toInt(), severity, it.groupValues[2])
    }.toList()
