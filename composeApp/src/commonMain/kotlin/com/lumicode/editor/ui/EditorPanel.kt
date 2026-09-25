package com.lumicode.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.model.Language
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.LineKind
import com.lumicode.editor.state.OverlayMode
import com.lumicode.editor.syntax.SyntaxHighlighter
import com.lumicode.editor.ui.components.Chip
import com.lumicode.editor.ui.components.GhostButton
import com.lumicode.editor.ui.components.HGap
import com.lumicode.editor.ui.components.Label
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.components.Rule
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlType

/**
 * Centre column: document tab strip, breadcrumb, find bar, the code surface and
 * a slim action footer.
 */
@Composable
fun EditorPanel(
    state: IdeState,
    onRun: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .background(RlColors.Panel),
    ) {
        TabStrip(state, compact)
        Rule()
        Breadcrumb(state)
        Rule()

        if (state.findVisible) {
            FindBar(state, compact)
            Rule()
        }

        val active = state.activePath
        if (active == null) {
            EmptyDocument()
        } else {
            val problemLines = state.problems
                .filter { it.path == active }
                .associate { it.line to it.severity }

            CodeEditor(
                path = active,
                text = state.contentOf(active),
                language = state.metaOf(active)?.language ?: Language.TEXT,
                findQuery = if (state.findVisible) state.findQuery else "",
                findActiveMatch = state.findActiveMatch,
                revealLine = state.pendingRevealLine,
                problemLines = problemLines,
                onTextChange = { state.updateContent(active, it) },
                onCursorChange = { line, column, selection ->
                    state.cursorLine = line
                    state.cursorColumn = column
                    state.selectionLength = selection
                },
                onSave = { state.save(active) },
                onRun = onRun,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        Rule()
        EditorFooter(state, onRun, compact)
    }
}

@Composable
private fun TabStrip(state: IdeState, compact: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(RlColors.Panel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).fillMaxHeight()) {
            state.openTabs.forEach { path ->
                val active = path == state.activePath
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val name = path.substringAfterLast('/')
                Row(
                    Modifier
                        .fillMaxHeight()
                        .background(if (active) RlColors.Paper else Color.Transparent)
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null) { state.open(path) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = name,
                        style = RlType.mono.copy(
                            fontSize = 11.sp,
                            color = when {
                                active -> RlColors.Ink
                                hovered -> RlColors.InkSoft
                                else -> RlColors.Faint
                            },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.isDirty(path)) {
                        HGap(7.dp)
                        Box(Modifier.size(4.dp).background(RlColors.Ink))
                    }
                    HGap(9.dp)
                    Box(
                        Modifier
                            .clickable { state.close(path) }
                            .padding(2.dp),
                    ) {
                        LabelRaw(
                            text = "×",
                            style = RlType.mono.copy(
                                fontSize = 11.sp,
                                color = if (active) RlColors.Muted else RlColors.Faint,
                            ),
                        )
                    }
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(38.dp)
                            .background(RlColors.Hair),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
            GhostButton(
                text = if (compact) "" else "Files",
                glyph = "▤",
                onClick = { state.explorerVisible = !state.explorerVisible },
            )
            HGap(if (compact) 8.dp else 14.dp)
            GhostButton(
                text = if (compact) "" else "Refs",
                glyph = "▥",
                onClick = { state.referenceVisible = !state.referenceVisible },
            )
            HGap(if (compact) 8.dp else 14.dp)
            GhostButton(text = if (compact) "" else "New", glyph = "+", onClick = { state.newFile() })
            HGap(if (compact) 8.dp else 14.dp)
            GhostButton(
                text = when {
                    compact -> ""
                    state.outputVisible -> "Hide console"
                    else -> "Console"
                },
                glyph = "≡",
                onClick = { state.outputVisible = !state.outputVisible },
            )
            if (!compact) {
                HGap(14.dp)
                Chip(text = "esc")
            }
        }
    }
}

@Composable
private fun Breadcrumb(state: IdeState) {
    val path = state.activePath ?: "—"
    val parts = path.split('/')
    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(RlColors.Panel)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelRaw(text = "←", style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.Muted))
        HGap(10.dp)
        parts.forEachIndexed { index, part ->
            val last = index == parts.lastIndex
            LabelRaw(
                text = if (last) part else "$part /",
                style = RlType.mono.copy(
                    fontSize = 10.sp,
                    color = if (last) RlColors.Ink else RlColors.Faint,
                ),
            )
            if (!last) HGap(6.dp)
        }
        Spacer(Modifier.weight(1f))
        val file = state.activeFile
        if (file != null) {
            LabelRaw(
                text = "NO.${file.meta.archiveNo}",
                style = RlType.label(8.sp, RlColors.Muted),
            )
            HGap(14.dp)
            LabelRaw(
                text = file.language.label.uppercase(),
                style = RlType.label(8.sp, RlColors.Faint),
            )
            HGap(14.dp)
        }
        LabelRaw(text = "UTF-8 · LF", style = RlType.label(8.sp, RlColors.Faint))
    }
}

@Composable
private fun FindBar(state: IdeState, compact: Boolean) {
    val focus = remember { FocusRequester() }
    val matches = SyntaxHighlighter.countMatches(state.activeContent, state.findQuery)
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(RlColors.PaperDeep)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            Label("Find / 查找")
            HGap(14.dp)
        }
        BasicTextField(
            value = TextFieldValue(state.findQuery, TextRange(state.findQuery.length)),
            onValueChange = {
                state.findQuery = it.text
                state.findActiveMatch = 0
            },
            singleLine = true,
            textStyle = RlType.mono.copy(fontSize = 11.sp, color = RlColors.Ink),
            cursorBrush = SolidColor(RlColors.Ink),
            modifier = Modifier
                .then(if (compact) Modifier.weight(1f) else Modifier.width(240.dp))
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter -> {
                            if (matches > 0) state.findActiveMatch = (state.findActiveMatch + 1) % matches
                            true
                        }

                        Key.Escape -> {
                            state.findVisible = false
                            true
                        }

                        else -> false
                    }
                },
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { inner() }
                }
            },
        )
        HGap(14.dp)
        LabelRaw(
            text = if (matches == 0) "000 / 000" else "${(state.findActiveMatch + 1).toString().padStart(3, '0')} / ${matches.toString().padStart(3, '0')}",
            style = RlType.monoMicro.copy(color = if (matches == 0) RlColors.Faint else RlColors.Ink),
        )
        if (!compact) Spacer(Modifier.weight(1f))
        HGap(12.dp)
        GhostButton(text = if (compact) "" else "Next", glyph = "→", glyphLeading = false, onClick = {
            if (matches > 0) state.findActiveMatch = (state.findActiveMatch + 1) % matches
        })
        HGap(if (compact) 8.dp else 16.dp)
        GhostButton(
            text = if (compact) "" else "Close",
            glyph = "×",
            glyphLeading = false,
            onClick = { state.findVisible = false },
        )
    }
}

@Composable
private fun EmptyDocument() {
    Box(Modifier.fillMaxSize().background(RlColors.Panel), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Label("No document open")
            Spacer(Modifier.height(8.dp))
            LabelRaw(text = "NO.000", style = RlType.title.copy(fontSize = 40.sp, color = RlColors.Hair))
        }
    }
}

@Composable
private fun EditorFooter(state: IdeState, onRun: () -> Unit, compact: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(RlColors.Panel)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Row(
            Modifier
                .background(if (hovered) Color(0xFF2C2C29) else RlColors.Ink)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) { onRun() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelRaw(text = "▶", style = RlType.mono.copy(fontSize = 9.sp, color = Color.White))
            HGap(9.dp)
            LabelRaw(text = "RUN ANALYSIS", style = RlType.label(8.5.sp, Color.White))
            HGap(16.dp)
            LabelRaw(text = "F5", style = RlType.label(8.sp, Color(0xFFB9B9B3)))
        }
        HGap(if (compact) 10.dp else 16.dp)
        GhostButton(text = if (compact) "" else "Save", glyph = "⌘S", glyphLeading = false, onClick = {
            state.activePath?.let { state.save(it) }
        })
        HGap(if (compact) 10.dp else 16.dp)
        GhostButton(text = if (compact) "" else "Find", glyph = "⌘F", glyphLeading = false, onClick = {
            state.findVisible = !state.findVisible
        })
        if (!compact) {
            HGap(16.dp)
            GhostButton(text = "Command index", glyph = "⌘K", glyphLeading = false, onClick = {
                state.toggleOverlay(OverlayMode.COMMAND_INDEX)
            })
        }
        Spacer(Modifier.weight(1f))
        val stats = "LNF ${state.cursorLine.toString().padStart(3, '0')} : COL ${state.cursorColumn.toString().padStart(3, '0')}"
        LabelRaw(text = stats, style = RlType.label(8.5.sp, RlColors.Muted))
        if (!compact) {
            HGap(18.dp)
            LabelRaw(
                text = "${state.activeContent.length.toString().padStart(5, '0')} CHARS",
                style = RlType.label(8.5.sp, RlColors.Faint),
            )
        }
    }
}

/** Bottom analysis console: terminal, problems and the archival access log. */
@Composable
fun OutputPanel(state: IdeState, modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(0) }

    Column(
        modifier
            .fillMaxWidth()
            .height(188.dp)
            .background(RlColors.PaperDeep),
    ) {
        Rule()
        Row(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                "01 TERMINAL" to 0,
                "02 PROBLEMS" to 1,
                "03 ACCESS LOG" to 2,
            ).forEach { (title, index) ->
                val active = tab == index
                Column(Modifier.clickable { tab = index }.padding(end = 20.dp)) {
                    LabelRaw(
                        text = title,
                        style = RlType.label(9.sp, if (active) RlColors.Ink else RlColors.Faint),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .height(1.dp)
                            .fillMaxWidth()
                            .background(if (active) RlColors.Ink else Color.Transparent),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val count = when (tab) {
                0 -> state.terminal.size
                1 -> state.problems.size
                else -> state.log.size
            }
            LabelRaw(text = count.toString().padStart(3, '0'), style = RlType.label(8.5.sp, RlColors.Muted))
            HGap(16.dp)
            GhostButton(text = "Hide", glyph = "×", glyphLeading = false, onClick = { state.outputVisible = false })
        }
        Rule()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, end = 12.dp),
        ) {
            when (tab) {
                0 -> state.terminal.takeLast(9).forEach { line ->
                    Row(Modifier.fillMaxWidth()) {
                        LabelRaw(text = line.time, style = RlType.monoMicro.copy(color = RlColors.Faint))
                        HGap(12.dp)
                        BasicText(
                            text = line.text,
                            style = RlType.mono.copy(
                                fontSize = 10.5.sp,
                                color = when (line.kind) {
                                    LineKind.OK -> RlColors.CodeString
                                    LineKind.WARN -> RlColors.CodeAnnotation
                                    LineKind.ERROR -> Color(0xFF8C3A2B)
                                    LineKind.MUTED -> RlColors.Faint
                                    else -> RlColors.InkSoft
                                },
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                1 -> if (state.problems.isEmpty()) {
                    Label("No findings · document clean", style = RlType.label(9.sp, RlColors.Faint))
                } else {
                    state.problems.take(7).forEach { problem ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    state.open(problem.path, revealLine = problem.line)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(4.dp)
                                    .background(if (problem.severity == LineKind.ERROR) RlColors.Ink else RlColors.Muted),
                            )
                            HGap(10.dp)
                            LabelRaw(
                                text = problem.path.substringAfterLast('/'),
                                style = RlType.mono.copy(fontSize = 10.5.sp, color = RlColors.InkSoft),
                            )
                            HGap(10.dp)
                            LabelRaw(
                                text = "${problem.line}:${problem.column}",
                                style = RlType.monoMicro.copy(color = RlColors.Muted),
                            )
                            HGap(12.dp)
                            LabelRaw(
                                text = problem.message,
                                style = RlType.mono.copy(fontSize = 10.5.sp, color = RlColors.Muted),
                            )
                        }
                    }
                }

                else -> state.log.takeLast(8).forEach { entry ->
                    Row(Modifier.fillMaxWidth()) {
                        LabelRaw(text = entry.time, style = RlType.monoMicro.copy(color = RlColors.Faint))
                        HGap(12.dp)
                        LabelRaw(text = entry.text, style = RlType.mono.copy(fontSize = 10.5.sp, color = RlColors.InkSoft))
                    }
                }
            }
        }
    }
}
