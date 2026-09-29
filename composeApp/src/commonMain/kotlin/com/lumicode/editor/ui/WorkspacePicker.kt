package com.lumicode.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.ui.components.GhostButton
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.components.SolidBarButton
import com.lumicode.editor.ui.components.clickableFlat
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlType
import com.lumicode.editor.workspace.DirEntry
import com.lumicode.editor.workspace.WorkspaceApi
import kotlinx.coroutines.launch

@Composable
fun WorkspaceOpenOverlay(state: IdeState, compact: Boolean) {
    val scope = rememberCoroutineScope()
    var pathDraft by remember(state.overlayQuery) { mutableStateOf(state.overlayQuery) }
    var error by remember { mutableStateOf<String?>(null) }
    var browsing by remember { mutableStateOf(false) }
    var dirCurrent by remember { mutableStateOf("") }
    var dirParent by remember { mutableStateOf("") }
    var dirEntries by remember { mutableStateOf<List<DirEntry>>(emptyList()) }
    var creatingFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("new-project") }

    suspend fun refreshDirs(at: String) {
        browsing = true
        val listing = if (state.workspaceMounted && !at.startsWith("/")) {
            WorkspaceApi.listDirs(at)
        } else {
            WorkspaceApi.browse(at.ifEmpty { pathDraft.ifBlank { "~" } })
        }
        browsing = false
        if (!listing.ok) {
            error = listing.error
            return
        }
        dirCurrent = listing.current
        dirParent = listing.parent
        dirEntries = listing.entries
        if (listing.current.startsWith("/")) pathDraft = listing.current
    }

    suspend fun createFolderHere() {
        val parent = dirCurrent.ifBlank { pathDraft.trim() }
        if (parent.isBlank()) {
            error = "请先进入一个父目录"
            return
        }
        val name = newFolderName.trim()
        if (name.isEmpty()) {
            error = "请输入文件夹名"
            return
        }
        error = null
        val result = WorkspaceApi.mkdirAbs(parent, name)
        if (!result.ok) {
            error = result.error ?: "创建失败"
            return
        }
        creatingFolder = false
        val created = if (parent.endsWith("/")) "$parent$name" else "$parent/$name"
        pathDraft = created
        // 建完直接进入新目录，方便接着挂载
        refreshDirs(created)
    }

    LaunchedEffect(Unit) {
        refreshDirs(pathDraft.ifBlank { "~" })
    }

    SheetScaffold(
        title = "打开工作区",
        subtitle = if (compact) "选择本机文件夹" else "WORKSPACE · 真实落盘",
        onClose = {
            if (state.workspaceMounted) state.overlay = com.lumicode.editor.state.OverlayMode.NONE
        },
        compact = compact,
        scrollContent = false,
    ) {
        Column(Modifier.fillMaxSize()) {
            LabelRaw(
                text = "输入绝对路径，或在下方浏览文件夹。可新建文件夹后再挂载。",
                style = RlType.label(11.sp, RlColors.Muted),
                maxLines = 4,
            )
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = pathDraft,
                onValueChange = {
                    pathDraft = it
                    error = null
                },
                textStyle = RlType.mono.copy(fontSize = 13.sp, color = RlColors.InkSoft),
                cursorBrush = SolidColor(RlColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .wash(RlColors.FieldDeep)
                    .padding(12.dp),
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                LabelRaw(text = it, style = RlType.label(11.sp, RlColors.CodeNumber))
            }
            Spacer(Modifier.height(12.dp))
            // SolidBarButton 自带 fillMaxWidth，必须独占一行，否则旁钮会被挤没。
            SolidBarButton(
                text = "挂载此路径",
                enabled = pathDraft.isNotBlank() && !state.workspaceBusy,
                onClick = {
                    scope.launch {
                        error = null
                        val ok = state.mountWorkspace(pathDraft.trim())
                        if (!ok) error = state.statusMessage
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            SolidBarButton(
                text = if (creatingFolder) "取消新建" else "新建文件夹",
                onClick = {
                    creatingFolder = !creatingFolder
                    error = null
                    if (creatingFolder && newFolderName.isBlank()) newFolderName = "new-project"
                },
            )
            Spacer(Modifier.height(4.dp))
            GhostButton(
                text = "用当前浏览目录填入路径",
                onClick = {
                    if (dirCurrent.isNotEmpty()) pathDraft = dirCurrent
                },
            )
            if (creatingFolder) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = newFolderName,
                        onValueChange = {
                            newFolderName = it
                            error = null
                        },
                        singleLine = true,
                        textStyle = RlType.mono.copy(fontSize = 13.sp, color = RlColors.InkSoft),
                        cursorBrush = SolidColor(RlColors.Accent),
                        modifier = Modifier
                            .weight(1f)
                            .wash(RlColors.FieldDeep)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.Enter, Key.NumPadEnter -> {
                                        scope.launch { createFolderHere() }
                                        true
                                    }
                                    Key.Escape -> {
                                        creatingFolder = false
                                        true
                                    }
                                    else -> false
                                }
                            },
                    )
                    GhostButton(
                        text = "创建",
                        glyph = "✓",
                        onClick = { scope.launch { createFolderHere() } },
                    )
                }
                LabelRaw(
                    text = "将在 ${dirCurrent.ifBlank { pathDraft.ifBlank { "~" } }} 下创建",
                    style = RlType.label(10.sp, RlColors.Faint),
                )
            }
            Spacer(Modifier.height(16.dp))
            LabelRaw(
                text = if (browsing) "读取目录…" else "浏览：${dirCurrent.ifEmpty { "（工作区根或未打开）" }}",
                style = RlType.label(10.sp, RlColors.Faint),
            )
            Spacer(Modifier.height(6.dp))
            val listScroll = rememberScrollState()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .wash(RlColors.FieldDeep)
                    .verticalScroll(listScroll)
                    .padding(8.dp),
            ) {
                if (dirParent.isNotEmpty() || dirCurrent.isNotEmpty()) {
                    LabelRaw(
                        text = "↑ 上级",
                        style = RlType.label(12.sp, RlColors.Accent),
                        modifier = Modifier
                            .clickableFlat { scope.launch { refreshDirs(dirParent) } }
                            .padding(vertical = 6.dp),
                    )
                }
                dirEntries.forEach { entry ->
                    LabelRaw(
                        text = "📁 ${entry.name}",
                        style = RlType.label(12.sp, RlColors.InkSoft),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableFlat {
                                pathDraft = entry.path
                                scope.launch { refreshDirs(entry.path) }
                            }
                            .padding(vertical = 6.dp),
                    )
                }
                if (dirEntries.isEmpty() && !browsing) {
                    LabelRaw(text = "此层无子文件夹，或请先挂载/输入路径后浏览", style = RlType.label(11.sp, RlColors.Muted))
                }
            }
        }
    }
}
