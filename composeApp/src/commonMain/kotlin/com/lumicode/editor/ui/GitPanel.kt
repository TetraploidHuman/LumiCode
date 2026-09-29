package com.lumicode.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.ui.components.GhostButton
import com.lumicode.editor.ui.components.Label
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.components.clickableFlat
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlType
import com.lumicode.editor.workspace.GitDiffResult
import com.lumicode.editor.workspace.GitStatusResult
import com.lumicode.editor.workspace.WorkspaceApi
import kotlinx.coroutines.launch

/** Git status / diff / commit for the mounted workspace. */
@Composable
fun GitPanel(state: IdeState, compact: Boolean, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<GitStatusResult?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var diff by remember { mutableStateOf<GitDiffResult?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            busy = true
            status = runCatching { WorkspaceApi.gitStatus() }.getOrNull()
            busy = false
        }
    }

    LaunchedEffect(state.workspaceRoot, state.workspaceMounted) {
        if (state.workspaceMounted) refresh()
    }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(
                if (compact) "Git" else "Git 工作区",
                style = RlType.label(if (compact) 10.5.sp else 11.sp, RlColors.Faint),
            )
            Spacer(Modifier.weight(1f))
            GhostButton(text = if (busy) "…" else "刷新", onClick = { refresh() })
        }
        Spacer(Modifier.height(6.dp))

        val st = status
        when {
            !state.workspaceMounted -> LabelRaw(
                text = "先打开工作区",
                style = RlType.label(11.sp, RlColors.Faint),
            )
            st == null -> LabelRaw(text = "加载中…", style = RlType.label(11.sp, RlColors.Faint))
            !st.ok -> LabelRaw(
                text = st.error ?: "git 不可用",
                style = RlType.label(11.sp, RlColors.CodeNumber),
            )
            else -> {
                LabelRaw(
                    text = buildString {
                        append(st.branch.ifBlank { "(detached)" })
                        if (st.head.isNotBlank()) append(" · ${st.head}")
                        append(if (st.clean) " · 干净" else " · ${st.files.size} 处改动")
                    },
                    style = RlType.label(11.sp, RlColors.InkSoft),
                    maxLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .wash(RlColors.FieldDeep)
                        .padding(8.dp)
                        .heightIn(max = if (compact) 140.dp else 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (st.files.isEmpty()) {
                        LabelRaw(text = "工作树干净", style = RlType.label(11.sp, RlColors.Faint))
                    } else {
                        st.files.take(if (compact) 12 else 40).forEach { f ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickableFlat(onClick = {
                                        selected = f.path
                                        state.open(f.path)
                                        scope.launch {
                                            diff = WorkspaceApi.gitDiff(f.path)
                                        }
                                    })
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LabelRaw(
                                    text = f.kind.take(1).uppercase(),
                                    style = RlType.label(10.sp, RlColors.Accent),
                                )
                                Spacer(Modifier.padding(4.dp))
                                LabelRaw(
                                    text = f.path,
                                    style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.InkSoft),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                val d = diff
                if (selected != null && d != null && d.diff.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        text = d.diff.lineSequence().take(if (compact) 40 else 80).joinToString("\n"),
                        style = RlType.mono.copy(fontSize = 10.sp, color = RlColors.Muted),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wash(RlColors.PaperDeep)
                            .padding(8.dp)
                            .heightIn(max = if (compact) 120.dp else 200.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                if (!st.clean) {
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = message,
                        onValueChange = { message = it },
                        textStyle = RlType.mono.copy(fontSize = 12.sp, color = RlColors.InkSoft),
                        cursorBrush = SolidColor(RlColors.Accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wash(RlColors.PaperDeep)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        decorationBox = { inner ->
                            if (message.isEmpty()) {
                                LabelRaw(text = "提交说明…", style = RlType.label(12.sp, RlColors.Faint))
                            }
                            inner()
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    GhostButton(text = "暂存并提交", onClick = {
                        val msg = message.trim()
                        if (msg.isEmpty()) {
                            state.statusMessage = "请填写提交说明"
                            return@GhostButton
                        }
                        scope.launch {
                            val result = WorkspaceApi.gitCommit(msg)
                            if (result.ok) {
                                state.statusMessage = "已提交 · $msg"
                                message = ""
                                selected = null
                                diff = null
                                refresh()
                            } else {
                                state.statusMessage = "提交失败：${result.error ?: "unknown"}"
                            }
                        }
                    })
                }
            }
        }
    }
}
