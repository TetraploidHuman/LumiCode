package com.lumicode.editor.syntax

import androidx.compose.ui.graphics.Color
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

    /**
     * Wraps [highlight] and additionally underlines every occurrence of [query],
     * marking the active match with a solid background.
     */
    fun highlightWithMatches(
        text: String,
        language: Language,
        query: String,
        activeMatch: Int,
    ): AnnotatedString {
        val base = highlight(text, language)
        if (query.isEmpty()) return base
        return buildAnnotatedString {
            append(base)
            var index = text.indexOf(query, 0, ignoreCase = true)
            var ordinal = 0
            while (index >= 0) {
                val isActive = ordinal == activeMatch
                addStyle(
                    SpanStyle(
                        background = if (isActive) Color(0x33121211) else Color(0x14121211),
                        color = if (isActive) RlColors.Ink else RlColors.InkSoft,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    ),
                    index,
                    index + query.length,
                )
                ordinal++
                index = text.indexOf(query, index + query.length, ignoreCase = true)
            }
        }
    }

    fun countMatches(text: String, query: String): Int {
        if (query.isEmpty()) return 0
        var count = 0
        var index = text.indexOf(query, 0, ignoreCase = true)
        while (index >= 0) {
            count++
            index = text.indexOf(query, index + query.length, ignoreCase = true)
        }
        return count
    }

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
