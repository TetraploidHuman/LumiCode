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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.lumicode.editor.state.IdeCommand
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.OverlayMode
import com.lumicode.editor.ui.components.Chip
import com.lumicode.editor.ui.components.HGap
import com.lumicode.editor.ui.components.Label
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlType

private data class PaletteRow(
    val index: String,
    val title: String,
    val secondary: String,
    val trailing: String,
    val group: String,
    val action: () -> Unit,
)

/**
 * Full-screen overlay for COMMAND INDEX (Ctrl/Cmd+K) and QUICK OPEN (Ctrl/Cmd+P).
 */
@Composable
fun OverlayHost(state: IdeState, commands: List<IdeCommand>) {
    val mode = state.overlay
    if (mode == OverlayMode.NONE) return

    val query = state.overlayQuery
    val rows: List<PaletteRow> = when (mode) {
        OverlayMode.COMMAND_INDEX -> commands
            .filter {
                query.isEmpty() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.chinese.contains(query) ||
                    it.id.contains(query, ignoreCase = true)
            }
            .mapIndexed { index, command ->
                PaletteRow(
                    index = (index + 1).toString().padStart(2, '0'),
                    title = command.title,
                    secondary = command.chinese,
                    trailing = command.shortcut,
                    group = command.group,
                    action = command.action,
                )
            }

        OverlayMode.QUICK_OPEN -> state.quickOpenTargets()
            .filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
            .mapIndexed { index, path ->
                PaletteRow(
                    index = (index + 1).toString().padStart(2, '0'),
                    title = path.substringAfterLast('/'),
                    secondary = path,
                    trailing = (state.metaOf(path)?.language?.short ?: "TXT"),
                    group = "ARCHIVE / ${path.substringBeforeLast('/', "root")}",
                    action = { state.open(path) },
                )
            }

        OverlayMode.NONE -> emptyList()
    }

    var selected by remember(mode, query) { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    var field by remember(mode) { mutableStateOf(TextFieldValue("", TextRange(0))) }

    LaunchedEffect(mode) {
        field = TextFieldValue("", TextRange(0))
        focus.requestFocus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(RlColors.Scrim)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                state.overlay = OverlayMode.NONE
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 96.dp)
                .width(620.dp)
                .background(RlColors.Panel)
                .border(1.dp, RlColors.HairStrong)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            state.overlay = OverlayMode.NONE
                            true
                        }

                        Key.DirectionDown -> {
                            if (rows.isNotEmpty()) selected = (selected + 1) % rows.size
                            true
                        }

                        Key.DirectionUp -> {
                            if (rows.isNotEmpty()) selected = (selected - 1 + rows.size) % rows.size
                            true
                        }

                        Key.Enter, Key.NumPadEnter -> {
                            rows.getOrNull(selected)?.action?.invoke()
                            state.overlay = OverlayMode.NONE
                            true
                        }

                        else -> false
                    }
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(
                    if (mode == OverlayMode.COMMAND_INDEX) "Command index" else "Quick open",
                    style = RlType.label(9.sp, RlColors.Ink),
                )
                HGap(18.dp)
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        state.overlayQuery = it.text
                    },
                    singleLine = true,
                    textStyle = RlType.mono.copy(fontSize = 12.sp, color = RlColors.Ink),
                    cursorBrush = SolidColor(RlColors.Ink),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { inner() }
                        }
                    },
                )
                HGap(18.dp)
                LabelRaw(
                    text = "${rows.size.toString().padStart(3, '0')} ENTRIES",
                    style = RlType.label(8.sp, RlColors.Faint),
                )
                HGap(14.dp)
                Chip(text = "esc")
            }
            Box(Modifier.height(1.dp).fillMaxWidth().background(RlColors.Ink))

            Column(Modifier.fillMaxWidth().height(320.dp)) {
                if (rows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Label("No matching entries")
                    }
                } else {
                    rows.take(9).forEachIndexed { index, row ->
                        val active = index == selected
                        val interaction = remember { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        active -> RlColors.PaperDeep
                                        hovered -> RlColors.Paper
                                        else -> Color.Transparent
                                    },
                                )
                                .hoverable(interaction)
                                .clickable(interactionSource = interaction, indication = null) {
                                    row.action()
                                    state.overlay = OverlayMode.NONE
                                }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LabelRaw(text = row.index, style = RlType.monoMicro.copy(color = RlColors.Faint))
                                HGap(14.dp)
                                BasicText(
                                    text = row.title,
                                    style = RlType.body.copy(
                                        fontSize = 12.5.sp,
                                        color = if (active) RlColors.Ink else RlColors.InkSoft,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                HGap(10.dp)
                                LabelRaw(text = row.secondary, style = RlType.label(8.5.sp, RlColors.Faint))
                                Spacer(Modifier.weight(1f))
                                LabelRaw(
                                    text = row.trailing,
                                    style = RlType.label(8.5.sp, if (active) RlColors.Ink else RlColors.Muted),
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            LabelRaw(text = row.group, style = RlType.label(7.5.sp, RlColors.Faint))
                        }
                        Box(Modifier.height(1.dp).fillMaxWidth().background(RlColors.Hair))
                    }
                }
            }
            Box(Modifier.height(1.dp).fillMaxWidth().background(RlColors.Hair))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabelRaw(text = "↑ ↓ NAVIGATE", style = RlType.label(7.5.sp, RlColors.Faint))
                HGap(18.dp)
                LabelRaw(text = "→ EXECUTE", style = RlType.label(7.5.sp, RlColors.Faint))
                Spacer(Modifier.weight(1f))
                LabelRaw(
                    text = if (mode == OverlayMode.COMMAND_INDEX) "ANALYSIS OS · INDEX" else "ANALYSIS OS · ARCHIVE",
                    style = RlType.label(7.5.sp, RlColors.Faint),
                )
            }
        }
    }
}
