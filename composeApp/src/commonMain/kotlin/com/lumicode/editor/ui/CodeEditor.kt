package com.lumicode.editor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.dp
import com.lumicode.editor.model.Language
import com.lumicode.editor.state.LineKind
import com.lumicode.editor.syntax.SyntaxHighlighter
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlMotion
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
    // 行距：先用设置值估一个初值，首帧布局后由 onTextLayout 用真实度量覆盖
    var lineHeightPx by remember(path) {
        mutableStateOf(with(density) { RlSettings.codeLineHeight.toPx() })
    }
    val lineHeight = with(density) { lineHeightPx.toDp() }

    // 视觉行模型：软换行会把一个逻辑行摊成多个视觉行。行号栏、当前行高亮带、
    // 光标自动滚动都必须按「逻辑行占据的视觉范围」来定位，否则一换行就整体错位。
    var lineTops by remember(path) { mutableStateOf<List<Float>>(emptyList()) }
    var lineBottoms by remember(path) { mutableStateOf<List<Float>>(emptyList()) }
    var layoutHeightPx by remember(path) { mutableStateOf(0f) }
    val verticalScroll = rememberScrollState()

    var value by remember(path) { mutableStateOf(TextFieldValue(text, TextRange(0))) }

    // 切换文档 = 换一页：整块代码面轻轻「显影」出来（微弱上浮 + 淡入）。
    // 用 graphicsLayer 而不是换 composable，BasicTextField 不会重建、也不会丢焦点。
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(path) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 190, easing = RlMotion.Sharp))
    }

    // Reset the buffer when switching documents.
    LaunchedEffect(path) {
        value = TextFieldValue(text, TextRange(0))
        verticalScroll.scrollTo(0)
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

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = reveal.value
                alpha = 0.15f + 0.85f * p
                translationY = (1f - p) * 6f
            },
    ) {
        val lineCount = value.text.count { it == '\n' } + 1
        val showLineNumbers = RlSettings.showLineNumbers
        val gutterWidth = if (showLineNumbers) RlDimens.gutterWidth else 0.dp
        // 布局还没回来时退化成等距，回来后立刻切到真实视觉位置
        val fallbackTops = List(lineCount) { it * lineHeightPx }
        val fallbackBottoms = List(lineCount) { (it + 1) * lineHeightPx }
        val tops = if (lineTops.size == lineCount) lineTops else fallbackTops
        val bottoms = if (lineBottoms.size == lineCount) lineBottoms else fallbackBottoms
        val textHeight = if (layoutHeightPx > 0f) {
            with(density) { layoutHeightPx.toDp() }
        } else {
            lineHeight * lineCount
        }
        val contentHeight = RlDimens.codePaddingTop + textHeight + 28.dp
        val totalHeight = maxOf(maxHeight, contentHeight)

        val activeLine = SyntaxHighlighter.lineOf(value.text, value.selection.start) + 1

        // 上下键（或输入）把光标移出可视区时自动滚动，保证光标行始终可见
        val cursorLineIndex = SyntaxHighlighter.lineOf(value.text, value.selection.start)
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        LaunchedEffect(cursorLineIndex, viewportHeightPx, tops, bottoms) {
            val top = tops.getOrElse(cursorLineIndex) { cursorLineIndex * lineHeightPx }
            val bottom = bottoms.getOrElse(cursorLineIndex) { top + lineHeightPx }
            val scroll = verticalScroll.value.toFloat()
            when {
                top < scroll -> verticalScroll.scrollTo(top.toInt().coerceAtLeast(0))
                bottom > scroll + viewportHeightPx ->
                    verticalScroll.scrollTo((bottom - viewportHeightPx).toInt().coerceAtLeast(0))
            }
        }

        // 光标移动时：行高亮带与侧边标记平滑滑过去，而不是瞬间跳。
        // 位置和高度都取当前逻辑行的真实视觉范围 —— 换行的行会整段被高亮。
        val activeTopPx = tops.getOrElse(activeLine - 1) { (activeLine - 1) * lineHeightPx }
        val activeBottomPx = bottoms.getOrElse(activeLine - 1) { activeTopPx + lineHeightPx }
        val lineOffset by animateDpAsState(
            targetValue = with(density) { activeTopPx.toDp() },
            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
            label = "activeLineOffset",
        )
        val activeSpan = with(density) { (activeBottomPx - activeTopPx).toDp() }

        // 只有纵向滚动：长行一律软换行，代码永远完整可见。
        // 行号栏按「逻辑行的视觉跨度」占位，所以换行的续行自然留空、编号不会错位。
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll),
        ) {
            Row(Modifier.fillMaxWidth().height(totalHeight)) {
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
                                // 行号占的是「这个逻辑行的全部视觉行」；换行产生的续行
                                // 没有编号，也不会有东西挤上来 —— 编号与文本永远同步
                                val span = with(density) {
                                    (bottoms[line - 1] - tops[line - 1]).toDp()
                                }.coerceAtLeast(lineHeight)
                                Box(Modifier.height(span).fillMaxWidth()) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .fillMaxWidth()
                                            .height(lineHeight),
                                    ) {
                                        BasicText(
                                            text = line.toString().padStart(3, '0'),
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .padding(end = 12.dp),
                                            style = RlType.codeGutter.copy(
                                                color = if (isActive) RlColors.Accent else RlColors.Faint,
                                            ),
                                        )
                                        if (marker != null) {
                                            Box(
                                                Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .padding(end = 3.dp)
                                                    .size(4.dp)
                                                    .wash(
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
                        }
                        // 侧边高亮：跟着光标行平滑移动，冰青小条
                        Box(
                            Modifier
                                .offset(y = RlDimens.codePaddingTop + lineOffset + 3.dp)
                                .width(3.dp)
                                .height((activeSpan - 6.dp).coerceAtLeast(4.dp))
                                .wash(RlColors.Accent),
                        )
                    }
                }

                // --------------------------------------------------------- code
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    // 当前行：一道横贯整块的极淡冰青，没有圆角、没有边框。
                    // 它读起来像"光扫过这一行"，而不是"选中了一个盒子"。
                    Box(
                        Modifier
                            .offset(y = RlDimens.codePaddingTop + lineOffset)
                            .fillMaxWidth()
                            .height(activeSpan)
                            .wash(RlColors.AccentSoft),
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
                        cursorBrush = SolidColor(RlColors.Accent),
                        onTextLayout = { result ->
                            // 用真实布局量行距，避免依赖字体度量（内置字体是异步加载的）
                            val measured = when {
                                result.lineCount >= 2 ->
                                    (result.getLineTop(1) - result.getLineTop(0)).toFloat()
                                result.lineCount == 1 ->
                                    (result.getLineBottom(0) - result.getLineTop(0)).toFloat()
                                else -> 0f
                            }
                            if (measured > 1f && kotlin.math.abs(measured - lineHeightPx) > 0.01f) {
                                lineHeightPx = measured
                            }

                            // 逐逻辑行问一次布局：它从哪个视觉行开始、到哪个视觉行结束
                            val src = result.layoutInput.text
                            val tops = ArrayList<Float>()
                            val bottoms = ArrayList<Float>()
                            var start = 0
                            while (true) {
                                val nl = src.indexOf('\n', start)
                                val end = if (nl < 0) src.length else nl
                                val first = result.getLineForOffset(start)
                                val probe = if (end > start) end - 1 else start
                                val last = result.getLineForOffset(probe)
                                tops.add(result.getLineTop(first).toFloat())
                                bottoms.add(result.getLineBottom(last).toFloat())
                                if (nl < 0) break
                                start = nl + 1
                            }
                            if (tops != lineTops) lineTops = tops
                            if (bottoms != lineBottoms) lineBottoms = bottoms
                            val h = result.size.height.toFloat()
                            if (h != layoutHeightPx) layoutHeightPx = h
                        },
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
