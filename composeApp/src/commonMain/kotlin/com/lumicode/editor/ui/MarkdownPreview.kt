package com.lumicode.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlType

/**
 * 轻量 Markdown 预览：标题 / 引用 / 列表 / 表格 / 代码块 / 行内强调。
 * 不依赖外部库，Wasm / 桌面 / Android 行为一致。
 */
@Composable
fun MarkdownPreview(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(
                start = RlDimens.codePaddingStart,
                end = 28.dp,
                top = RlDimens.codePaddingTop,
                bottom = 36.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    Spacer(Modifier.height(if (block.level <= 2) 18.dp else 12.dp))
                    BasicText(
                        text = inlineMarkdown(block.text),
                        style = headingStyle(block.level),
                    )
                    if (block.level <= 2) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).wash(RlColors.Hair))
                        Spacer(Modifier.height(10.dp))
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                is MdBlock.Paragraph -> {
                    BasicText(
                        text = inlineMarkdown(block.text),
                        style = RlType.body.copy(fontSize = 14.sp, lineHeight = 22.sp, color = RlColors.InkSoft),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                is MdBlock.Quote -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Box(Modifier.width(3.dp).height(22.dp).wash(RlColors.Accent))
                        Spacer(Modifier.width(12.dp))
                        BasicText(
                            text = inlineMarkdown(block.text),
                            style = RlType.body.copy(
                                fontSize = 13.5.sp,
                                lineHeight = 21.sp,
                                color = RlColors.Muted,
                                fontStyle = FontStyle.Italic,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                is MdBlock.Bullet -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        BasicText(
                            text = "·",
                            style = RlType.mono.copy(fontSize = 14.sp, color = RlColors.AccentDeep),
                            modifier = Modifier.width(18.dp),
                        )
                        BasicText(
                            text = inlineMarkdown(block.text),
                            style = RlType.body.copy(fontSize = 14.sp, lineHeight = 21.sp, color = RlColors.InkSoft),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is MdBlock.Ordered -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        BasicText(
                            text = "${block.index}.",
                            style = RlType.mono.copy(fontSize = 13.sp, color = RlColors.AccentDeep),
                            modifier = Modifier.width(28.dp),
                        )
                        BasicText(
                            text = inlineMarkdown(block.text),
                            style = RlType.body.copy(fontSize = 14.sp, lineHeight = 21.sp, color = RlColors.InkSoft),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is MdBlock.Code -> {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .wash(RlColors.FieldDeep)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (block.lang.isNotEmpty()) {
                            BasicText(
                                text = block.lang.uppercase(),
                                style = RlType.label(10.sp, RlColors.Faint),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        BasicText(
                            text = block.text,
                            style = RlType.mono.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                color = RlColors.Ink,
                            ),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                is MdBlock.Table -> {
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.fillMaxWidth()) {
                        block.rows.forEachIndexed { rowIndex, row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                row.forEachIndexed { colIndex, cell ->
                                    val weight = if (colIndex == 0) 0.38f else 0.62f
                                    BasicText(
                                        text = inlineMarkdown(cell.trim()),
                                        style = if (rowIndex == 0) {
                                            RlType.label(11.sp, RlColors.Ink)
                                        } else {
                                            RlType.body.copy(fontSize = 13.sp, color = RlColors.InkSoft)
                                        },
                                        modifier = Modifier.weight(weight),
                                        maxLines = 3,
                                    )
                                }
                            }
                            if (rowIndex == 0) {
                                Box(Modifier.fillMaxWidth().height(1.dp).wash(RlColors.HairStrong))
                            } else if (rowIndex < block.rows.lastIndex) {
                                Box(Modifier.fillMaxWidth().height(1.dp).wash(RlColors.Hair))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                is MdBlock.Rule -> {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).wash(RlColors.HairStrong))
                    Spacer(Modifier.height(14.dp))
                }

                is MdBlock.Blank -> Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> RlType.title.copy(fontSize = 26.sp, lineHeight = 32.sp, color = RlColors.Ink)
    2 -> RlType.sectionTitle.copy(fontSize = 20.sp, lineHeight = 26.sp, color = RlColors.Ink)
    3 -> RlType.sectionTitle.copy(fontSize = 16.sp, lineHeight = 22.sp, color = RlColors.Ink)
    else -> RlType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RlColors.Ink)
}

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Ordered(val index: Int, val text: String) : MdBlock()
    data class Code(val lang: String, val text: String) : MdBlock()
    data class Table(val rows: List<List<String>>) : MdBlock()
    data object Rule : MdBlock()
    data object Blank : MdBlock()
}

private fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val out = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()
        val trimmed = line.trimStart()

        if (trimmed.isEmpty()) {
            out += MdBlock.Blank
            i++
            continue
        }

        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val body = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[i])
                i++
            }
            if (i < lines.size) i++ // closing fence
            out += MdBlock.Code(lang, body.toString())
            continue
        }

        if (Regex("^---+$").matches(trimmed) || Regex("^\\*\\*\\*+$").matches(trimmed)) {
            out += MdBlock.Rule
            i++
            continue
        }

        val heading = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
        if (heading != null) {
            out += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            i++
            continue
        }

        if (trimmed.startsWith(">")) {
            val text = trimmed.removePrefix(">").trimStart()
            out += MdBlock.Quote(text)
            i++
            continue
        }

        if (trimmed.startsWith("|") && trimmed.count { it == '|' } >= 2) {
            val rows = mutableListOf<List<String>>()
            while (i < lines.size) {
                val rowLine = lines[i].trim()
                if (!rowLine.startsWith("|") || rowLine.count { it == '|' } < 2) break
                val cells = rowLine.trim('|').split('|').map { it.trim() }
                // skip alignment row | --- | --- |
                val isSep = cells.all { cell ->
                    cell.isNotEmpty() && cell.all { it == '-' || it == ':' || it == ' ' }
                }
                if (!isSep) rows += cells
                i++
            }
            if (rows.isNotEmpty()) out += MdBlock.Table(rows)
            continue
        }

        val ordered = Regex("^(\\d+)\\.\\s+(.*)$").find(trimmed)
        if (ordered != null) {
            out += MdBlock.Ordered(ordered.groupValues[1].toInt(), ordered.groupValues[2])
            i++
            continue
        }

        val bullet = Regex("^[-*+]\\s+(.*)$").find(trimmed)
        if (bullet != null) {
            out += MdBlock.Bullet(bullet.groupValues[1])
            i++
            continue
        }

        // HTML comments → skip
        if (trimmed.startsWith("<!--")) {
            if (!trimmed.contains("-->")) {
                i++
                while (i < lines.size && !lines[i].contains("-->")) i++
            }
            i++
            continue
        }

        // paragraph: join consecutive plain lines
        val para = StringBuilder(trimmed)
        i++
        while (i < lines.size) {
            val next = lines[i].trimEnd()
            val nt = next.trimStart()
            if (nt.isEmpty()) break
            if (nt.startsWith("#") || nt.startsWith(">") || nt.startsWith("```") ||
                nt.startsWith("|") || nt.startsWith("- ") || nt.startsWith("* ") ||
                nt.startsWith("+ ") || Regex("^\\d+\\.\\s").containsMatchIn(nt) ||
                Regex("^---+$").matches(nt)
            ) {
                break
            }
            para.append(' ').append(nt)
            i++
        }
        out += MdBlock.Paragraph(para.toString())
    }

    // collapse trailing blanks
    while (out.lastOrNull() is MdBlock.Blank) out.removeAt(out.lastIndex)
    return out
}

/** 行内：`code`、**bold**、*italic*、[text](url)、~~strike~~ */
internal fun inlineMarkdown(text: String): AnnotatedString {
    val pattern = Regex(
        """(\*\*[^*\n]+?\*\*)|(\*[^*\n]+?\*)|(`[^`\n]+?`)|(~~[^~\n]+?~~)|(\[[^\]\n]+?\]\([^)\n]+?\))""",
    )
    return buildAnnotatedString {
        var cursor = 0
        for (match in pattern.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            val token = match.value
            when {
                token.startsWith("**") -> withStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, color = RlColors.Ink),
                ) { append(token.removeSurrounding("**")) }

                token.startsWith("~~") -> withStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough, color = RlColors.Muted),
                ) { append(token.removeSurrounding("~~")) }

                token.startsWith("*") -> withStyle(
                    SpanStyle(fontStyle = FontStyle.Italic, color = RlColors.InkSoft),
                ) { append(token.removeSurrounding("*")) }

                token.startsWith("`") -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = RlColors.AccentGlow,
                        color = RlColors.AccentDeep,
                    ),
                ) { append(token.removeSurrounding("`")) }

                token.startsWith("[") -> {
                    val link = Regex("""\[([^]]+)]\(([^)]+)\)""").matchEntire(token)
                    val label = link?.groupValues?.getOrNull(1) ?: token
                    withStyle(
                        SpanStyle(color = RlColors.AccentDeep, textDecoration = TextDecoration.Underline),
                    ) { append(label) }
                }

                else -> append(token)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
