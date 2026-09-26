package com.lumicode.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
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
import com.lumicode.editor.platform.platformLabel
import com.lumicode.editor.state.IdeCommand
import com.lumicode.editor.LUMICODE_STAMP
import com.lumicode.editor.LUMICODE_VERSION
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.OverlayMode
import com.lumicode.editor.ui.components.AccentTick
import com.lumicode.editor.ui.components.Chip
import com.lumicode.editor.ui.components.HGap
import com.lumicode.editor.ui.components.Label
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlMotion
import com.lumicode.editor.ui.theme.RlSettings
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
 * 浮层总入口：命令面板 / 快速打开 / 设置 / 工作区总览。
 *
 * 页面切换的动效分两层：厚纱快速淡入淡出，内容再做"上浮 + 微放大 + 淡入"。
 * 用 [MutableTransitionState] 而不是 `if`，是为了让**关闭**也有动画 ——
 * 内容在退场期间继续渲染上一次的浮层。
 */
@Composable
fun OverlayHost(state: IdeState, commands: List<IdeCommand>, compact: Boolean = false) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = state.overlay != OverlayMode.NONE

    // 退场时 state.overlay 已经是 NONE，所以得自己记住"刚才那一页"
    var lastMode by remember { mutableStateOf(OverlayMode.COMMAND_INDEX) }
    if (state.overlay != OverlayMode.NONE) lastMode = state.overlay

    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(RlMotion.enter(150)),
        exit = fadeOut(RlMotion.exit()),
        label = "scrim",
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(RlColors.Scrim)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { state.overlay = OverlayMode.NONE },
        )
    }

    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(RlMotion.enter(160)) +
            scaleIn(RlMotion.enter(190), initialScale = 0.988f) +
            slideInVertically(RlMotion.enter(190)) { -it / 56 },
        exit = fadeOut(RlMotion.exit(90)) +
            scaleOut(RlMotion.exit(120), targetScale = 0.992f),
        label = "sheet",
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (lastMode) {
                OverlayMode.COMMAND_INDEX, OverlayMode.QUICK_OPEN -> PaletteSheet(state, commands, compact)
                OverlayMode.SETTINGS -> SettingsOverlay(state, compact)
                OverlayMode.OVERVIEW -> OverviewOverlay(state, compact)
                OverlayMode.NONE -> Unit
            }
        }
    }
}

/** 浮层通用外壳：标题 + ESC + 细线 + 内容。 */
@Composable
private fun SheetScaffold(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .padding(top = if (compact) 24.dp else 84.dp, start = 12.dp, end = 12.dp)
            .width(if (compact) 0.dp else 700.dp)
            .then(if (compact) Modifier.fillMaxWidth() else Modifier)
            .heightIn(max = 640.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            },
    ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentTick(length = 20.dp)
                HGap(11.dp)
                BasicText(title, style = RlType.sectionTitle.copy(fontSize = 17.sp))
                HGap(12.dp)
                LabelRaw(text = subtitle, style = RlType.label(10.sp, RlColors.Faint))
                Spacer(Modifier.weight(1f))
                val escInteraction = remember { MutableInteractionSource() }
                val escHovered by escInteraction.collectIsHoveredAsState()
                Box(
                    Modifier
                        .hoverable(escInteraction)
                        .clickable(interactionSource = escInteraction, indication = null) { onClose() },
                ) {
                    Chip(
                        text = "ESC",
                        fill = if (escHovered) RlColors.AccentSoft else RlColors.PaperDeep,
                        textColor = if (escHovered) RlColors.AccentDeep else RlColors.Muted,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                content()
            }
    }
}

// ------------------------------------------------------------------ 命令面板

@Composable
private fun PaletteSheet(state: IdeState, commands: List<IdeCommand>, compact: Boolean) {
    val mode = state.overlay
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
                    group = "档案 / ${path.substringBeforeLast('/', "root")}",
                    action = { state.open(path) },
                )
            }

        else -> emptyList()
    }

    var selected by remember(mode, query) { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    var field by remember(mode) { mutableStateOf(TextFieldValue("", TextRange(0))) }

    LaunchedEffect(mode) {
        field = TextFieldValue("", TextRange(0))
        focus.requestFocus()
    }

    Column(
        Modifier
            .padding(top = if (compact) 24.dp else 84.dp, start = 12.dp, end = 12.dp)
            .width(if (compact) 0.dp else 660.dp)
            .then(if (compact) Modifier.fillMaxWidth() else Modifier)
            .heightIn(max = 560.dp)
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
                    .padding(horizontal = 20.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentTick(length = 20.dp)
                HGap(11.dp)
                Label(
                    if (mode == OverlayMode.COMMAND_INDEX) "命令面板" else "快速打开",
                    style = RlType.label(11.sp, RlColors.Ink),
                )
                HGap(16.dp)
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        state.overlayQuery = it.text
                    },
                    singleLine = true,
                    textStyle = RlType.mono.copy(fontSize = 13.sp, color = RlColors.Ink),
                    cursorBrush = SolidColor(RlColors.Accent),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { inner() }
                        }
                    },
                )
                HGap(16.dp)
                LabelRaw(
                    text = "${rows.size} 项",
                    style = RlType.label(10.sp, RlColors.Faint),
                )
                HGap(14.dp)
                val paletteEsc = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .clickable(interactionSource = paletteEsc, indication = null) {
                            state.overlay = OverlayMode.NONE
                        },
                ) {
                    Chip(text = "ESC")
                }
            }

            Column(Modifier.fillMaxWidth().height(340.dp)) {
                if (rows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Label("没有匹配项")
                    }
                } else {
                    rows.take(9).forEachIndexed { index, row ->
                        val active = index == selected
                        val interaction = remember { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                .wash(when {
                                        active -> RlColors.AccentSoft
                                        hovered -> RlColors.FieldDeep
                                        else -> Color.Transparent
                                    })
                                .hoverable(interaction)
                                .clickable(interactionSource = interaction, indication = null) {
                                    row.action()
                                    state.overlay = OverlayMode.NONE
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 选中项：一枚冰青小条，比反白更「无界」
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .height(18.dp)
                                        .wash(if (active) RlColors.Accent else Color.Transparent),
                                )
                                HGap(11.dp)
                                LabelRaw(text = row.index, style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.Faint))
                                HGap(14.dp)
                                BasicText(
                                    text = row.title,
                                    style = RlType.body.copy(
                                        fontSize = 13.5.sp,
                                        color = if (active) RlColors.Ink else RlColors.InkSoft,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                HGap(10.dp)
                                LabelRaw(text = row.secondary, style = RlType.label(10.sp, RlColors.Faint))
                                Spacer(Modifier.weight(1f))
                                LabelRaw(
                                    text = row.trailing,
                                    style = RlType.label(10.sp, if (active) RlColors.AccentDeep else RlColors.Muted),
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            LabelRaw(text = row.group, style = RlType.label(9.sp, RlColors.Faint))
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabelRaw(text = "↑ ↓ 选择", style = RlType.label(9.5.sp, RlColors.Faint))
                HGap(18.dp)
                LabelRaw(text = "回车 执行", style = RlType.label(9.5.sp, RlColors.Faint))
                Spacer(Modifier.weight(1f))
                LabelRaw(
                    text = if (mode == OverlayMode.COMMAND_INDEX) "分析系统 · 索引" else "分析系统 · 档案",
                    style = RlType.label(9.5.sp, RlColors.Faint),
                )
            }
    }
}

// -------------------------------------------------------------------- 设置页

@Composable
private fun SettingsOverlay(state: IdeState, compact: Boolean) {
    SheetScaffold(
        title = "设置",
        subtitle = "SETTINGS · 仅保存在本次会话",
        onClose = { state.overlay = OverlayMode.NONE },
        compact = compact,
    ) {
        SettingSection("显示")
        StepperRow(
            label = "代码字号",
            hint = "当前 ${RlSettings.codeFontSize.toInt()} sp（行高自动跟随）",
            onMinus = { RlSettings.codeFontSize = (RlSettings.codeFontSize - 1f).coerceAtLeast(12f) },
            onPlus = { RlSettings.codeFontSize = (RlSettings.codeFontSize + 1f).coerceAtLeast(12f).coerceAtMost(22f) },
        )
        ChoiceRow(
            label = "制表符宽度",
            hint = "按 Tab 时插入的空格数",
            options = listOf("2" to 2, "4" to 4, "8" to 8),
            selected = RlSettings.tabWidth,
            onSelect = { RlSettings.tabWidth = it },
        )
        Spacer(Modifier.height(6.dp))
        ToggleRow("显示行号", "编辑器左侧的行号栏", RlSettings.showLineNumbers) {
            RlSettings.showLineNumbers = it
        }
        ToggleRow("显示遥测栏", "最右侧的帧率/行数等信息条", RlSettings.showRail) {
            RlSettings.showRail = it
        }

        SettingSection("面板")
        ToggleRow("资源管理器", "左侧文件树", state.explorerVisible) { state.explorerVisible = it }
        ToggleRow("参考区", "右侧档案信息", state.referenceVisible) { state.referenceVisible = it }
        ToggleRow("分析控制台", "底部终端 / 问题 / 日志", state.outputVisible) { state.outputVisible = it }

        SettingSection("操作")
        ActionRow("重置工作区", "丢弃所有修改，回到初始档案", state.statusMessage) {
            state.softReset()
            state.overlay = OverlayMode.NONE
        }
        ActionRow("运行分析", "等同 F5", "ANALYSIS") { state.requestRun() }
        ActionRow("工作区总览", "查看文档与统计（ESC 同效）", "OVERVIEW") {
            state.openOverlay(OverlayMode.OVERVIEW)
        }

        SettingSection("关于")
        InfoRow("版本", "LUMICODE $LUMICODE_VERSION · 构建 $LUMICODE_STAMP")
        InfoRow("运行平台", platformLabel())
        InfoRow("代码字体", "JetBrains Mono + Noto Sans CJK（合并子集）")
        InfoRow("界面字体", "Noto Sans CJK SC")
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingSection(title: String) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(title, style = RlType.label(10.sp, RlColors.Ink))
        HGap(10.dp)
        Box(
            Modifier
                .height(1.dp)
                .weight(1f)
                .background(
                    Brush.horizontalGradient(listOf(RlColors.Hair, Color.Transparent)),
                ),
        )
    }
}

@Composable
private fun SettingRow(label: String, hint: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = RlType.body.copy(fontSize = 13.sp, color = RlColors.Ink))
            Spacer(Modifier.height(3.dp))
            LabelRaw(text = hint, style = RlType.label(9.5.sp, RlColors.Faint))
        }
        HGap(16.dp)
        control()
    }
}

@Composable
private fun StepperRow(label: String, hint: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    SettingRow(label, hint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton("−", onMinus)
            Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) {
                LabelRaw(
                    text = RlSettings.codeFontSize.toInt().toString(),
                    style = RlType.mono.copy(fontSize = 13.sp, color = RlColors.Ink),
                )
            }
            StepperButton("+", onPlus)
        }
    }
}

@Composable
private fun StepperButton(glyph: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .wash(if (hovered) RlColors.AccentSoft else RlColors.FieldDeep)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .width(30.dp)
            .height(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        LabelRaw(
            text = glyph,
            style = RlType.mono.copy(fontSize = 13.sp, color = if (hovered) RlColors.AccentDeep else RlColors.Ink),
        )
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    hint: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    SettingRow(label, hint) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (text, value) ->
                val active = value == selected
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Box(
                    Modifier
                        .wash(when {
                                active -> RlColors.Ink
                                hovered -> RlColors.FieldDeep
                                else -> RlColors.Field.copy(alpha = 0.7f)
                            })
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    LabelRaw(
                        text = text,
                        style = RlType.mono.copy(
                            fontSize = 12.sp,
                            color = if (active) Color.White else RlColors.InkSoft,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(label, hint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabelRaw(
                text = if (checked) "开启" else "关闭",
                style = RlType.label(10.sp, if (checked) RlColors.AccentDeep else RlColors.Faint),
            )
            HGap(10.dp)
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .width(44.dp)
                    .height(20.dp)
                    .wash(if (checked) RlColors.Accent else RlColors.HairStrong)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) { onChange(!checked) },
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .width(14.dp)
                        .height(14.dp)
                        .wash(RlColors.Panel),
                )
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, hint: String, trailing: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .wash(if (hovered) RlColors.FieldDeep else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = RlType.body.copy(fontSize = 13.sp, color = RlColors.Ink))
            Spacer(Modifier.height(3.dp))
            LabelRaw(text = hint, style = RlType.label(9.5.sp, RlColors.Faint))
        }
        HGap(16.dp)
        LabelRaw(
            text = "→",
            style = RlType.mono.copy(fontSize = 13.sp, color = if (hovered) RlColors.Accent else RlColors.InkSoft),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        LabelRaw(text = label, style = RlType.label(10.sp, RlColors.Muted))
        Spacer(Modifier.width(20.dp))
        BasicText(value, style = RlType.body.copy(fontSize = 12.5.sp, color = RlColors.InkSoft), maxLines = 2)
    }
}

// ---------------------------------------------------------------- 工作区总览

@Composable
private fun OverviewOverlay(state: IdeState, compact: Boolean) {
    val files = state.quickOpenTargets()
    val totalLines = files.sumOf { state.contentOf(it).count { ch -> ch == '\n' } + 1 }
    val totalChars = files.sumOf { state.contentOf(it).length }

    SheetScaffold(
        title = "工作区总览",
        subtitle = "WORKSPACE OVERVIEW · 按 ESC 关闭",
        onClose = { state.overlay = OverlayMode.NONE },
        compact = compact,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            StatBlock("档案数", files.size.toString())
            StatBlock("总行数", totalLines.toString())
            StatBlock("总字符", totalChars.toString())
            StatBlock("未保存", state.dirtyCount.toString())
            StatBlock("已保存次数", state.savedCount.toString())
        }
        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.height(12.dp))

        Label("文档（点击打开）", style = RlType.label(10.sp, RlColors.Ink))
        Spacer(Modifier.height(8.dp))
        files.forEach { path ->
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .wash(if (hovered) RlColors.FieldDeep else Color.Transparent)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) {
                        state.open(path)
                        state.overlay = OverlayMode.NONE
                    }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabelRaw(
                    text = state.metaOf(path)?.meta?.archiveNo ?: "—",
                    style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.Faint),
                )
                HGap(14.dp)
                BasicText(
                    text = path,
                    style = RlType.mono.copy(
                        fontSize = 12.sp,
                        color = if (path == state.activePath) RlColors.Ink else RlColors.InkSoft,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                LabelRaw(
                    text = "${state.contentOf(path).count { it == '\n' } + 1} 行",
                    style = RlType.label(10.sp, RlColors.Faint),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Spacer(Modifier.height(12.dp))
        Label("最近操作", style = RlType.label(10.sp, RlColors.Ink))
        Spacer(Modifier.height(8.dp))
        state.log.takeLast(6).reversed().forEach { entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                LabelRaw(text = entry.time, style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.Faint))
                HGap(12.dp)
                LabelRaw(text = entry.text, style = RlType.mono.copy(fontSize = 12.sp, color = RlColors.InkSoft))
            }
        }

        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.height(12.dp))
        Label("快捷键", style = RlType.label(10.sp, RlColors.Ink))
        Spacer(Modifier.height(8.dp))
        listOf(
            "⌘K / Ctrl+K" to "命令面板",
            "⌘P / Ctrl+P" to "快速打开",
            "⌘F / Ctrl+F" to "文档内查找",
            "⌘S / Ctrl+S" to "保存档案",
            "⌘N / Ctrl+N" to "新建文件",
            "F5" to "运行分析",
            "ESC" to "关闭浮层 / 控制台 / 本页",
        ).forEach { (key, meaning) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelRaw(text = key, style = RlType.mono.copy(fontSize = 11.5.sp, color = RlColors.Ink))
                Spacer(Modifier.width(20.dp))
                LabelRaw(text = meaning, style = RlType.body.copy(fontSize = 12.5.sp, color = RlColors.InkSoft))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column {
        LabelRaw(text = value, style = RlType.title.copy(fontSize = 26.sp, lineHeight = 28.sp))
        Spacer(Modifier.height(4.dp))
        Label(label, style = RlType.label(10.sp, RlColors.Muted))
    }
}
