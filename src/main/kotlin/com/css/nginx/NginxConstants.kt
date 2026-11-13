package com.css.nginx

import com.css.nginx.psi.NginxTypes
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.TokenSet

/** Fixed names, patterns and directive sets the plugin's code shares; the data from nginx's docs lives in data/. */
object NginxConstants {
    val PASS_DIRECTIVES = setOf("proxy_pass", "grpc_pass", "uwsgi_pass")

    val NAMED_LOCATION_USERS = setOf("try_files", "error_page", "post_action")

    /** directive -> index of its module.function argument */
    val JS_FUNCTION_ARG = mapOf(
        "js_access" to 0, "js_body_filter" to 0, "js_content" to 0, "js_filter" to 0, "js_header_filter" to 0,
        "js_periodic" to 0, "js_preread" to 0, "js_set" to 1,
    )

    val PATH_ARG_DIRECTIVES = setOf(
        "include",
        "root",
        "alias",
        "auth_basic_user_file",
        "ssl_certificate",
        "ssl_certificate_key",
        "ssl_trusted_certificate",
        "ssl_client_certificate",
        "ssl_dhparam",
        "ssl_crl",
        "ssl_password_file",
        "ssl_stapling_file",
        "ssl_session_ticket_key",
        "proxy_ssl_certificate",
        "proxy_ssl_certificate_key",
        "proxy_ssl_trusted_certificate",
        "js_path",
        "geoip2",
        "geoip_country",
        "geoip_city",
        "geoip_org",
    )

    val DIRECTORY_DIRECTIVES = setOf("root", "alias", "js_path")

    val MATCH_OPERATORS = setOf("~", "~*", "!~", "!~*")

    /** directive -> index of the argument holding the declared variable, e.g. `map $uri $new` declares $new */
    val VARIABLE_ARG = mapOf(
        "set" to 0,
        "map" to 1,
        "split_clients" to 1,
        "js_var" to 0,
        "set_by_lua" to 0,
        "set_by_lua_block" to 0,
        "set_by_lua_file" to 0,
    )

    /** blocks whose entries are `key value;` pairs, not directives: `map $a $b { foo $c; }` uses $c */
    val KEYED_BLOCKS = setOf("map", "geo", "split_clients", "types", "charset_map", "match")

    val NAMED_CAPTURE = Regex("""\(\?P?<([A-Za-z_]\w*)>""")

    /** Directives whose first argument is a header name. */
    val HEADER_NAME_DIRECTIVES = setOf(
        "add_header", "add_trailer", "proxy_set_header", "grpc_set_header",
        "proxy_hide_header", "proxy_pass_header", "grpc_hide_header", "grpc_pass_header",
        "fastcgi_hide_header", "fastcgi_pass_header", "uwsgi_hide_header", "uwsgi_pass_header",
        "scgi_hide_header", "scgi_pass_header",
    )

    /** Files every nginx package ships next to nginx.conf; projects rarely keep copies. */
    val SHIPPED_INCLUDES = setOf(
        "mime.types",
        "fastcgi_params",
        "fastcgi.conf",
        "proxy_params",
        "scgi_params",
        "uwsgi_params",
        "koi-utf",
        "koi-win",
        "win-utf",
        "snippets/fastcgi-php.conf"
    )

    val NAME_VALUE_PARAMETER = Regex("""^([a-z_][a-z0-9_]*)=""")

    val NGINX_TEST_LINE = Regex("""nginx: \[(\w+)] (.*) in (.+):(\d+)$""", RegexOption.MULTILINE)

    val QUOTED_NAME = Regex("""(?<!\w)'([^'\s][^']*)'""")  // not the apostrophe in "isn't"

    val TOOLTIP_TAG = Regex("""^(Security|nginx -t):""")

    const val REGEX_META_CHARS = "^$|()*+?.{}"

    val PCRE_ONLY = Regex("""\(\?\||\[\[:|\\K|\(\*|\(\?R\)|\(\?\d""")

    // (a+)+ (\w+\s?)* (.*)*: a group with + or * inside, itself followed by + * or {n,}
    val NESTED_QUANTIFIER = Regex("""\((?:[^()\\]|\\.)*[+*](?:[^()\\]|\\.)*\)(?:[+*]|\{\d+,\d*})""")

    val REDIRECT_CODES = setOf("301", "302", "303", "307", "308")

    val WEAK_PROTOCOLS = setOf("SSLv2", "SSLv3", "TLSv1", "TLSv1.1")

    val WEAK_CIPHER = Regex("NULL|RC4|MD5|EXPORT|EXP-|DES|IDEA|SEED", RegexOption.IGNORE_CASE)

    val DECODED_URI_VARIABLE = Regex("""\$(?:\{)?(uri|document_uri)\b""")

    val USER_CONTROLLED_VARIABLE =
        Regex("""\$\{?(http_\w+|arg_\w+|cookie_\w+|host|request_uri|uri|args|query_string|[1-9])\b""")

    val PLAIN_NAME = Regex("[A-Za-z0-9_-]+")

    val PASS_SCHEMES = mapOf(
        "proxy_pass" to listOf("http", "https"),
        "grpc_pass" to listOf("grpc", "grpcs"),
        "uwsgi_pass" to listOf("uwsgi", "suwsgi"),
    )

    // $1 is a regex capture, not a name; quotes, ; { } # and whitespace end an nginx token
    val RENAMABLE_NAME = Regex("""\$[A-Za-z_]\w*|@[^\s;{}'"#$]+|[^\s;{}'"#$@][^\s;{}'"#$]*""")

    val STRUCTURE_DIRECTIVES = setOf("include", "listen", "server_name")

    /** (content stamp, verdict) of the last sniff, so the platform's frequent file type queries stay cheap. */
    val SNIFFED = Key.create<Pair<Long, Boolean>>("nginx.sniffed")

    val ARGUMENT_TOKENS = TokenSet.create(NginxTypes.WORD, NginxTypes.STRING, NginxTypes.VARIABLE)

    /** `$name` or `${name}` as nginx interpolates them in arguments and strings. */
    val VARIABLE_REF = Regex("""\$(?:\{(\w+)}|(\w+))""")
}
