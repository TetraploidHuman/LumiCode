package com.lumicode.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.lumicode.editor.dsh.DshApi
import com.lumicode.editor.state.IdeState

/** 小队 ↔ lumicode-dsh-bridge ↔ 本机 dsh-web（不重启 DSH）。 */
@Composable
fun CollabDshEffects(state: IdeState) {
    val collab = state.collab

    LaunchedEffect(Unit) {
        collab.onDshHealth(DshApi.health())
    }

    LaunchedEffect(collab.dshRequestToken) {
        if (collab.dshRequestToken == 0) return@LaunchedEffect
        val taskId = collab.pendingDshTaskId ?: return@LaunchedEffect
        val task = collab.tasks.firstOrNull { it.id == taskId } ?: return@LaunchedEffect
        if (!state.workspaceMounted) {
            collab.failDshTask(taskId, "请先选择工作区文件夹")
            state.statusMessage = "小队 · 请先打开工作区"
            state.openWorkspacePicker()
            return@LaunchedEffect
        }
        collab.dshBusy = true
        val prompt = collab.buildDshPrompt(
            task.title,
            collab.consumeDshSupervisorNote(),
            state.workspaceContextBlock(),
        )
        val result = DshApi.chat(
            text = prompt,
            sessionId = collab.dshSessionFor(task.agentId),
            title = task.title,
        )
        if (result.ok && !result.reply.isNullOrBlank()) {
            collab.completeDshTask(taskId, result.reply!!, result.sessionId)
            state.statusMessage = "小队 · DSH 已回提案（${result.model ?: "qwen35-9b"}）"
        } else {
            collab.failDshTask(taskId, result.error ?: "无回复")
            state.statusMessage = "小队 · DSH 失败：${result.error ?: "无回复"}"
        }
        collab.dshBusy = false
    }
}
