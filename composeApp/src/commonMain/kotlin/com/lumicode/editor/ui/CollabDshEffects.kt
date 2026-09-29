package com.lumicode.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.lumicode.editor.dsh.DshApi
import com.lumicode.editor.dsh.newDshJobId
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.LineKind
import com.lumicode.editor.workspace.WorkspaceApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        val cwd = state.workspaceRoot
        if (cwd.isNullOrBlank()) {
            collab.failDshTask(taskId, "工作区根路径未知")
            return@LaunchedEffect
        }
        collab.dshBusy = true
        val agentId = task.agentId
        val jobId = newDshJobId()
        collab.appendAgent("DSH 开始 · ${task.title.take(24)}", LineKind.MUTED)

        // Snapshot workspace before tools write, so cancel/retry can roll back.
        val previousSnap = task.snapshotId
        val snap = WorkspaceApi.createSnapshot()
        val snapshotId = snap.snapshotId?.takeIf { snap.ok }
        if (snapshotId != null) {
            collab.bindTaskSnapshot(taskId, snapshotId)
            collab.appendAgent("快照 · $snapshotId（${snap.fileCount} 文件）", LineKind.MUTED)
            if (!previousSnap.isNullOrBlank() && previousSnap != snapshotId) {
                runCatching { WorkspaceApi.forgetSnapshot(previousSnap) }
            }
        } else {
            collab.appendAgent("快照失败 · ${snap.error ?: "unknown"}（取消将无法回滚）", LineKind.WARN)
        }

        var seen = 0
        val poll = launch {
            while (isActive) {
                delay(450)
                val progress = runCatching { DshApi.progress(jobId) }.getOrNull() ?: continue
                if (progress.steps.size > seen) {
                    collab.appendDshTrace(agentId, progress.steps.drop(seen))
                    seen = progress.steps.size
                }
            }
        }

        val prompt = collab.buildDshPrompt(
            task.title,
            collab.consumeDshSupervisorNote(),
            state.workspaceListingBlock(),
        )
        try {
            val result = DshApi.chat(
                text = prompt,
                sessionId = collab.dshSessionFor(task.agentId),
                title = task.title,
                cwd = cwd,
                jobId = jobId,
            )
            poll.cancel()
            runCatching { poll.join() }
            if (result.steps.size > seen) {
                collab.appendDshTrace(agentId, result.steps.drop(seen))
            }
            if (result.ok && !result.reply.isNullOrBlank()) {
                val changes = if (snapshotId != null) {
                    WorkspaceApi.diffSnapshot(snapshotId).changes
                } else {
                    emptyList()
                }
                collab.completeDshTask(
                    taskId,
                    result.reply!!,
                    result.sessionId,
                    snapshotId = snapshotId,
                    changedFiles = changes,
                )
                state.reloadWorkspaceFromDisk()
                state.statusMessage = if (changes.isEmpty()) {
                    "小队 · DSH 完成（无文件变动）"
                } else {
                    "小队 · DSH 改了 ${changes.size} 个文件 · 可确认或回滚"
                }
            } else {
                // Failed mid-flight: roll back partial tool writes when possible.
                if (snapshotId != null) {
                    val restored = WorkspaceApi.restoreSnapshot(snapshotId)
                    if (restored.ok) {
                        collab.markRollbackDone(
                            taskId,
                            "失败回滚 · 恢复 ${restored.restored} · 删 ${restored.deleted}",
                        )
                        runCatching { WorkspaceApi.forgetSnapshot(snapshotId) }
                        collab.clearTaskSnapshot(taskId)
                        state.reloadWorkspaceFromDisk()
                    }
                }
                collab.failDshTask(taskId, result.error ?: "无回复")
                state.statusMessage = "小队 · DSH 失败：${result.error ?: "无回复"}"
            }
        } catch (e: CancellationException) {
            poll.cancel()
            throw e
        } catch (t: Throwable) {
            poll.cancel()
            if (snapshotId != null) {
                runCatching {
                    WorkspaceApi.restoreSnapshot(snapshotId)
                    WorkspaceApi.forgetSnapshot(snapshotId)
                    collab.clearTaskSnapshot(taskId)
                    state.reloadWorkspaceFromDisk()
                }
            }
            collab.failDshTask(taskId, t.message ?: "DSH 异常")
            state.statusMessage = "小队 · DSH 失败：${t.message ?: "异常"}"
        }
        collab.dshBusy = false
    }
}
