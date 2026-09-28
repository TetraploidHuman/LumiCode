package com.lumicode.editor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
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
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlMotion
import com.lumicode.editor.ui.theme.RlSettings
import com.lumicode.editor.ui.theme.RlType
import kotlinx.coroutines.delay

/**
 * 一次文本布局的「视觉行地图」：每个逻辑行占据的视觉范围（px）。
 *
 * 带上 [text] / [width] / [foldKey] 作为指纹 —— 只有和当前渲染完全一致时才会被采用。
 * 折叠隐藏的行 [tops]/[bottoms] 为 [Float.NaN]。
 */
private data class LineMap(
    val text: String,
    val width: Int,
    val foldKey: String,
    val tops: List<Float>,
    val bottoms: List<Float>,
    val height: Float,
) {
    val size: Int get() = tops.size
}

/** 代码区光标宽度（默认 Compose 约 2dp，这里加粗一档）。 */
private val CodeCursorWidth = 3.5.dp

private data class EditSnapshot(val text: String, val selection: TextRange)

/** 显式撤销 / 重做栈；连续输入合并到约 400ms 的同一组。 */
private class EditHistory(private val limit: Int = 120) {
    private val undo = ArrayDeque<EditSnapshot>()
    private val redo = ArrayDeque<EditSnapshot>()
    private var lastPushElapsed = 0L
    private var burstStart: EditSnapshot? = null

    fun clear() {
        undo.clear()
        redo.clear()
        burstStart = null
        lastPushElapsed = 0L
    }

    fun recordBefore(current: TextFieldValue, elapsedMs: Long) {
        if (burstStart != null && elapsedMs - lastPushElapsed < 400L) {
            lastPushElapsed = elapsedMs
            return
        }
        val snap = EditSnapshot(current.text, current.selection)
        undo.addLast(snap)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
        burstStart = snap
        lastPushElapsed = elapsedMs
    }

    fun endBurst() {
        burstStart = null
    }

    fun undo(current: TextFieldValue): TextFieldValue? {
        endBurst()
        val snap = undo.removeLastOrNull() ?: return null
        redo.addLast(EditSnapshot(current.text, current.selection))
        return TextFieldValue(snap.text, snap.selection)
    }

    fun redo(current: TextFieldValue): TextFieldValue? {
        endBurst()
        val snap = redo.removeLastOrNull() ?: return null
        undo.addLast(EditSnapshot(current.text, current.selection))
        return TextFieldValue(snap.text, snap.selection)
    }
}

/**
 * The code surface: gutter + syntax highlighted, editable text.
 *
 * Text is a plain [BasicTextField] whose rendering is decorated by a
 * [VisualTransformation]; that keeps the implementation identical on all targets
 * (Android / desktop / wasm) and avoids platform text APIs entirely.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeEditor(
    path: String,
    text: String,
    language: Language,
    findQuery: String,
    findActiveMatch: Int,
    findCaseSensitive: Boolean = false,
    findRegex: Boolean = false,
    findRangeStart: Int = 0,
    findRangeEnd: Int = -1,
    revealLine: Int?,
    revealSeq: Int = 0,
    problemLines: Map<Int, LineKind>,
    onTextChange: (String) -> Unit,
    onCursorChange: (line: Int, column: Int, selectionLength: Int, selStart: Int, selEnd: Int) -> Unit,
    onSave: () -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var lineHeightPx by remember(path) {
        mutableStateOf(with(density) { RlSettings.codeLineHeight.toPx() })
    }
    val lineHeight = with(density) { lineHeightPx.toDp() }

    var lineMap by remember(path) { mutableStateOf<LineMap?>(null) }
    val verticalScroll = rememberScrollState()

    var value by remember(path) { mutableStateOf(TextFieldValue(text, TextRange(0))) }
    var ensureCursorVisible by remember(path) { mutableStateOf(false) }
    var foldedStarts by remember(path) { mutableStateOf(emptySet<Int>()) }
    var textLayout by remember(path) { mutableStateOf<TextLayoutResult?>(null) }
    var editorFocused by remember(path) { mutableStateOf(false) }
    val editHistory = remember(path) { EditHistory() }
    val editClock = remember(path) { Animatable(0f) }
    LaunchedEffect(path) {
        // 单调时钟，供撤销合并窗口使用（避免依赖多平台 System.currentTimeMillis）
        while (true) {
            delay(50)
            editClock.snapTo(editClock.value + 50f)
        }
    }

    fun publishEdit(next: TextFieldValue, recordUndo: Boolean) {
        if (recordUndo && next.text != value.text) {
            editHistory.recordBefore(value, editClock.value.toLong())
        }
        value = next
        onTextChange(value.text)
        val selection = value.selection
        onCursorChange(
            SyntaxHighlighter.lineOf(value.text, selection.start) + 1,
            SyntaxHighlighter.columnOf(value.text, selection.start) + 1,
            selection.max - selection.min,
            selection.min,
            selection.max,
        )
    }

    fun applyHistory(next: TextFieldValue?) {
        if (next == null) return
        value = next
        onTextChange(value.text)
        ensureCursorVisible = true
        val selection = value.selection
        onCursorChange(
            SyntaxHighlighter.lineOf(value.text, selection.start) + 1,
            SyntaxHighlighter.columnOf(value.text, selection.start) + 1,
            selection.max - selection.min,
            selection.min,
            selection.max,
        )
    }

    val allFolds = remember(value.text) { detectFoldRegions(value.text) }
    val activeFolds = remember(allFolds, foldedStarts) { activeFoldRegions(allFolds, foldedStarts) }
    val foldKey = remember(activeFolds) {
        activeFolds.joinToString(",") { "${it.startLine}-${it.endLine}" }
    }
    val foldMapping = remember(value.text, activeFolds) {
        foldTransformedText(value.text, activeFolds).offsetMapping
    }

    val noBringIntoView = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float = 0f
        }
    }

    // 光标移动时立刻常亮；停住后再开始闪烁，避免「刚好闪灭」时找不到光标。
    var cursorVisible by remember(path) { mutableStateOf(true) }
    LaunchedEffect(value.selection, editorFocused, path) {
        if (!editorFocused) {
            cursorVisible = false
            return@LaunchedEffect
        }
        cursorVisible = true
        delay(520)
        while (true) {
            cursorVisible = false
            delay(520)
            cursorVisible = true
            delay(520)
        }
    }

    val reveal = remember { Animatable(1f) }
    LaunchedEffect(path) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 190, easing = RlMotion.Sharp))
    }

    LaunchedEffect(path) {
        value = TextFieldValue(text, TextRange(0))
        foldedStarts = emptySet()
        editHistory.clear()
        verticalScroll.scrollTo(0)
    }

    LaunchedEffect(text) {
        if (text != value.text) {
            editHistory.recordBefore(value, editClock.value.toLong())
            editHistory.endBurst()
            value = value.copy(
                text = text,
                selection = TextRange(value.selection.start.coerceAtMost(text.length)),
            )
        }
    }

    // 括号结构变了就丢掉失效的折叠。
    LaunchedEffect(allFolds) {
        val valid = allFolds.mapTo(HashSet()) { it.startLine }
        val next = foldedStarts.filter { it in valid }.toSet()
        if (next != foldedStarts) foldedStarts = next
    }

    // 光标进到被折叠的行里时自动展开。
    LaunchedEffect(value.selection.start, foldedStarts, allFolds, value.text) {
        val need = caretLineNeedsUnfold(value.text, value.selection.start, allFolds, foldedStarts)
        if (need.isNotEmpty()) foldedStarts = foldedStarts - need
    }

    LaunchedEffect(revealLine, revealSeq, path) {
        val line = revealLine ?: return@LaunchedEffect
        val need = unfoldsForLine(line - 1, allFolds, foldedStarts)
        if (need.isNotEmpty()) foldedStarts = foldedStarts - need
        val offset = SyntaxHighlighter.offsetOfLine(value.text, line - 1)
        value = value.copy(selection = TextRange(offset))
        verticalScroll.animateScrollTo(((line - 2).coerceAtLeast(0) * lineHeightPx).toInt())
    }

    LaunchedEffect(findQuery, findActiveMatch, findCaseSensitive, findRegex, findRangeStart, findRangeEnd, path) {
        if (findQuery.isEmpty()) return@LaunchedEffect
        val match = SyntaxHighlighter.matchAt(
            value.text,
            findQuery,
            findActiveMatch,
            findCaseSensitive,
            findRegex,
            findRangeStart,
            findRangeEnd,
        ) ?: return@LaunchedEffect
        val line = SyntaxHighlighter.lineOf(value.text, match.start)
        val need = unfoldsForLine(line, allFolds, foldedStarts)
        if (need.isNotEmpty()) foldedStarts = foldedStarts - need
        value = value.copy(selection = TextRange(match.start, match.end))
        verticalScroll.animateScrollTo((line * lineHeightPx).toInt())
    }

    fun toggleFold(startLine: Int) {
        if (foldStartsAt(startLine, allFolds) == null) return
        foldedStarts = if (startLine in foldedStarts) {
            foldedStarts - startLine
        } else {
            foldedStarts + startLine
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = reveal.value
                alpha = 0.15f + 0.85f * p
            },
    ) {
        val lineCount = value.text.count { it == '\n' } + 1
        val showLineNumbers = RlSettings.showLineNumbers
        val gutterWidth = if (showLineNumbers) RlDimens.gutterWidth else 0.dp
        val stale = lineMap
        val usable = stale != null &&
            stale.size == lineCount &&
            stale.foldKey == foldKey
        val tops = if (usable) {
            stale!!.tops
        } else {
            List(lineCount) { i ->
                if (isLineHidden(i, activeFolds)) Float.NaN else i * lineHeightPx
            }
        }
        val bottoms = if (usable) {
            stale!!.bottoms
        } else {
            List(lineCount) { i ->
                if (isLineHidden(i, activeFolds)) Float.NaN else (i + 1) * lineHeightPx
            }
        }
        // 内容高度：优先用行 bottoms（真实逻辑行底），避免 fillMaxSize 文本测量
        // 把「视口高」写回成固定 height()，在 2× DPI 上冲破 Constraints 上限（~262143px）
        // → Can't represent height of 262146。
        val maxPackablePx = 262_142
        val paddingTopPx = with(density) { RlDimens.codePaddingTop.toPx() }
        val textHeightPx = if (usable) {
            bottoms.asSequence().filter { !it.isNaN() }.maxOrNull()
                ?: stale!!.height
        } else {
            val visible = (0 until lineCount).count { !isLineHidden(it, activeFolds) }
            lineHeightPx * visible.coerceAtLeast(1)
        }
        val padBottomPx = with(density) { 28.dp.toPx() }
        val contentHeightPx = (paddingTopPx + textHeightPx + padBottomPx)
            .coerceAtMost(maxPackablePx.toFloat())
        val viewportHeightCapPx = with(density) { maxHeight.toPx() }.coerceAtMost(maxPackablePx.toFloat())
        val totalHeight = with(density) {
            maxOf(viewportHeightCapPx, contentHeightPx).toDp()
        }

        val activeLine = SyntaxHighlighter.lineOf(value.text, value.selection.start) + 1
        val cursorLineIndex = SyntaxHighlighter.lineOf(value.text, value.selection.start)
        val viewportHeightPx = with(density) { maxHeight.toPx() }

        LaunchedEffect(cursorLineIndex, viewportHeightPx, ensureCursorVisible) {
            if (!ensureCursorVisible) return@LaunchedEffect
            ensureCursorVisible = false
            val layoutTop = tops.getOrElse(cursorLineIndex) { cursorLineIndex * lineHeightPx }
            if (layoutTop.isNaN()) return@LaunchedEffect
            val layoutBottom = bottoms.getOrElse(cursorLineIndex) { layoutTop + lineHeightPx }
            val top = paddingTopPx + layoutTop
            val bottom = paddingTopPx + layoutBottom
            val scroll = verticalScroll.value.toFloat()
            when {
                top < scroll -> verticalScroll.scrollTo(top.toInt().coerceAtLeast(0))
                bottom > scroll + viewportHeightPx ->
                    verticalScroll.scrollTo((bottom - viewportHeightPx).toInt().coerceAtLeast(0))
            }
        }

        val activeTopPx = tops.getOrElse(activeLine - 1) { (activeLine - 1) * lineHeightPx }
            .let { if (it.isNaN()) 0f else it }
        val activeBottomPx = bottoms.getOrElse(activeLine - 1) { activeTopPx + lineHeightPx }
            .let { if (it.isNaN()) activeTopPx + lineHeightPx else it }
        val lineOffset by animateDpAsState(
            targetValue = with(density) { activeTopPx.toDp() },
            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
            label = "activeLineOffset",
        )
        val activeSpan = with(density) { (activeBottomPx - activeTopPx).toDp() }

        CompositionLocalProvider(LocalBringIntoViewSpec provides noBringIntoView) {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll),
            ) {
                Row(Modifier.fillMaxWidth().height(totalHeight)) {
                    if (showLineNumbers) {
                        val gutterMeasurer = rememberTextMeasurer()
                        Canvas(
                            Modifier
                                .width(gutterWidth)
                                .fillMaxHeight()
                                .padding(top = RlDimens.codePaddingTop)
                                .pointerInput(lineCount, allFolds, foldedStarts, tops, lineHeightPx) {
                                    detectTapGestures { pos ->
                                        for (i in 0 until lineCount) {
                                            val top = tops.getOrElse(i) { i * lineHeightPx }
                                            if (top.isNaN()) continue
                                            val bottom = bottoms.getOrElse(i) { top + lineHeightPx }
                                            if (pos.y in top..bottom && foldStartsAt(i, allFolds) != null) {
                                                toggleFold(i)
                                                break
                                            }
                                        }
                                    }
                                },
                        ) {
                            val padEnd = 12.dp.toPx()
                            val dot = 4.dp.toPx()
                            val dotGap = 3.dp.toPx()
                            val chevronPad = 6.dp.toPx()
                            val chevron = 7.dp.toPx()
                            for (i in 0 until lineCount) {
                                val top = tops.getOrElse(i) { i * lineHeightPx }
                                if (top.isNaN()) continue
                                if (top < -lineHeightPx || top > size.height) continue

                                val region = foldStartsAt(i, allFolds)
                                if (region != null) {
                                    val cy = top + lineHeightPx / 2f
                                    val cx = chevronPad + chevron / 2f
                                    val folded = i in foldedStarts
                                    val path = Path()
                                    if (folded) {
                                        path.moveTo(cx - chevron * 0.25f, cy - chevron * 0.4f)
                                        path.lineTo(cx + chevron * 0.35f, cy)
                                        path.lineTo(cx - chevron * 0.25f, cy + chevron * 0.4f)
                                        path.close()
                                    } else {
                                        path.moveTo(cx - chevron * 0.4f, cy - chevron * 0.2f)
                                        path.lineTo(cx + chevron * 0.4f, cy - chevron * 0.2f)
                                        path.lineTo(cx, cy + chevron * 0.4f)
                                        path.close()
                                    }
                                    drawPath(
                                        path,
                                        color = if (i + 1 == activeLine) RlColors.Accent else RlColors.Muted,
                                    )
                                }

                                // 逐行 measure 再画，避免预设 size 把第三位裁成「00/01」
                                val label = (i + 1).toString().padStart(3, '0')
                                val labelStyle = RlType.codeGutter.copy(
                                    color = if (i + 1 == activeLine) RlColors.Accent else RlColors.Faint,
                                )
                                val labelLayout = gutterMeasurer.measure(
                                    AnnotatedString(label),
                                    labelStyle,
                                )
                                drawText(
                                    textLayoutResult = labelLayout,
                                    topLeft = Offset(
                                        size.width - padEnd - labelLayout.size.width,
                                        top,
                                    ),
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
                            drawRect(
                                color = RlColors.Accent,
                                topLeft = Offset(0f, lineOffset.toPx() + 3.dp.toPx()),
                                size = Size(3.dp.toPx(), (activeSpan - 6.dp).coerceAtLeast(4.dp).toPx()),
                            )
                        }
                    }

                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .drawBehind {
                                val top = tops.getOrElse(activeLine - 1) { (activeLine - 1) * lineHeightPx }
                                if (top.isNaN()) return@drawBehind
                                val bottom = bottoms.getOrElse(activeLine - 1) { top + lineHeightPx }
                                drawRect(
                                    color = RlColors.AccentSoft,
                                    topLeft = Offset(0f, RlDimens.codePaddingTop.toPx() + lineOffset.toPx()),
                                    size = Size(size.width, (bottom - top)),
                                )
                            },
                    ) {
                        val cursorWidthPx = with(density) { CodeCursorWidth.toPx() }
                        BasicTextField(
                            value = value,
                            onValueChange = { next ->
                                val edited = autoClose(value, next)
                                val textChanged = edited.text != value.text
                                publishEdit(edited, recordUndo = textChanged)
                                if (textChanged) ensureCursorVisible = true
                            },
                            textStyle = RlType.code,
                            // 自绘加粗光标，隐藏系统细光标。
                            cursorBrush = SolidColor(Color.Transparent),
                            onTextLayout = { result ->
                                textLayout = result
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

                                val src = value.text
                                val maxTrans = result.layoutInput.text.length
                                val newTops = ArrayList<Float>(lineCount)
                                val newBottoms = ArrayList<Float>(lineCount)
                                for (line in 0 until lineCount) {
                                    if (isLineHidden(line, activeFolds)) {
                                        newTops.add(Float.NaN)
                                        newBottoms.add(Float.NaN)
                                        continue
                                    }
                                    val orig = SyntaxHighlighter.offsetOfLine(src, line)
                                    val trans = foldMapping.originalToTransformed(orig)
                                        .coerceIn(0, maxTrans)
                                    val visual = result.getLineForOffset(trans)
                                    newTops.add(result.getLineTop(visual).toFloat())
                                    // 逻辑行可能软换行成多行：取该逻辑行最后一个可见字符所在视觉行底
                                    val lineEndOrig = if (line + 1 < lineCount) {
                                        SyntaxHighlighter.offsetOfLine(src, line + 1) - 1
                                    } else {
                                        src.length
                                    }
                                    val endTrans = foldMapping.originalToTransformed(lineEndOrig.coerceAtLeast(orig))
                                        .coerceIn(0, maxTrans)
                                    val visualEnd = result.getLineForOffset(endTrans)
                                    newBottoms.add(result.getLineBottom(visualEnd).toFloat())
                                }
                                val map = LineMap(
                                    text = src,
                                    width = result.size.width,
                                    foldKey = foldKey,
                                    tops = newTops,
                                    bottoms = newBottoms,
                                    height = result.size.height.toFloat(),
                                )
                                if (map != lineMap) lineMap = map
                            },
                            visualTransformation = VisualTransformation { annotated ->
                                if (annotated.text != value.text) {
                                    return@VisualTransformation TransformedText(
                                        annotated,
                                        OffsetMapping.Identity,
                                    )
                                }
                                val folded = foldTransformedText(annotated.text, activeFolds)
                                val caretOrig = value.selection.start
                                val selEndOrig = value.selection.end
                                val caret = folded.offsetMapping
                                    .originalToTransformed(caretOrig)
                                    .coerceIn(0, folded.text.length)
                                val selEnd = folded.offsetMapping
                                    .originalToTransformed(selEndOrig)
                                    .coerceIn(0, folded.text.length)
                                val highlighted = SyntaxHighlighter.highlightEditor(
                                    folded.text.text,
                                    language,
                                    findQuery,
                                    findActiveMatch,
                                    caret,
                                    selEnd,
                                    findCaseSensitive,
                                    findRegex,
                                    findRangeStart,
                                    findRangeEnd,
                                )
                                TransformedText(highlighted, folded.offsetMapping)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = RlDimens.codePaddingStart, top = RlDimens.codePaddingTop)
                                .onFocusChanged { editorFocused = it.isFocused }
                                .drawWithContent {
                                    drawContent()
                                    val layout = textLayout
                                    if (!editorFocused ||
                                        !value.selection.collapsed ||
                                        layout == null ||
                                        !cursorVisible
                                    ) {
                                        return@drawWithContent
                                    }
                                    val trans = foldMapping
                                        .originalToTransformed(value.selection.start)
                                        .coerceIn(0, layout.layoutInput.text.length)
                                    val rect = layout.getCursorRect(trans)
                                    drawRect(
                                        color = RlColors.Accent,
                                        topLeft = Offset(rect.left, rect.top),
                                        size = Size(cursorWidthPx, rect.height),
                                    )
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    val ctrl = event.isCtrlPressed || event.isMetaPressed
                                    when {
                                        event.key == Key.DirectionUp ||
                                            event.key == Key.DirectionDown ||
                                            event.key == Key.PageUp ||
                                            event.key == Key.PageDown ||
                                            event.key == Key.MoveHome ||
                                            event.key == Key.MoveEnd -> {
                                            ensureCursorVisible = true
                                            false
                                        }

                                        // Ctrl+Z 撤销；Ctrl+Y / Ctrl+Shift+Z 重做
                                        ctrl && event.key == Key.Z && !event.isShiftPressed -> {
                                            applyHistory(editHistory.undo(value))
                                            true
                                        }

                                        ctrl && (event.key == Key.Y ||
                                            (event.key == Key.Z && event.isShiftPressed)) -> {
                                            applyHistory(editHistory.redo(value))
                                            true
                                        }

                                        // Ctrl+Shift+[ 折叠当前块；Ctrl+Shift+] 展开
                                        ctrl && event.isShiftPressed && event.key == Key.LeftBracket -> {
                                            foldStartsAt(cursorLineIndex, allFolds)?.let {
                                                toggleFold(it.startLine)
                                            }
                                            true
                                        }

                                        ctrl && event.isShiftPressed && event.key == Key.RightBracket -> {
                                            val atHead = foldStartsAt(cursorLineIndex, allFolds)?.startLine
                                            when {
                                                atHead != null && atHead in foldedStarts ->
                                                    toggleFold(atHead)
                                                else -> {
                                                    val nested = unfoldsForLine(
                                                        cursorLineIndex,
                                                        allFolds,
                                                        foldedStarts,
                                                    )
                                                    nested.forEach { toggleFold(it) }
                                                }
                                            }
                                            true
                                        }

                                        event.key == Key.Tab -> {
                                            val insertion = if (event.isShiftPressed) {
                                                removeIndent(value)
                                            } else {
                                                insertAtCursor(value, " ".repeat(RlSettings.tabWidth))
                                            }
                                            publishEdit(insertion, recordUndo = true)
                                            ensureCursorVisible = true
                                            editHistory.endBurst()
                                            true
                                        }

                                        event.key == Key.Enter && !ctrl -> {
                                            publishEdit(insertNewlineWithIndent(value), recordUndo = true)
                                            ensureCursorVisible = true
                                            editHistory.endBurst()
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
    val extra = if (currentLine.trimEnd().endsWith("{") || currentLine.trimEnd().endsWith("(")) {
        " ".repeat(RlSettings.tabWidth)
    } else {
        ""
    }
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
