package com.lumicode.editor.workspace

internal fun jsonString(value: String): String =
    buildString(value.length + 8) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    else append(ch)
                }
            }
        }
        append('"')
    }

internal fun String.boolField(key: String): Boolean {
    val re = Regex(""""$key"\s*:\s*(true|false)""")
    return re.find(this)?.groupValues?.getOrNull(1) == "true"
}

internal fun String.stringField(key: String): String? {
    val keyPat = "\"$key\""
    var i = 0
    while (true) {
        val at = indexOf(keyPat, i)
        if (at < 0) return null
        var j = at + keyPat.length
        while (j < length && this[j].isWhitespace()) j++
        if (j >= length || this[j] != ':') {
            i = at + 1
            continue
        }
        j++
        while (j < length && this[j].isWhitespace()) j++
        if (j + 4 <= length && substring(j, j + 4) == "null") return null
        if (j >= length || this[j] != '"') {
            i = at + 1
            continue
        }
        return decodeJsonString(j)
    }
}

/** [start] points at opening quote. */
private fun String.decodeJsonString(start: Int): String? {
    if (start >= length || this[start] != '"') return null
    val out = StringBuilder()
    var i = start + 1
    while (i < length) {
        val ch = this[i]
        when (ch) {
            '"' -> return out.toString()
            '\\' -> {
                if (i + 1 >= length) return null
                when (val esc = this[i + 1]) {
                    '"', '\\', '/' -> {
                        out.append(esc); i += 2
                    }
                    'n' -> {
                        out.append('\n'); i += 2
                    }
                    'r' -> {
                        out.append('\r'); i += 2
                    }
                    't' -> {
                        out.append('\t'); i += 2
                    }
                    'b' -> {
                        out.append('\b'); i += 2
                    }
                    'f' -> {
                        out.append('\u000c'); i += 2
                    }
                    'u' -> {
                        if (i + 6 > length) return null
                        val hex = substring(i + 2, i + 6)
                        out.append(hex.toInt(16).toChar())
                        i += 6
                    }
                    else -> {
                        out.append(esc); i += 2
                    }
                }
            }
            else -> {
                out.append(ch); i++
            }
        }
    }
    return null
}

internal fun String.intField(key: String): Int? =
    Regex(""""$key"\s*:\s*(-?\d+)""").find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()

internal fun String.stringListField(key: String): List<String> {
    val keyPat = "\"$key\""
    val at = indexOf(keyPat)
    if (at < 0) return emptyList()
    var j = at + keyPat.length
    while (j < length && this[j].isWhitespace()) j++
    if (j >= length || this[j] != ':') return emptyList()
    j++
    while (j < length && this[j].isWhitespace()) j++
    if (j >= length || this[j] != '[') return emptyList()
    j++
    val out = mutableListOf<String>()
    while (j < length) {
        while (j < length && (this[j].isWhitespace() || this[j] == ',')) j++
        if (j < length && this[j] == ']') break
        if (j >= length || this[j] != '"') break
        val s = decodeJsonString(j) ?: break
        out += s
        j = skipJsonString(j) ?: break
    }
    return out
}

private fun String.skipJsonString(start: Int): Int? {
    if (start >= length || this[start] != '"') return null
    var i = start + 1
    while (i < length) {
        when (this[i]) {
            '"' -> return i + 1
            '\\' -> i += 2
            else -> i++
        }
    }
    return null
}

/**
 * 提取 JSON 对象字面量（尊重字符串内的 `{}`，避免把 content 里的花括号当成结构）。
 */
internal fun String.jsonObjectSlices(): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < length) {
        if (this[i] != '{') {
            i++
            continue
        }
        val start = i
        var depth = 0
        var inStr = false
        var esc = false
        while (i < length) {
            val ch = this[i]
            if (inStr) {
                when {
                    esc -> esc = false
                    ch == '\\' -> esc = true
                    ch == '"' -> inStr = false
                }
                i++
                continue
            }
            when (ch) {
                '"' -> {
                    inStr = true
                    i++
                }
                '{' -> {
                    depth++
                    i++
                }
                '}' -> {
                    depth--
                    i++
                    if (depth == 0) {
                        out += substring(start, i)
                        break
                    }
                }
                else -> i++
            }
        }
        if (depth != 0) break
    }
    return out
}
