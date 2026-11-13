package com.css.nginx.lexer

import com.css.nginx.psi.NginxTypes
import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * Lexer state: 0 = the next word is a directive name, 1 = inside a directive's arguments, 2 = arguments of a `*_by_lua_block`
 * directive, 3 = right after its `{`, where everything up to the matching `}` is one Lua token.
 */
class NginxLexer : LexerBase() {
    private var buf: CharSequence = ""
    private var end = 0
    private var start = 0
    private var tokenEnd = 0
    private var type: IElementType? = null
    private var state = 0
    private var stateAtStart = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        buf = buffer; end = endOffset; tokenEnd = startOffset; state = initialState
        advance()
    }

    override fun getState() = stateAtStart
    override fun getTokenType() = type
    override fun getTokenStart() = start
    override fun getTokenEnd() = tokenEnd
    override fun getBufferSequence() = buf
    override fun getBufferEnd() = end

    // nginx only treats '$' as a variable when followed by a name or '{'
    private fun isVarStart(i: Int) =
        buf[i] == '$' && i + 1 < end && (buf[i + 1] == '{' || buf[i + 1] == '_' || buf[i + 1].isLetterOrDigit())

    override fun advance() {
        start = tokenEnd
        stateAtStart = state
        if (start >= end) {
            type = null; return
        }
        var i = start
        val c = buf[i]
        // quotes and # are only special at the start of an nginx token, not glued to a word or variable
        val tokenStart = i == 0 || buf[i - 1].isWhitespace() || buf[i - 1] in ";{}"
        type = when {
            c.isWhitespace() -> {
                while (i < end && buf[i].isWhitespace()) i++
                TokenType.WHITE_SPACE
            }

            state == 3 && c != '}' -> {
                i = luaEnd(i)
                while (i > start && buf[i - 1].isWhitespace()) i--  // trailing whitespace before } stays whitespace for the formatter
                state = 0; NginxTypes.LUA
            }

            c == '#' && tokenStart -> {
                while (i < end && buf[i] != '\n') i++
                NginxTypes.COMMENT
            }

            c == ';' || c == '{' || c == '}' -> {
                i++; state = if (c == '{' && state == 2) 3 else 0
                when (c) {
                    ';' -> NginxTypes.SEMI; '{' -> NginxTypes.LBRACE; else -> NginxTypes.RBRACE
                }
            }

            (c == '"' || c == '\'') && tokenStart -> {
                i++
                while (i < end && buf[i] != c) i += if (buf[i] == '\\' && i + 1 < end) 2 else 1
                if (i < end) i++
                state = if (state == 2) 2 else 1; NginxTypes.STRING
            }

            isVarStart(i) -> {
                i++
                if (buf[i] == '{') {
                    while (i < end && buf[i] != '}') i++
                    if (i < end) i++
                } else {
                    while (i < end && (buf[i] == '_' || buf[i].isLetterOrDigit())) i++
                }
                state = if (state == 2) 2 else 1; NginxTypes.VARIABLE
            }

            else -> {
                i++
                while (i < end && !buf[i].isWhitespace() && buf[i] !in ";{}" && !isVarStart(i)) i++
                if (state == 0) NginxTypes.NAME.also {
                    state = if (buf.subSequence(start, i).endsWith("_by_lua_block")) 2 else 1
                }
                else NginxTypes.WORD.also { if (state != 2) state = 1 }
            }
        }
        tokenEnd = i
    }

    /** End of a Lua body starting at [from]: the `}` that closes it, skipping Lua strings, comments and long brackets. */
    private fun luaEnd(from: Int): Int {
        var i = from
        var depth = 0
        fun longBracket(at: Int): Int {  // [[ or [==[ -> the number of '=', or -1
            if (at >= end || buf[at] != '[') return -1
            var j = at + 1
            while (j < end && buf[j] == '=') j++
            return if (j < end && buf[j] == '[') j - at - 1 else -1
        }

        fun skipLong(at: Int, level: Int): Int {  // past the matching ]==]
            val close = "]" + "=".repeat(level) + "]"
            val k = buf.indexOf(close, at + level + 2)
            return if (k < 0) end else k + close.length
        }
        while (i < end) {
            val c = buf[i]
            when {
                c == '-' && i + 1 < end && buf[i + 1] == '-' -> {
                    val level = longBracket(i + 2)
                    i = if (level >= 0) skipLong(i + 2, level) else buf.indexOf('\n', i).let { if (it < 0) end else it }
                    continue
                }

                c == '"' || c == '\'' -> {
                    i++
                    while (i < end && buf[i] != c && buf[i] != '\n') i += if (buf[i] == '\\' && i + 1 < end) 2 else 1
                }

                c == '[' && longBracket(i) >= 0 -> {
                    i = skipLong(i, longBracket(i)); continue
                }

                c == '{' -> depth++
                c == '}' -> if (depth == 0) return i else depth--
            }
            i++
        }
        return end
    }
}
