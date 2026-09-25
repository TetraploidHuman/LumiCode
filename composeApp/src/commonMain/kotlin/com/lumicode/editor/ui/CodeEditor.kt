package com.lumicode.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.model.Language
import com.lumicode.editor.state.LineKind
import com.lumicode.editor.syntax.SyntaxHighlighter
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlSettings
import com.lumicode.editor.ui.theme.RlType

/**
 * The code surface: gutter + syntax highlighted, editable text.
 *
 * Text is a plain [BasicTextField] whose rendering is decorated by a
 * [VisualTransformation]; that keeps the implementation identical on all targets
 * (Android / desktop / wasm) and avoids platform text APIs entirely.
 */
@Composable
fun CodeEditor(
    path: String,
    text: String,
    language: Language,
    findQuery: String,
    findActiveMatch: Int,
    revealLine: Int?,
    problemLines: Map<Int, LineKind>,
    onTextChange: (String) -> Unit,
    onCursorChange: (line: Int, column: Int, selectionLength: Int) -> Unit,
    onSave: () -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val lineHeight = RlSettings.codeLineHeight
    val lineHeightPx = with(density) { lineHeight.toPx() }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val measurer = rememberTextMeasurer()

    // 等宽字符宽度：字号变了要重新量，行号栏与横向滚动范围都靠它对齐
    val charWidth = remember(measurer, RlSettings.codeFontSize) {
        val measured = measurer.measure("0000000000", RlType.code)
        (measured.size.width / 10f).toInt().coerceAtLeast(1)
    }

    var value by remember(path) { mutableStateOf(TextFieldValue(text, TextRange(0))) }

    // Reset the buffer when switching documents.
    LaunchedEffect(path) {
        value = TextFieldValue(text, TextRange(0))
        verticalScroll.scrollTo(0)
        horizontalScroll.scrollTo(0)
    }

    // Keep the buffer in sync when the document changes from the outside (e.g. new file).
    LaunchedEffect(text) {
        if (text != value.text) value = value.copy(text = text, selection = TextRange(value.selection.start.coerceAtMost(text.length)))
    }

    // Jump to the requested line.
    LaunchedEffect(revealLine, path) {
        val line = revealLine ?: return@LaunchedEffect
        val offset = SyntaxHighlighter.offsetOfLine(value.text, line - 1)
        value = value.copy(selection = TextRange(offset))
        verticalScroll.animateScrollTo(((line - 2).coerceAtLeast(0) * lineHeightPx).toInt())
    }

    // Jump to the active search hit.
    LaunchedEffect(findQuery, findActiveMatch, path) {
        if (findQuery.isEmpty()) return@LaunchedEffect
        var index = value.text.indexOf(findQuery, 0, ignoreCase = true)
        var ordinal = 0
        while (index >= 0 && ordinal < findActiveMatch) {
            ordinal++
            index = value.text.indexOf(findQuery, index + findQuery.length, ignoreCase = true)
        }
        if (index >= 0) {
            value = value.copy(selection = TextRange(index, index + findQuery.length))
            val line = SyntaxHighlighter.lineOf(value.text, index)
            verticalScroll.animateScrollTo((line * lineHeightPx).toInt())
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(RlColors.Panel)) {
        val lineCount = value.text.count { it == '\n' } + 1
        val showLineNumbers = RlSettings.showLineNumbers
        val gutterWidth = if (showLineNumbers) RlDimens.gutterWidth else 0.dp
        val contentHeight = RlDimens.codePaddingTop + lineHeight * lineCount + 28.dp
        val longest = value.text.split('\n').maxOfOrNull { it.length } ?: 0
        val codeWidth = with(density) { (charWidth * longest).toDp() } + RlDimens.codePaddingStart + 48.dp
        val totalWidth = maxOf(maxWidth, gutterWidth + codeWidth)
        val totalHeight = maxOf(maxHeight, contentHeight)

        val activeLine = SyntaxHighlighter.lineOf(value.text, value.selection.start) + 1

        // 光标移动时：行高亮带与侧边标记平滑滑过去，而不是瞬间跳
        val lineOffset by animateDpAsState(
            targetValue = lineHeight * (activeLine - 1),
            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
            label = "activeLineOffset",
        )

        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll),
        ) {
            Row(Modifier.size(totalWidth, totalHeight)) {
                // ------------------------------------------------------- gutter
                if (showLineNumbers) {
                    Box(Modifier.width(gutterWidth).fillMaxHeight()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(top = RlDimens.codePaddingTop),
                            horizontalAlignment = Alignment.End,
                        ) {
                            for (line in 1..lineCount) {
                                val isActive = line == activeLine
                                val marker = problemLines[line]
                                Box(Modifier.height(lineHeight).fillMaxWidth()) {
                                    BasicText(
                                        text = line.toString().padStart(3, '0'),
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 12.dp),
                                        style = RlType.codeGutter.copy(
                                            color = if (isActive) RlColors.Ink else RlColors.Faint,
                                        ),
                                    )
                                    if (marker != null) {
                                        Box(
                                            Modifier
                                                .align(Alignment.CenterEnd)
                                                .padding(end = 3.dp)
                                                .size(4.dp)
                                                .background(
                                                    when (marker) {
                                                        LineKind.ERROR -> RlColors.Ink
                                                        LineKind.WARN -> RlColors.Muted
                                                        else -> RlColors.Faint
                                                    },
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                        // 侧边高亮：跟着光标行平滑移动
                        Box(
                            Modifier
                                .offset(y = RlDimens.codePaddingTop + lineOffset + 3.dp)
                                .width(3.dp)
                                .height((lineHeight - 6.dp).coerceAtLeast(4.dp))
                                .background(RlColors.Ink),
                        )
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(RlColors.Hair))
                }

                // --------------------------------------------------------- code
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    // 当前行高亮带（跟随光标滑动）
                    Box(
                        Modifier
                            .offset(y = RlDimens.codePaddingTop + lineOffset)
                            .fillMaxWidth()
                            .height(lineHeight)
                            .background(RlColors.PaperDeep.copy(alpha = 0.6f)),
                    )
                    BasicTextField(
                        value = value,
                        onValueChange = { next ->
                            value = autoClose(value, next)
                            onTextChange(value.text)
                            val selection = value.selection
                            onCursorChange(
                                SyntaxHighlighter.lineOf(value.text, selection.start) + 1,
                                SyntaxHighlighter.columnOf(value.text, selection.start) + 1,
                                selection.max - selection.min,
                            )
                        },
                        textStyle = RlType.code,
                        cursorBrush = SolidColor(RlColors.Ink),
                        visualTransformation = VisualTransformation { annotated ->
                            if (annotated.text == value.text) {
                                TransformedText(
                                    SyntaxHighlighter.highlightWithMatches(
                                        annotated.text,
                                        language,
                                        findQuery,
                                        findActiveMatch,
                                    ),
                                    OffsetMapping.Identity,
                                )
                            } else {
                                TransformedText(annotated, OffsetMapping.Identity)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = RlDimens.codePaddingStart, top = RlDimens.codePaddingTop)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val ctrl = event.isCtrlPressed || event.isMetaPressed
                                when {
                                    event.key == Key.Tab -> {
                                        val insertion = if (event.isShiftPressed) {
                                            removeIndent(value)
                                        } else {
                                            insertAtCursor(value, " ".repeat(RlSettings.tabWidth))
                                        }
                                        value = insertion
                                        onTextChange(value.text)
                                        true
                                    }

                                    event.key == Key.Enter && !ctrl -> {
                                        value = insertNewlineWithIndent(value)
                                        onTextChange(value.text)
                                        true
                                    }

                                    ctrl && event.key == Key.S -> {
                                        onSave()
                                        true
                                    }

                                    event.key == Key.F5 || (ctrl && event.key == Key.Enter) -> {
                                        onRun()
                                        true
                                    }

                                    else -> false
                                }
                            },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- editing helpers

private fun insertAtCursor(value: TextFieldValue, insertion: String): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val next = text.substring(0, start) + insertion + text.substring(end)
    val caret = start + insertion.length
    return value.copy(text = next, selection = TextRange(caret))
}

private fun insertNewlineWithIndent(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val caret = value.selection.min
    val lineStart = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val currentLine = text.substring(lineStart, caret)
    val indent = currentLine.takeWhile { it == ' ' || it == '\t' }
    val extra = if (currentLine.trimEnd().endsWith("{") || currentLine.trimEnd().endsWith("(")) " ".repeat(RlSettings.tabWidth) else ""
    val insertion = "\n$indent$extra"
    val next = text.substring(0, caret) + insertion + text.substring(value.selection.max)
    return value.copy(text = next, selection = TextRange(caret + insertion.length))
}

private fun removeIndent(value: TextFieldValue): TextFieldValue {
    val text = value.text
    var caret = value.selection.min
    var removed = 0
    while (removed < RlSettings.tabWidth && caret > 0 && text[caret - 1] == ' ') {
        caret--
        removed++
    }
    if (removed == 0) return value
    val next = text.removeRange(caret, caret + removed)
    return value.copy(text = next, selection = TextRange(caret))
}

private val CLOSERS = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'')

/** Minimal bracket / quote auto-closing, applied on the plain-text diff. */
private fun autoClose(previous: TextFieldValue, next: TextFieldValue): TextFieldValue {
    val oldText = previous.text
    val newText = next.text
    if (newText.length != oldText.length + 1) return next
    val caret = next.selection.start
    if (caret != next.selection.end || caret == 0) return next
    val typed = newText[caret - 1]
    val closer = CLOSERS[typed] ?: return next
    val after = newText.getOrNull(caret) ?: ' '
    if (after.isLetterOrDigit()) return next
    val inserted = newText.substring(0, caret) + closer + newText.substring(caret)
    return next.copy(text = inserted, selection = TextRange(caret))
}
