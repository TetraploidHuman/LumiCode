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
import com.lumicode.editor.ui.components.GearIcon
import com.lumicode.editor.ui.components.GhostButton
import com.lumicode.editor.ui.components.HGap
import com.lumicode.editor.ui.components.Label
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlType

/** 报头：左侧标题块，右侧命令组。 */
@Composable
fun TopBar(
    state: IdeState,
    compact: Boolean = false,
    onOpenSettings: () -> Unit,
    onOpenOverview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 26.dp, top = 18.dp, end = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            // 两个标题放在同一行：LUMICODE ANALYSIS OS
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(
                    text = "LUMICODE",
                    style = RlType.title.copy(
                        fontSize = if (compact) 19.sp else 30.sp,
                        lineHeight = if (compact) 21.sp else 31.sp,
                    ),
                )
                HGap(if (compact) 7.dp else 14.dp)
                BasicText(
                    text = "ANALYSIS",
                    style = RlType.title.copy(
                        fontSize = if (compact) 19.sp else 30.sp,
                        lineHeight = if (compact) 21.sp else 31.sp,
                        letterSpacing = 0.02.em,
                    ),
                )
                HGap(if (compact) 4.dp else 8.dp)
                BasicText(
                    text = "OS",
                    style = RlType.title.copy(
                        fontSize = if (compact) 19.sp else 30.sp,
                        lineHeight = if (compact) 21.sp else 31.sp,
                    ),
                )
            }
            if (!compact) {
                Spacer(Modifier.height(7.dp))
                Label("信息综合处理 · 代码档案工作台")
            }
        }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // 档案检索 / 命令面板入口
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
                BasicText("◎", style = RlType.mono.copy(fontSize = 13.sp, color = RlColors.Ink))
                if (!compact) {
                    HGap(10.dp)
                    Label("档案检索", style = RlType.label(11.sp, RlColors.InkSoft))
                    HGap(14.dp)
                    Chip(text = "⌘K")
                }
            }
            HGap(if (compact) 14.dp else 22.dp)
            GhostButton(text = if (compact) "" else "新建文件", glyph = "+", onClick = { state.newFile() })
            if (!compact) {
                HGap(8.dp)
                LabelRaw(
                    text = state.openTabs.size.toString().padStart(2, '0'),
                    style = RlType.label(11.sp, RlColors.Faint),
                )
            }
            if (!compact) {
                HGap(22.dp)
                LabelRaw(text = "已保存", style = RlType.label(11.sp, RlColors.Muted))
                HGap(8.dp)
                LabelRaw(
                    text = state.savedCount.toString().padStart(2, '0'),
                    style = RlType.label(11.sp, RlColors.Ink),
                )
            }
            HGap(if (compact) 14.dp else 22.dp)
            // 设置入口
            val gearInteraction = remember { MutableInteractionSource() }
            val gearHovered by gearInteraction.collectIsHoveredAsState()
            Row(
                Modifier
                    .background(if (gearHovered) RlColors.PaperDeep else Color.Transparent)
                    .hoverable(gearInteraction)
                    .clickable(interactionSource = gearInteraction, indication = null) { onOpenSettings() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GearIcon(size = 16.dp)
                if (!compact) {
                    HGap(7.dp)
                    Label("设置", style = RlType.label(11.sp, RlColors.InkSoft))
                }
            }
        }
    }
}

/** 第二行：返回工作区总览（对应参考图里的 "← ARCHIVE OVERVIEW [ESC]"）。 */
@Composable
fun NavigationRow(
    state: IdeState,
    compact: Boolean = false,
    onOpenOverview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 26.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostButton(
            text = "工作区总览",
            glyph = "←",
            onClick = onOpenOverview,
        )
        HGap(12.dp)
        val escInteraction = remember { MutableInteractionSource() }
        val escHovered by escInteraction.collectIsHoveredAsState()
        Box(
            Modifier
                .hoverable(escInteraction)
                .clickable(interactionSource = escInteraction, indication = null) { onOpenOverview() },
        ) {
            Chip(
                text = "ESC",
                borderColor = if (escHovered) RlColors.Ink else RlColors.HairStrong,
                textColor = if (escHovered) RlColors.Ink else RlColors.Muted,
            )
        }
        Spacer(Modifier.weight(1f))
        LabelRaw(
            text = if (compact) {
                state.activePath ?: "—"
            } else {
                "档案 ${state.activeFile?.meta?.archiveNo ?: "X-000"}   ·   ${state.activePath ?: "—"}"
            },
            style = RlType.label(10.sp, RlColors.Faint),
        )
    }
}

/** 底部状态条。 */
@Composable
fun StatusBar(state: IdeState, clock: String, compact: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.height(1.dp).fillMaxWidth().background(RlColors.Hair))
        Row(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(RlColors.Paper)
                .padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(7.dp).height(7.dp).background(RlColors.Ink))
            HGap(10.dp)
            LabelRaw(text = state.statusMessage, style = RlType.label(10.sp, RlColors.Ink))
            if (!compact) {
                HGap(20.dp)
                LabelRaw(text = platformLabel(), style = RlType.label(10.sp, RlColors.Faint))
                HGap(20.dp)
                LabelRaw(
                    text = "${state.openTabs.size} 个文档 · ${state.dirtyCount} 个未保存",
                    style = RlType.label(10.sp, RlColors.Faint),
                )
            }
            Spacer(Modifier.weight(1f))
            LabelRaw(
                text = "行 ${state.cursorLine}  列 ${state.cursorColumn}",
                style = RlType.label(10.sp, RlColors.Muted),
            )
            if (!compact) {
                HGap(18.dp)
                LabelRaw(text = "乔伊斯·摩尔", style = RlType.label(10.sp, RlColors.Muted))
            }
            HGap(if (compact) 12.dp else 10.dp)
            LabelRaw(text = clock, style = RlType.label(10.sp, RlColors.Ink))
            if (!compact) {
                HGap(18.dp)
                LabelRaw(text = "重置会话", style = RlType.label(10.sp, RlColors.Faint), maxLines = 1)
            }
        }
    }
}

/** 最右侧遥测栏（对应参考图右缘的竖排数据条）。 */
@Composable
fun TelemetryRail(state: IdeState, clock: String, fps: Int, modifier: Modifier = Modifier) {
    val activeLines = state.activeContent.count { it == '\n' } + 1

    @Composable
    fun Metric(label: String, value: String) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            LabelRaw(text = value, style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.InkSoft))
            LabelRaw(text = label, style = RlType.label(9.5.sp, RlColors.Faint), maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
    }

    Column(
        modifier
            .width(RlDimens.railWidth)
            .padding(top = 4.dp, end = 16.dp),
        horizontalAlignment = Alignment.End,
    ) {
        LabelRaw(text = clock, style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.Ink))
        Spacer(Modifier.height(10.dp))
        Metric("帧率", fps.toString().padStart(2, '0'))
        Metric("行数", activeLines.toString().padStart(3, '0'))
        Metric("未存", state.dirtyCount.toString().padStart(2, '0'))
        Metric("问题", state.problems.size.toString().padStart(2, '0'))
        Metric("内存", "2.1G")
        Metric("负载", "37%")
        Metric("渲染", "4K")
        Spacer(Modifier.weight(1f))
        LabelRaw(
            text = "会话",
            style = RlType.label(9.5.sp, RlColors.Faint),
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(26.dp).height(2.dp).background(RlColors.Ink))
        Spacer(Modifier.height(14.dp))
    }
}
