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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.state.CollabTask
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.ui.components.GhostButton
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.components.clickableFlat
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlType
import com.lumicode.editor.workspace.WorkspaceApi
import com.lumicode.editor.workspace.WorkspaceFileChange
import com.lumicode.editor.workspace.WorkspaceFileDiffResult
import kotlinx.coroutines.launch

/** Snapshot file list + expandable before/after preview. */
@Composable
fun ChangeDiffPanel(
    task: CollabTask,
    state: IdeState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val changes = task.changedFiles
    val snap = task.snapshotId
    if (changes.isEmpty() || snap.isNullOrBlank()) return

    var selected by remember(task.id) { mutableStateOf<String?>(null) }
    var diff by remember(task.id, selected) { mutableStateOf<WorkspaceFileDiffResult?>(null) }
    var loading by remember(task.id, selected) { mutableStateOf(false) }

    LaunchedEffect(task.id, selected, snap) {
        val path = selected ?: return@LaunchedEffect
        loading = true
        diff = runCatching { WorkspaceApi.fileDiff(snap, path) }.getOrNull()
        loading = false
    }

    Column(modifier.fillMaxWidth()) {
        LabelRaw(
            text = "改动预览 · ${changes.size} 个文件（点开看前后）",
            style = RlType.label(10.sp, RlColors.Accent),
        )
        Spacer(Modifier.height(4.dp))
        changes.take(if (compact) 6 else 20).forEach { change ->
            ChangeRow(
                change = change,
                selected = selected == change.path,
                onClick = {
                    selected = if (selected == change.path) null else change.path
                    state.open(change.path)
                },
            )
        }
        if (selected != null) {
            Spacer(Modifier.height(6.dp))
            when {
                loading -> LabelRaw(text = "加载 diff…", style = RlType.label(10.sp, RlColors.Faint))
                diff == null || diff?.ok != true -> LabelRaw(
                    text = "无法读取 · ${diff?.error ?: "unknown"}",
                    style = RlType.label(10.sp, RlColors.CodeNumber),
                )
                else -> DiffBody(diff!!, compact)
            }
        }
    }
}

@Composable
private fun ChangeRow(
    change: WorkspaceFileChange,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val kindColor = when (change.kind) {
        "added" -> RlColors.CodeString
        "deleted" -> RlColors.CodeNumber
        else -> RlColors.Accent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .wash(if (selected) RlColors.AccentSoft else RlColors.FieldDeep)
            .clickableFlat(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelRaw(
            text = change.kind.take(1).uppercase(),
            style = RlType.label(10.sp, kindColor),
        )
        Spacer(Modifier.padding(4.dp))
        LabelRaw(
            text = change.path,
            style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.InkSoft),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DiffBody(diff: WorkspaceFileDiffResult, compact: Boolean) {
    val maxLines = if (compact) 24 else 60
    Column(
        Modifier
            .fillMaxWidth()
            .wash(RlColors.PaperDeep)
            .padding(8.dp)
            .heightIn(max = if (compact) 180.dp else 320.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        LabelRaw(
            text = "${diff.kind} · ${diff.path}",
            style = RlType.label(10.sp, RlColors.Faint),
        )
        Spacer(Modifier.height(4.dp))
        if (diff.before != null) {
            LabelRaw(text = "— 前", style = RlType.label(10.sp, RlColors.CodeNumber))
            BasicText(
                text = diff.before!!.lineSequence().take(maxLines).joinToString("\n"),
                style = RlType.mono.copy(fontSize = 10.sp, color = RlColors.Muted),
            )
            Spacer(Modifier.height(6.dp))
        }
        if (diff.after != null) {
            LabelRaw(text = "+ 后", style = RlType.label(10.sp, RlColors.CodeString))
            BasicText(
                text = diff.after!!.lineSequence().take(maxLines).joinToString("\n"),
                style = RlType.mono.copy(fontSize = 10.sp, color = RlColors.InkSoft),
            )
        }
        if (diff.before == null && diff.after == null) {
            LabelRaw(text = "（空文件）", style = RlType.label(10.sp, RlColors.Faint))
        }
    }
}

/** Live tool approval gate for write/edit/bash. */
@Composable
fun ToolApprovalBanner(state: IdeState, modifier: Modifier = Modifier) {
    val pending = state.collab.pendingToolApproval ?: return
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .fillMaxWidth()
            .wash(RlColors.AccentSoft)
            .padding(10.dp),
    ) {
        LabelRaw(
            text = "工具待批 · ${pending.toolName}",
            style = RlType.label(12.sp, RlColors.AccentDeep),
        )
        if (pending.argsPreview.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            LabelRaw(
                text = pending.argsPreview.take(240),
                style = RlType.mono.copy(fontSize = 10.sp, color = RlColors.InkSoft),
                maxLines = 3,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(text = "批准并继续", onClick = {
                scope.launch { state.approvePendingTool() }
            })
            GhostButton(text = "拒绝并取消", onClick = {
                scope.launch { state.leaveItAndRestore(pending.taskId) }
            })
            Spacer(Modifier.weight(1f))
            GhostButton(
                text = if (state.collab.requireToolApproval) "审批：开" else "审批：关",
                onClick = {
                    state.collab.requireToolApproval = !state.collab.requireToolApproval
                    state.statusMessage =
                        if (state.collab.requireToolApproval) "写盘/bash 需批准" else "工具自动执行"
                },
            )
        }
    }
}
