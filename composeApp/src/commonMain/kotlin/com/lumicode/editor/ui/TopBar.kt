package com.lumicode.editor.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.platform.platformLabel
import com.lumicode.editor.LUMICODE_STAMP
import com.lumicode.editor.LUMICODE_VERSION
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.OverlayMode
import com.lumicode.editor.ui.components.AccentTick
import com.lumicode.editor.ui.components.Chip
import com.lumicode.editor.ui.components.GearIcon
import com.lumicode.editor.ui.components.GhostButton
import com.lumicode.editor.ui.components.HGap
import com.lumicode.editor.ui.components.Label
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.components.wash
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
            .padding(start = RlDimens.pagePad, top = 18.dp, end = RlDimens.pagePad, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            // 报头：靠字重对比拉层级 —— 黑体粗字 + 细字，不用分栏线
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(
                    text = "LUMICODE",
                    style = RlType.title.copy(
                        fontSize = if (compact) 19.sp else 30.sp,
                        lineHeight = if (compact) 21.sp else 31.sp,
                    ),
                )
                HGap(if (compact) 7.dp else 13.dp)
                BasicText(
                    text = "ANALYSIS OS",
                    style = RlType.titleSoft.copy(
                        fontSize = if (compact) 19.sp else 30.sp,
                        lineHeight = if (compact) 21.sp else 31.sp,
                    ),
                )
            }
            if (!compact) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccentTick(length = 18.dp)
                    HGap(10.dp)
                    LabelRaw(
                        text = "信息综合处理 · 代码档案工作台",
                        style = RlType.label(9.5.sp, RlColors.Faint),
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // 档案检索 / 命令面板入口：无边药丸，hover 时浮起一层
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Row(
                Modifier
                    .wash(if (hovered) RlColors.Panel else Color.Transparent)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) {
                        state.toggleOverlay(OverlayMode.COMMAND_INDEX)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    "◎",
                    style = RlType.mono.copy(
                        fontSize = 13.sp,
                        color = if (hovered) RlColors.Accent else RlColors.Ink,
                    ),
                )
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
                    .wash(if (gearHovered) RlColors.Panel else Color.Transparent)
                    .hoverable(gearInteraction)
                    .clickable(interactionSource = gearInteraction, indication = null) { onOpenSettings() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GearIcon(size = 16.dp, color = if (gearHovered) RlColors.Accent else RlColors.Ink)
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
            .padding(start = RlDimens.pagePad, end = RlDimens.pagePad, top = 4.dp, bottom = 10.dp),
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
                fill = if (escHovered) RlColors.AccentSoft else RlColors.PaperDeep,
                textColor = if (escHovered) RlColors.AccentDeep else RlColors.Muted,
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

/** 底部状态条。无分界线 —— 只是浮在场地上的细字，边缘自然淡出。 */
@Composable
fun StatusBar(state: IdeState, clock: String, compact: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(horizontal = RlDimens.pagePad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 活信号：冰青圆点 + 一圈柔光
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(13.dp).wash(RlColors.AccentSoft))
                Box(Modifier.size(5.dp).wash(RlColors.Accent))
            }
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
                // 版本 + 构建戳：一眼分辨「你看的是不是最新构建」
                LabelRaw(
                    text = "v$LUMICODE_VERSION · $LUMICODE_STAMP",
                    style = RlType.label(10.sp, RlColors.Muted),
                    maxLines = 1,
                )
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
            .padding(top = 4.dp, end = RlDimens.pagePad),
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
        Box(
            Modifier
                .width(26.dp)
                .height(3.dp)
                .wash(RlColors.Accent.copy(alpha = 0.75f)),
        )
        Spacer(Modifier.height(14.dp))
    }
}
