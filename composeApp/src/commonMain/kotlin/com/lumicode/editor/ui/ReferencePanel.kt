package com.lumicode.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.model.FileNode
import com.lumicode.editor.platform.platformTag
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.OverlayMode
import com.lumicode.editor.ui.components.GhostButton
import com.lumicode.editor.ui.components.Label
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.components.SolidBarButton
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlType

/**
 * Left column: the archival file tree, numbered like archive entries.
 */
@Composable
fun ExplorerPanel(state: IdeState, showFooter: Boolean = true, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(RlDimens.explorerWidth)
            .fillMaxHeight()
            .padding(start = 22.dp, top = 18.dp, end = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("工作区")
            Spacer(Modifier.width(8.dp))
            Box(Modifier.height(1.dp).weight(1f).background(RlColors.Hair))
        }
        Spacer(Modifier.height(14.dp))

        LabelRaw(
            text = "R-OS 工作区",
            style = RlType.title.copy(fontSize = 22.sp, lineHeight = 24.sp),
        )
        Spacer(Modifier.height(5.dp))
        Label("内部数据库")
        Spacer(Modifier.height(3.dp))
        LabelRaw(
            text = "NO.${state.openTabs.size.toString().padStart(3, '0')}",
            style = RlType.mono.copy(fontSize = 12.sp, color = RlColors.Faint),
        )
        Spacer(Modifier.height(22.dp))

        Column(Modifier.weight(1f).fillMaxWidth()) {
            state.tree.forEach { node ->
                TreeNode(node, state, depth = 0)
            }
        }

        if (!showFooter) return@Column
        Spacer(Modifier.height(10.dp))
        Box(Modifier.height(1.dp).fillMaxWidth().background(RlColors.Hair))
        Spacer(Modifier.height(10.dp))
        GhostButton(
            text = "拖拽查看",
            glyph = "→",
            glyphLeading = false,
            onClick = { state.statusMessage = "拖拽查看模式" },
        )
        Spacer(Modifier.height(6.dp))
        GhostButton(
            text = "文档结构图",
            glyph = "↗",
            glyphLeading = false,
            onClick = { state.openOverlay(OverlayMode.OVERVIEW) },
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun TreeNode(node: FileNode, state: IdeState, depth: Int) {
    if (node.isFolder) {
        val open = state.isFolderOpen(node.path)
        TreeRow(
            label = node.name,
            prefix = if (open) "−" else "+",
            hint = node.fileCount.toString().padStart(2, '0'),
            depth = depth,
            selected = false,
            isFolder = true,
            onClick = { state.toggleFolder(node.path) },
        )
        if (open) node.children.forEach { TreeNode(it, state, depth + 1) }
    } else {
        val selected = state.activePath == node.path
        val dirty = state.isDirty(node.path)
        TreeRow(
            label = node.name,
            prefix = if (dirty) "•" else " ",
            hint = null,
            depth = depth,
            selected = selected,
            isFolder = false,
            onClick = { state.open(node.path) },
        )
    }
}

@Composable
private fun TreeRow(
    label: String,
    prefix: String,
    hint: String?,
    depth: Int,
    selected: Boolean,
    isFolder: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        selected -> RlColors.PaperDeep
        hovered -> RlColors.Panel
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = (depth * 12).dp, top = 5.dp, bottom = 5.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelRaw(
            text = prefix,
            style = RlType.mono.copy(
                fontSize = 9.sp,
                color = if (selected) RlColors.Ink else RlColors.Faint,
            ),
        )
        Spacer(Modifier.width(7.dp))
        BasicText(
            text = label,
            style = (if (isFolder) RlType.label(10.sp, if (selected) RlColors.Ink else RlColors.Muted) else RlType.mono.copy(
                fontSize = 12.sp,
                color = if (selected) RlColors.Ink else RlColors.InkSoft,
            )),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            LabelRaw(text = hint, style = RlType.label(9.5.sp, RlColors.Faint))
        }
    }
}

/**
 * Right column: REFERENCE AREA — file metadata, tabbed detail views and the
 * archive action bar, mirroring the poster layout.
 */
@Composable
fun ReferencePanel(state: IdeState, modifier: Modifier = Modifier) {
    val file = state.activeFile
    var tab by remember { mutableStateOf(0) }

    // keep the tab selection stable when switching documents
    LaunchedEffect(state.activePath) { tab = 0 }

    Column(
        modifier
            .width(RlDimens.referenceWidth)
            .fillMaxHeight()
            .padding(start = 18.dp, top = 18.dp, end = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabelRaw(
                text = "档案 ${file?.meta?.archiveNo ?: "X-000"}",
                style = RlType.label(10.sp, RlColors.Ink),
            )
            Spacer(Modifier.weight(1f))
            Label("参考区")
        }
        Spacer(Modifier.height(18.dp))

        // Scrollable reference body: metadata + tabbed detail + archive actions.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
        BasicText(
            text = file?.name ?: "未选择文档",
            style = RlType.pageTitle.copy(fontSize = 28.sp, lineHeight = 30.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabelRaw(
                text = if (file != null) file.folder.ifEmpty { "工作区根目录" } else "—",
                style = RlType.label(10.sp, RlColors.InkSoft),
            )
            Spacer(Modifier.width(10.dp))
            Label("机构档案")
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.height(1.dp).fillMaxWidth().background(RlColors.Ink))
        Spacer(Modifier.height(18.dp))

        if (file == null) {
            Label("尚未选择文档")
        } else {
            Row(Modifier.fillMaxWidth()) {
                MetaColumn(
                    Modifier.weight(1f),
                    "科室" to file.meta.departmentCn,
                    "相关人物" to file.meta.related,
                )
                Spacer(Modifier.width(18.dp))
                MetaColumn(
                    Modifier.weight(1f),
                    "编目范围" to file.meta.collectionCn,
                    "状态" to file.meta.statusCn,
                    statusDot = true,
                )
            }
            Spacer(Modifier.height(20.dp))

            // ------------------------------------------------------- tab strip
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                val titles = listOf("01 概述" to "", "02 结构" to "", "03 日志" to "")
                titles.forEachIndexed { index, (cn, en) ->
                    val active = index == tab
                    Column(
                        Modifier
                            .clickable { tab = index }
                            .padding(end = 18.dp, bottom = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LabelRaw(
                                text = cn,
                                style = RlType.label(11.sp, if (active) RlColors.Ink else RlColors.Faint),
                            )
                            if (en.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                LabelRaw(
                                    text = en,
                                    style = RlType.label(9.sp, if (active) RlColors.Muted else RlColors.Faint),
                                )
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Box(
                            Modifier
                                .height(if (active) 1.5.dp else 1.dp)
                                .fillMaxWidth()
                                .background(if (active) RlColors.Ink else Color.Transparent),
                        )
                    }
                }
            }
            Box(Modifier.height(1.dp).fillMaxWidth().background(RlColors.Hair))
            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxWidth()) {
                when (tab) {
                    0 -> {
                        Label("摘要", style = RlType.label(10.sp, RlColors.Ink))
                        Spacer(Modifier.height(12.dp))
                        BasicText(text = file.meta.abstract, style = RlType.body)
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth()) {
                            MiniStat("行数", (state.activeContent.count { it == '\n' } + 1).toString(), Modifier.weight(1f))
                            MiniStat("字符", state.activeContent.length.toString(), Modifier.weight(1f))
                            MiniStat("语言", file.language.short, Modifier.weight(1f))
                        }
                    }

                    1 -> {
                        Label("结构", style = RlType.label(10.sp, RlColors.Ink))
                        Spacer(Modifier.height(12.dp))
                        val outline = remember(state.activeContent) { outlineOf(state.activeContent) }
                        if (outline.isEmpty()) {
                            Label("未找到声明", style = RlType.label(10.sp, RlColors.Faint))
                        } else {
                            outline.take(18).forEach { (line, text) ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { state.open(file.path, revealLine = line) }
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LabelRaw(
                                        text = line.toString().padStart(3, '0'),
                                        style = RlType.mono.copy(fontSize = 10.5.sp, color = RlColors.Faint),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    BasicText(
                                        text = text,
                                        style = RlType.mono.copy(fontSize = 11.5.sp, color = RlColors.InkSoft),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Label("访问日志", style = RlType.label(10.sp, RlColors.Ink))
                        Spacer(Modifier.height(12.dp))
                        if (state.log.isEmpty()) {
                            Label("暂无记录", style = RlType.label(10.sp, RlColors.Faint))
                        } else {
                            state.log.reversed().take(14).forEach { entry ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    LabelRaw(text = entry.time, style = RlType.mono.copy(fontSize = 10.5.sp, color = RlColors.Faint))
                                    Spacer(Modifier.width(10.dp))
                                    BasicText(
                                        text = entry.text,
                                        style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.InkSoft),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SolidBarButton(
                text = if (state.isDirty(file.path)) "保存档案" else "档案已保存",
                trailing = "收藏档案",
                onClick = { state.save(file.path) },
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("导出", style = RlType.label(10.sp, RlColors.Muted))
                Spacer(Modifier.weight(1f))
                BasicText("↓", style = RlType.mono.copy(fontSize = 15.sp, color = RlColors.Ink))
            }
        }
        Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LabelRaw(text = platformTag(), style = RlType.label(9.5.sp, RlColors.Faint))
            Spacer(Modifier.weight(1f))
            LabelRaw(text = "由 LUMICODE 驱动", style = RlType.label(10.sp, RlColors.Ink))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(width = 26.dp, height = 3.dp).background(RlColors.Ink))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MetaColumn(
    modifier: Modifier,
    vararg entries: Pair<String, String>,
    statusDot: Boolean = false,
) {
    Column(modifier) {
        entries.forEachIndexed { index, (label, value) ->
            if (index > 0) Spacer(Modifier.height(20.dp))
            Label(label, style = RlType.label(10.sp, RlColors.Muted))
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (statusDot) {
                    Box(Modifier.size(4.dp).background(RlColors.Ink))
                    Spacer(Modifier.width(7.dp))
                }
                BasicText(
                    text = value,
                    style = RlType.value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Label(label, style = RlType.label(9.5.sp, RlColors.Faint))
        Spacer(Modifier.height(4.dp))
        BasicText(value, style = RlType.mono.copy(fontSize = 13.sp, color = RlColors.Ink))
    }
}

/** Naive outline extraction: declarations worth listing in the reference area. */
internal fun outlineOf(text: String): List<Pair<Int, String>> {
    val regex = Regex("^\\s*(?:@\\w+\\s+)?(?:fun|class|object|interface|enum class|data class|val|var|def|function)\\s+[^\\n]*")
    val out = mutableListOf<Pair<Int, String>>()
    text.split('\n').forEachIndexed { index, line ->
        val match = regex.find(line) ?: return@forEachIndexed
        val cleaned = match.value.trim().removeSuffix("{").trim()
        if (cleaned.length in 3..74) out += (index + 1) to cleaned
    }
    return out
}
