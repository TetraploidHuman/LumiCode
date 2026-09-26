package com.lumicode.editor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
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
 * 一次文本布局的「视觉行地图」：每个逻辑行占据的视觉范围（px）。
 *
 * 带上 [text] / [width] 作为指纹 —— 只有和当前渲染的文本、当前布局宽度完全一致时
 * 才会被采用，避免用到上一次布局（比如换行数不同的旧布局）算出的错数据。
 */
private data class LineMap(
    val text: String,
    val width: Int,
    val tops: List<Float>,
    val bottoms: List<Float>,
    val height: Float,
) {
    val size: Int get() = tops.size
}

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
    //
    // 它带着「算它时用的文本 + 布局宽度」一起存（见 [LineMap]），渲染时指纹对不上
    // 就退回等距兜底 —— 这样模型永远不可能张冠李戴。
    var lineMap by remember(path) { mutableStateOf<LineMap?>(null) }
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
        // 只要逻辑行数一致就沿用上一帧的模型。**绝不能因为"文本变了"就退回等距兜底** ——
        // 敲一个字符时文本必然先变、布局后到，退回等距会让整列行号跳一下再跳回来，
        // 那就是"每输入一个字符行号闪一次"。行数变了（回车/删行）才退回等距。
        val stale = lineMap
        val usable = stale != null && stale.size == lineCount
        val tops = if (usable) stale!!.tops else List(lineCount) { it * lineHeightPx }
        val bottoms = if (usable) stale!!.bottoms else List(lineCount) { (it + 1) * lineHeightPx }
        val textHeight = if (usable) {
            with(density) { stale!!.height.toDp() }
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
                //
                // 行号全部在 **draw 阶段**绘制，而不是用 Box + offset 在 layout 阶段定位。
                //
                // 原因：lineMap 是文本在 layout 阶段回写的。同一次 layout 里行号栏已经排完，
                // 只能等下一帧才用上新值 —— 每敲一个字符都会闪一下。draw 阶段在 layout
                // 之后，读到的必然是当前帧的最新值，零延迟。
                if (showLineNumbers) {
                    val gutterMeasurer = rememberTextMeasurer()
                    // 等宽字体，量一次 "000" 就能得到所有行号的宽度
                    val numberWidth = remember(gutterMeasurer, RlSettings.codeFontSize) {
                        gutterMeasurer.measure(AnnotatedString("000"), RlType.codeGutter)
                            .size.width.toFloat()
                    }
                    Canvas(
                        Modifier
                            .width(gutterWidth)
                            .fillMaxHeight()
                            .padding(top = RlDimens.codePaddingTop),
                    ) {
                        val padEnd = 12.dp.toPx()
                        val dot = 4.dp.toPx()
                        val dotGap = 3.dp.toPx()
                        val map = lineMap
                        val t = if (map != null && map.size == lineCount) {
                            map.tops
                        } else {
                            List(lineCount) { it * lineHeightPx }
                        }
                        for (i in 0 until lineCount) {
                            val top = t.getOrElse(i) { i * lineHeightPx }
                            // 画布外的行直接跳过：不然 drawText 会用「画布高度 - topLeft.y」
                            // 当约束，一旦为负就抛 IllegalArgumentException 把整个界面带崩
                            if (top < -lineHeightPx || top > size.height) continue
                            drawText(
                                textMeasurer = gutterMeasurer,
                                text = (i + 1).toString().padStart(3, '0'),
                                topLeft = Offset(size.width - padEnd - numberWidth, top),
                                style = RlType.codeGutter.copy(
                                    color = if (i + 1 == activeLine) RlColors.Accent else RlColors.Faint,
                                ),
                                // 显式给出排版尺寸，避免它自己去减 topLeft
                                size = Size(numberWidth + 1f, lineHeightPx),
                            )
                            problemLines[i + 1]?.let { kind ->
                                drawRect(
                                    color = when (kind) {
                                        LineKind.ERROR -> RlColors.Ink
                                        LineKind.WARN -> RlColors.Muted
                                        else -> RlColors.Faint
                                    },
                                    topLeft = Offset(
                                        size.width - dotGap - dot,
                                        top + (lineHeightPx - dot) / 2f,
                                    ),
                                    size = Size(dot, dot),
                                )
                            }
                        }
                        // 当前行侧边条：跟着光标行平滑移动的冰青小条
                        drawRect(
                            color = RlColors.Accent,
                            topLeft = Offset(0f, lineOffset.toPx() + 3.dp.toPx()),
                            size = Size(3.dp.toPx(), (activeSpan - 6.dp).coerceAtLeast(4.dp).toPx()),
                        )
                    }
                }

                // --------------------------------------------------------- code
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind {
                            val map = lineMap
                            val t = if (map != null && map.size == lineCount) {
                                map.tops
                            } else {
                                List(lineCount) { it * lineHeightPx }
                            }
                            val top = t.getOrElse(activeLine - 1) { (activeLine - 1) * lineHeightPx }
                            val bottom = if (map != null && map.size == lineCount) {
                                map.bottoms.getOrElse(activeLine - 1) { top + lineHeightPx }
                            } else {
                                top + lineHeightPx
                            }
                            drawRect(
                                color = RlColors.AccentSoft,
                                topLeft = Offset(0f, RlDimens.codePaddingTop.toPx() + lineOffset.toPx()),
                                size = Size(size.width, (bottom - top)),
                            )
                        },
                ) {
                    // 当前行：一道横贯整块的极淡冰青。同样画在 draw 阶段，
                    // 所以它和行号、侧边条永远读同一帧的数据，不会各错各的。
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
                            val src = result.layoutInput.text.text
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
                            val map = LineMap(
                                text = src,
                                width = result.size.width,
                                tops = tops,
                                bottoms = bottoms,
                                height = result.size.height.toFloat(),
                            )
                            if (map != lineMap) lineMap = map
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
