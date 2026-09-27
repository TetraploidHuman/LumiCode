package com.lumicode.editor.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import com.lumicode.editor.syntax.SyntaxHighlighter

/**
 * A foldable brace block: [startLine] holds `{`, [endLine] holds the matching `}`
 * (both 0-based, inclusive). Folding hides (startLine, endLine].
 */
data class FoldRegion(val startLine: Int, val endLine: Int) {
    init {
        require(endLine > startLine) { "empty fold $startLine..$endLine" }
    }

    fun hides(line: Int): Boolean = line > startLine && line <= endLine
}

/** Placeholder appended to the header line when a block is folded (`{` … `}`). */
const val FOLD_PLACEHOLDER = " … }"

fun detectFoldRegions(text: String): List<FoldRegion> {
    val regions = ArrayList<FoldRegion>()
    val stack = ArrayDeque<Int>()
    var line = 0
    for (ch in text) {
        when (ch) {
            '\n' -> line++
            '{' -> stack.addLast(line)
            '}' -> {
                val start = stack.removeLastOrNull() ?: continue
                if (line > start) regions += FoldRegion(start, line)
            }
        }
    }
    return regions
}

/** Drop regions nested inside another currently-folded region. */
fun activeFoldRegions(
    all: List<FoldRegion>,
    foldedStarts: Set<Int>,
): List<FoldRegion> {
    val folded = all.filter { it.startLine in foldedStarts }.sortedBy { it.startLine }
    if (folded.isEmpty()) return emptyList()
    val active = ArrayList<FoldRegion>()
    for (region in folded) {
        val covered = active.any { region.startLine > it.startLine && region.startLine <= it.endLine }
        if (!covered) active += region
    }
    return active
}

fun isLineHidden(line: Int, activeFolds: List<FoldRegion>): Boolean =
    activeFolds.any { it.hides(line) }

fun foldStartsAt(line: Int, all: List<FoldRegion>): FoldRegion? =
    all.firstOrNull { it.startLine == line }

/**
 * Build the visible buffer: keep the fold header line, append [FOLD_PLACEHOLDER],
 * drop hidden lines through the matching `}`.
 */
fun foldTransformedText(
    original: String,
    activeFolds: List<FoldRegion>,
): TransformedText {
    if (activeFolds.isEmpty() || original.isEmpty()) {
        return TransformedText(AnnotatedString(original), OffsetMapping.Identity)
    }

    val lineCount = original.count { it == '\n' } + 1
    val hidden = BooleanArray(lineCount) { isLineHidden(it, activeFolds) }
    val foldAt = activeFolds.associateBy { it.startLine }

    val lineStarts = IntArray(lineCount)
    run {
        var ls = 0
        var line = 0
        lineStarts[0] = 0
        for (i in original.indices) {
            if (original[i] == '\n') {
                line++
                if (line < lineCount) {
                    ls = i + 1
                    lineStarts[line] = ls
                }
            }
        }
    }

    fun lineEnd(line: Int): Int {
        if (line + 1 < lineCount) return lineStarts[line + 1] - 1
        return original.length
    }

    val out = StringBuilder(original.length)
    val o2t = IntArray(original.length + 1) { -1 }
    val t2o = ArrayList<Int>(original.length)

    fun emitOriginalChar(origIndex: Int) {
        o2t[origIndex] = out.length
        out.append(original[origIndex])
        t2o.add(origIndex)
    }

    fun emitPlaceholder(anchorOrig: Int) {
        for (ch in FOLD_PLACEHOLDER) {
            t2o.add(anchorOrig)
            out.append(ch)
        }
    }

    for (line in 0 until lineCount) {
        if (hidden[line]) continue
        val start = lineStarts[line]
        val end = lineEnd(line)
        var i = start
        while (i < end && i < original.length && original[i] != '\n') {
            emitOriginalChar(i)
            i++
        }
        if (foldAt[line] != null) {
            emitPlaceholder(end.coerceIn(0, original.length))
        }
        if (end < original.length && original[end] == '\n') {
            emitOriginalChar(end)
        }
    }
    o2t[original.length] = out.length

    fun mapHidden(origIndex: Int): Int {
        val line = SyntaxHighlighter.lineOf(original, origIndex.coerceAtMost(original.length))
        val fold = activeFolds.firstOrNull { it.hides(line) } ?: return out.length
        val headerNl = lineEnd(fold.startLine)
        val bodyLast = (headerNl - 1).coerceAtLeast(lineStarts[fold.startLine])
        val afterBody = when {
            bodyLast >= lineStarts[fold.startLine] &&
                bodyLast < original.length &&
                original[bodyLast] != '\n' &&
                o2t[bodyLast] >= 0 -> o2t[bodyLast] + 1
            else -> o2t[lineStarts[fold.startLine]].coerceAtLeast(0)
        }
        return afterBody.coerceIn(0, out.length)
    }

    for (i in o2t.indices) {
        if (o2t[i] < 0) o2t[i] = mapHidden(i)
    }

    val transformed = out.toString()
    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int =
            o2t[offset.coerceIn(0, original.length)]

        override fun transformedToOriginal(offset: Int): Int {
            if (transformed.isEmpty()) return 0
            val t = offset.coerceIn(0, transformed.length)
            if (t >= t2o.size) return original.length
            return t2o[t].coerceIn(0, original.length)
        }
    }

    return TransformedText(AnnotatedString(transformed), mapping)
}

fun unfoldsForLine(line: Int, all: List<FoldRegion>, foldedStarts: Set<Int>): Set<Int> =
    all.asSequence()
        .filter { it.startLine in foldedStarts && it.hides(line) }
        .map { it.startLine }
        .toSet()

fun caretLineNeedsUnfold(
    text: String,
    caret: Int,
    all: List<FoldRegion>,
    foldedStarts: Set<Int>,
): Set<Int> {
    val line = SyntaxHighlighter.lineOf(text, caret)
    return unfoldsForLine(line, all, foldedStarts)
}
