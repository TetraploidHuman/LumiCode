package com.lumicode.editor.syntax

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.lumicode.editor.model.Language
import com.lumicode.editor.ui.theme.RlColors

/**
 * A deliberately tiny regex tokenizer. It is fast enough for archive-sized files and,
 * because it is pure Kotlin, it behaves identically on every target.
 */
object SyntaxHighlighter {

    private class Rule(val regex: Regex, val style: SpanStyle)

    private val keywordStyle = SpanStyle(color = RlColors.CodeKeyword, fontWeight = FontWeight.Bold)
    private val stringStyle = SpanStyle(color = RlColors.CodeString)
    private val numberStyle = SpanStyle(color = RlColors.CodeNumber)
    private val commentStyle = SpanStyle(color = RlColors.CodeComment)
    private val typeStyle = SpanStyle(color = RlColors.CodeType)
    private val annotationStyle = SpanStyle(color = RlColors.CodeAnnotation)
    private val functionStyle = SpanStyle(color = RlColors.CodeFunction)

    private const val KOTLIN_KEYWORDS =
        "package|import|class|interface|object|fun|val|var|when|if|else|for|while|return|" +
            "is|in|as|null|true|false|this|super|override|open|data|enum|sealed|private|public|" +
            "internal|protected|companion|init|try|catch|finally|throw|by|lazy|get|set|const|suspend|typealias"

    private const val JS_KEYWORDS =
        "const|let|var|function|return|if|else|for|while|class|extends|new|this|null|true|false|" +
            "import|export|from|default|async|await|try|catch|finally|throw|typeof|instanceof"

    private val kotlinRules = listOf(
        Rule(Regex("//[^\\n]*"), commentStyle),
        Rule(Regex("/\\*[\\s\\S]*?\\*/"), commentStyle),
        Rule(Regex("\"\"\"[\\s\\S]*?\"\"\""), stringStyle),
        Rule(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), stringStyle),
        Rule(Regex("'(?:\\\\.|[^'\\\\\\n])*'"), stringStyle),
        Rule(Regex("@[A-Za-z_][A-Za-z0-9_]*"), annotationStyle),
        Rule(Regex("\\b(?:$KOTLIN_KEYWORDS)\\b"), keywordStyle),
        Rule(Regex("\\b[A-Z][A-Za-z0-9_]*\\b"), typeStyle),
        Rule(Regex("\\b[A-Za-z_][A-Za-z0-9_]*(?=\\s*\\()"), functionStyle),
        Rule(Regex("\\b\\d[\\d_]*(?:\\.\\d+)?[fFlL]?\\b"), numberStyle),
    )

    private val gradleRules = kotlinRules

    private val jsonRules = listOf(
        Rule(Regex("\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)"), SpanStyle(color = RlColors.CodeType, fontWeight = FontWeight.Medium)),
        Rule(Regex("\"(?:\\\\.|[^\"\\\\])*\""), stringStyle),
        Rule(Regex("\\b(?:true|false|null)\\b"), keywordStyle),
        Rule(Regex("-?\\b\\d+(?:\\.\\d+)?\\b"), numberStyle),
    )

    private val markdownRules = listOf(
        Rule(Regex("(?m)^#{1,6} [^\\n]*"), SpanStyle(color = RlColors.CodeKeyword, fontWeight = FontWeight.Bold)),
        Rule(Regex("(?m)^> [^\\n]*"), commentStyle),
        Rule(Regex("`[^`\\n]+`"), stringStyle),
        Rule(Regex("\\*\\*[^*\\n]+\\*\\*"), SpanStyle(color = RlColors.CodeKeyword, fontWeight = FontWeight.Bold)),
        Rule(Regex("(?m)^\\s*(?:\\d+\\.|[-*]) "), SpanStyle(color = RlColors.CodeAnnotation)),
        Rule(Regex("\\[[^\\]\\n]*\\]\\([^)\\n]*\\)"), SpanStyle(color = RlColors.CodeType)),
        Rule(Regex("<!--[\\s\\S]*?-->"), commentStyle),
    )

    private val textRules = listOf(
        Rule(Regex("(?m)^\\[\\d+\\]"), SpanStyle(color = RlColors.CodeAnnotation)),
    )

    private fun rulesFor(language: Language): List<Rule> = when (language) {
        Language.KOTLIN -> kotlinRules
        Language.GRADLE -> gradleRules
        Language.JSON -> jsonRules
        Language.MARKDOWN -> markdownRules
        Language.TEXT -> textRules
    }

    fun highlight(text: String, language: Language): AnnotatedString {
        val rules = rulesFor(language)
        val claimed = BooleanArray(text.length)
        val spans = mutableListOf<Triple<Int, Int, SpanStyle>>()

        for (rule in rules) {
            for (match in rule.regex.findAll(text)) {
                val start = match.range.first
                val end = match.range.last + 1
                if (start >= end) continue
                var overlaps = false
                var i = start
                while (i < end) {
                    if (claimed[i]) {
                        overlaps = true
                        break
                    }
                    i++
                }
                if (overlaps) continue
                for (j in start until end) claimed[j] = true
                spans += Triple(start, end, rule.style)
            }
        }

        return buildAnnotatedString {
            append(text)
            spans.forEach { (start, end, style) -> addStyle(style, start, end) }
        }
    }

    data class FindMatch(val start: Int, val end: Int) {
        val length: Int get() = end - start
    }

    /**
     * 文档内查找：字面量或正则；[caseSensitive] / [regex] 控制匹配方式。
     * [rangeStart] / [rangeEnd] 限制在半开区间内（默认全文）。
     * 非法正则返回空列表（不抛）。
     */
    fun findMatches(
        text: String,
        query: String,
        caseSensitive: Boolean = false,
        regex: Boolean = false,
        rangeStart: Int = 0,
        rangeEnd: Int = -1,
    ): List<FindMatch> {
        if (query.isEmpty()) return emptyList()
        val endBound = if (rangeEnd < 0) text.length else rangeEnd.coerceIn(0, text.length)
        val startBound = rangeStart.coerceIn(0, endBound)
        if (startBound >= endBound) return emptyList()
        val slice = text.substring(startBound, endBound)
        if (regex) {
            val options = buildSet {
                if (!caseSensitive) add(RegexOption.IGNORE_CASE)
            }
            val pattern = try {
                Regex(query, options)
            } catch (_: Throwable) {
                return emptyList()
            }
            return pattern.findAll(slice)
                .map { FindMatch(it.range.first + startBound, it.range.last + 1 + startBound) }
                .filter { it.length > 0 }
                .toList()
        }
        val out = mutableListOf<FindMatch>()
        var index = slice.indexOf(query, 0, ignoreCase = !caseSensitive)
        while (index >= 0) {
            out += FindMatch(startBound + index, startBound + index + query.length)
            index = slice.indexOf(query, index + query.length, ignoreCase = !caseSensitive)
        }
        return out
    }

    /**
     * Wraps [highlight] and additionally underlines every occurrence of [query],
     * marking the active match with a solid background.
     */
    fun highlightWithMatches(
        text: String,
        language: Language,
        query: String,
        activeMatch: Int,
        caseSensitive: Boolean = false,
        regex: Boolean = false,
        rangeStart: Int = 0,
        rangeEnd: Int = -1,
    ): AnnotatedString {
        val base = highlight(text, language)
        if (query.isEmpty()) return base
        return buildAnnotatedString {
            append(base)
            findMatches(text, query, caseSensitive, regex, rangeStart, rangeEnd).forEachIndexed { ordinal, match ->
                val isActive = ordinal == activeMatch
                addStyle(
                    SpanStyle(
                        background = if (isActive) RlColors.AccentSoft else RlColors.AccentGlow,
                        color = if (isActive) RlColors.Ink else RlColors.InkSoft,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    ),
                    match.start,
                    match.end,
                )
            }
        }
    }

    fun countMatches(
        text: String,
        query: String,
        caseSensitive: Boolean = false,
        regex: Boolean = false,
        rangeStart: Int = 0,
        rangeEnd: Int = -1,
    ): Int = findMatches(text, query, caseSensitive, regex, rangeStart, rangeEnd).size

    /**
     * 语法高亮 + 查找命中 + 当前词出现 + 配对括号。
     * [caret] / [selEnd] 是**已变换文本**上的偏移（折叠后）。
     */
    fun highlightEditor(
        text: String,
        language: Language,
        query: String,
        activeMatch: Int,
        caret: Int,
        selEnd: Int,
        caseSensitive: Boolean = false,
        regex: Boolean = false,
        rangeStart: Int = 0,
        rangeEnd: Int = -1,
    ): AnnotatedString {
        val base = highlightWithMatches(
            text,
            language,
            query,
            activeMatch,
            caseSensitive,
            regex,
            rangeStart,
            rangeEnd,
        )
        return buildAnnotatedString {
            append(base)

            // 当前词 / 非空选区：高亮全文同词出现（查找激活时跳过，避免叠色）
            if (query.isEmpty()) {
                val word = selectedOrWord(text, caret, selEnd)
                if (word != null && word.length >= 2) {
                    val style = SpanStyle(background = RlColors.AccentGlow)
                    var index = 0
                    while (true) {
                        index = text.indexOf(word, index)
                        if (index < 0) break
                        val end = index + word.length
                        // 只标整词边界
                        val leftOk = index == 0 || !isWordChar(text[index - 1])
                        val rightOk = end >= text.length || !isWordChar(text[end])
                        if (leftOk && rightOk) {
                            addStyle(style, index, end)
                        }
                        index = end
                    }
                }
            }

            findBracketPair(text, caret)?.let { (a, b) ->
                val style = SpanStyle(
                    background = RlColors.AccentSoft,
                    color = RlColors.AccentDeep,
                    fontWeight = FontWeight.Bold,
                )
                addStyle(style, a, a + 1)
                addStyle(style, b, b + 1)
            }
        }
    }

    fun selectedOrWord(text: String, caret: Int, selEnd: Int): String? {
        val start = minOf(caret, selEnd).coerceIn(0, text.length)
        val end = maxOf(caret, selEnd).coerceIn(0, text.length)
        if (end > start) {
            val selected = text.substring(start, end)
            return selected.takeIf { it.isNotEmpty() && '\n' !in it && selected.length <= 64 }
        }
        return wordRangeAt(text, caret)?.let { (s, e) -> text.substring(s, e) }
    }

    fun wordRangeAt(text: String, offset: Int): Pair<Int, Int>? {
        if (text.isEmpty()) return null
        val o = offset.coerceIn(0, text.length)
        val probe = when {
            o < text.length && isWordChar(text[o]) -> o
            o > 0 && isWordChar(text[o - 1]) -> o - 1
            else -> return null
        }
        var s = probe
        while (s > 0 && isWordChar(text[s - 1])) s--
        var e = probe + 1
        while (e < text.length && isWordChar(text[e])) e++
        return s to e
    }

    private fun isWordChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '$'

    private val OPENERS = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val CLOSERS = mapOf(')' to '(', ']' to '[', '}' to '{')

    /**
     * 在光标处（或紧邻左侧）找配对括号，返回两个括号的索引。
     * 不做字符串/注释感知，对档案体量足够。
     */
    fun findBracketPair(text: String, caret: Int): Pair<Int, Int>? {
        if (text.isEmpty()) return null
        val c = caret.coerceIn(0, text.length)
        val candidates = buildList {
            if (c < text.length) add(c)
            if (c > 0) add(c - 1)
        }
        for (i in candidates) {
            val ch = text[i]
            OPENERS[ch]?.let { closer ->
                matchForward(text, i, ch, closer)?.let { return i to it }
            }
            CLOSERS[ch]?.let { opener ->
                matchBackward(text, i, opener, ch)?.let { return it to i }
            }
        }
        return null
    }

    private fun matchForward(text: String, from: Int, open: Char, close: Char): Int? {
        var depth = 0
        for (i in from until text.length) {
            when (text[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    private fun matchBackward(text: String, from: Int, open: Char, close: Char): Int? {
        var depth = 0
        for (i in from downTo 0) {
            when (text[i]) {
                close -> depth++
                open -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    fun offsetOfMatch(
        text: String,
        query: String,
        ordinal: Int,
        caseSensitive: Boolean = false,
        regex: Boolean = false,
        rangeStart: Int = 0,
        rangeEnd: Int = -1,
    ): Int = findMatches(text, query, caseSensitive, regex, rangeStart, rangeEnd)
        .getOrNull(ordinal)?.start ?: -1

    fun matchAt(
        text: String,
        query: String,
        ordinal: Int,
        caseSensitive: Boolean = false,
        regex: Boolean = false,
        rangeStart: Int = 0,
        rangeEnd: Int = -1,
    ): FindMatch? = findMatches(text, query, caseSensitive, regex, rangeStart, rangeEnd)
        .getOrNull(ordinal)

    fun lineOf(text: String, offset: Int): Int = text.take(offset.coerceIn(0, text.length)).count { it == '\n' }

    fun columnOf(text: String, offset: Int): Int {
        val safe = offset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (safe - 1).coerceAtLeast(0))
        return safe - (if (lineStart < 0) 0 else lineStart + 1)
    }

    fun offsetOfLine(text: String, lineIndex: Int): Int {
        if (lineIndex <= 0) return 0
        var seen = 0
        var i = 0
        while (i < text.length) {
            if (text[i] == '\n') {
                seen++
                if (seen == lineIndex) return i + 1
            }
            i++
        }
        return text.length
    }
}
