package com.css.nginx.lexer

/** `$name` or `${name}` as nginx interpolates them in arguments and strings. */
internal val VARIABLE_REF = Regex("""\$(?:\{(\w+)}|(\w+))""")
