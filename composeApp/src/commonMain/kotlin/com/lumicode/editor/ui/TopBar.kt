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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lumicode.editor.platform.platformLabel
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.OverlayMode
import com.lumicode.editor.ui.components.Chip
import com.lumicode.editor.ui.components.GhostButton
import com.lumicode.editor.ui.components.HGap
import com.lumicode.editor.ui.components.Label
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlType

/** Masthead: left title block, right command cluster. */
@Composable
fun TopBar(
    state: IdeState,
    compact: Boolean = false,
    onReinitialize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 26.dp, top = 20.dp, end = 26.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            BasicText(
                text = "LUMICODE",
                style = RlType.title.copy(fontSize = if (compact) 22.sp else 30.sp, lineHeight = if (compact) 22.sp else 30.sp),
            )
            if (!compact) {
                Spacer(Modifier.height(6.dp))
                Label("Synthesize information")
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(
                    text = "ANALYSIS",
                    style = RlType.title.copy(
                        fontSize = if (compact) 22.sp else 30.sp,
                        lineHeight = if (compact) 22.sp else 30.sp,
                        letterSpacing = 0.02.em,
                    ),
                )
                HGap(if (compact) 6.dp else 10.dp)
                BasicText(
                    text = "OS",
                    style = RlType.title.copy(fontSize = if (compact) 22.sp else 30.sp, lineHeight = if (compact) 22.sp else 30.sp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // search / command index field
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Row(
                Modifier
                    .border(1.dp, if (hovered) RlColors.HairStrong else RlColors.Hair)
                    .background(if (hovered) RlColors.Panel else Color.Transparent)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) {
                        state.toggleOverlay(OverlayMode.COMMAND_INDEX)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("◎", style = RlType.mono.copy(fontSize = 12.sp, color = RlColors.Ink))
                if (!compact) {
                    HGap(10.dp)
                    LabelRaw(text = "ARCHIVE INDEX", style = RlType.label(9.sp, RlColors.InkSoft))
                }
                HGap(14.dp)
                Chip(text = "⌘K")
            }
            HGap(if (compact) 14.dp else 26.dp)
            GhostButton(text = if (compact) "" else "New file", glyph = "+", onClick = { state.newFile() })
            HGap(10.dp)
            LabelRaw(
                text = state.openTabs.size.toString().padStart(2, '0'),
                style = RlType.label(9.sp, RlColors.Faint),
            )
            if (!compact) {
                HGap(26.dp)
                LabelRaw(text = "SAVED", style = RlType.label(9.sp, RlColors.Muted))
                HGap(8.dp)
                LabelRaw(
                    text = state.savedCount.toString().padStart(2, '0'),
                    style = RlType.label(9.sp, RlColors.Ink),
                )
            }
            HGap(if (compact) 14.dp else 26.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .clickable { onReinitialize() }
                        .padding(2.dp),
                ) {
                    BasicText("≡", style = RlType.mono.copy(fontSize = 13.sp, color = RlColors.Ink))
                }
                if (!compact) {
                    Spacer(Modifier.height(3.dp))
                    LabelRaw(text = "设置", style = RlType.label(7.5.sp, RlColors.Muted))
                }
            }
        }
    }
}

/** Second row: navigation back-link (mirrors "← ARCHIVE OVERVIEW [ESC]"). */
@Composable
fun NavigationRow(state: IdeState, compact: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 26.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostButton(
            text = "Workspace overview",
            glyph = "←",
            onClick = { state.statusMessage = "WORKSPACE OVERVIEW" },
        )
        HGap(12.dp)
        Chip(text = "esc")
        Spacer(Modifier.weight(1f))
        LabelRaw(
            text = if (compact) {
                state.activePath ?: "—"
            } else {
                "FILE ${state.activeFile?.meta?.archiveNo ?: "X-000"}   ·   ${state.activePath ?: "—"}"
            },
            style = RlType.label(8.5.sp, RlColors.Faint),
        )
    }
}

/** Bottom status strip. */
@Composable
fun StatusBar(state: IdeState, clock: String, compact: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.height(1.dp).fillMaxWidth().background(RlColors.Hair))
        Row(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(RlColors.Paper)
                .padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(7.dp).height(7.dp).background(RlColors.Ink))
            HGap(10.dp)
            LabelRaw(text = state.statusMessage, style = RlType.label(8.5.sp, RlColors.Ink))
            if (!compact) {
                HGap(20.dp)
                LabelRaw(text = platformLabel().uppercase(), style = RlType.label(8.sp, RlColors.Faint))
                HGap(20.dp)
                LabelRaw(
                    text = "${state.openTabs.size} DOC · ${state.dirtyCount} MODIFIED",
                    style = RlType.label(8.sp, RlColors.Faint),
                )
            }
            Spacer(Modifier.weight(1f))
            LabelRaw(
                text = "${state.cursorLine.toString().padStart(3, '0')} : ${state.cursorColumn.toString().padStart(3, '0')}",
                style = RlType.label(8.5.sp, RlColors.Muted),
            )
            if (!compact) {
                HGap(20.dp)
                LabelRaw(text = "JOYCE MOORE", style = RlType.label(8.5.sp, RlColors.Muted))
                HGap(10.dp)
                LabelRaw(text = "/", style = RlType.label(8.5.sp, RlColors.Faint))
            }
            HGap(if (compact) 12.dp else 10.dp)
            LabelRaw(text = clock, style = RlType.label(8.5.sp, RlColors.Ink))
            if (!compact) {
                HGap(20.dp)
                LabelRaw(text = "REINITIALIZE ↗", style = RlType.label(8.5.sp, RlColors.Faint), maxLines = 1)
            }
        }
    }
}

/** Far-right telemetry rail, echoing the vertical data strip of the poster. */
@Composable
fun TelemetryRail(state: IdeState, clock: String, fps: Int, modifier: Modifier = Modifier) {
    val activeLines = state.activeContent.count { it == '\n' } + 1

    @Composable
    fun Metric(label: String, value: String) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            LabelRaw(text = value, style = RlType.monoMicro.copy(color = RlColors.InkSoft, fontSize = 8.sp))
            LabelRaw(text = label, style = RlType.label(7.sp, RlColors.Faint), maxLines = 1)
        }
        Spacer(Modifier.height(9.dp))
    }

    Column(
        modifier
            .width(RlDimens.railWidth)
            .padding(top = 4.dp, end = 16.dp),
        horizontalAlignment = Alignment.End,
    ) {
        LabelRaw(text = clock, style = RlType.monoMicro.copy(color = RlColors.Ink))
        Spacer(Modifier.height(9.dp))
        Metric("udp", "rx 04")
        Metric("fps", fps.toString().padStart(2, '0'))
        Metric("lines", activeLines.toString().padStart(3, '0'))
        Metric("dirty", state.dirtyCount.toString().padStart(2, '0'))
        Metric("prob", state.problems.size.toString().padStart(2, '0'))
        Metric("heap", "2.1 TiB")
        Metric("load", "37 m")
        Metric("error", "0.0%")
        Metric("render", "4K")
        Spacer(Modifier.weight(1f))
        LabelRaw(
            text = "SESSION",
            style = RlType.label(7.sp, RlColors.Faint),
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(26.dp).height(2.dp).background(RlColors.Ink))
        Spacer(Modifier.height(14.dp))
    }
}
