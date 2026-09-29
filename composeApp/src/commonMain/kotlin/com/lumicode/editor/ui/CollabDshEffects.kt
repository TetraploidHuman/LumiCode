package com.lumicode.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.lumicode.editor.dsh.DshApi
import com.lumicode.editor.dsh.newDshJobId
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.LineKind
import com.lumicode.editor.state.PendingToolApproval
import com.lumicode.editor.state.TaskStatus
import com.lumicode.editor.workspace.WorkspaceApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 小队 ↔ lumicode-dsh-bridge ↔ 本机 dsh-web（支持多任务并发）。 */
@Composable
fun CollabDshEffects(state: IdeState) {
    val collab = state.collab
    val scope = rememberCoroutineScope()
    val startMutex = remember { Mutex() }

    LaunchedEffect(Unit) {
        collab.onDshHealth(DshApi.health())
    }

    LaunchedEffect(collab.dshDispatchToken) {
        if (collab.dshDispatchToken == 0) return@LaunchedEffect
        while (isActive) {
            val taskId = startMutex.withLock { collab.takeNextDshDispatch() } ?: break
            scope.launch { runDshJob(state, taskId) }
        }
    }
}

private suspend fun runDshJob(state: IdeState, taskId: String) {
    val collab = state.collab
    val task = collab.tasks.firstOrNull { it.id == taskId }
    if (task == null) {
        collab.finishDshDispatch(taskId)
        return
    }
    if (!state.workspaceMounted) {
        collab.failDshTask(taskId, "请先选择工作区文件夹")
        state.statusMessage = "小队 · 请先打开工作区"
        state.openWorkspacePicker()
        return
    }
    val cwd = state.workspaceRoot
    if (cwd.isNullOrBlank()) {
        collab.failDshTask(taskId, "工作区根路径未知")
        return
    }

    val agentId = task.agentId
    val jobId = newDshJobId()
    collab.bindDshJob(taskId, jobId, collab.dshSessionFor(agentId))
    collab.appendAgent("DSH 开始 · ${task.title.take(24)}", LineKind.MUTED)

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

    val writeScopes = collab.writeScopesForTask(taskId)
    var seen = 0
    var lastPartial: String? = null

    coroutineScope {
        val poll = launch {
            while (isActive) {
                delay(400)
                val progress = runCatching { DshApi.progress(jobId) }.getOrNull() ?: continue
                progress.sessionId?.let { sid -> collab.bindDshJob(taskId, jobId, sid) }
                if (progress.steps.size > seen) {
                    collab.appendDshTrace(agentId, progress.steps.drop(seen))
                    seen = progress.steps.size
                }
                val partial = progress.partialReply
                if (!partial.isNullOrBlank() && partial != lastPartial) {
                    lastPartial = partial
                    collab.updateTaskLiveStatus(taskId, "流式 · ${partial.take(48)}")
                }
                val approval = progress.pendingApproval
                if (approval != null) {
                    collab.setPendingApproval(
                        PendingToolApproval(
                            taskId = taskId,
                            jobId = jobId,
                            toolName = approval.toolName,
                            argsPreview = approval.argsPreview,
                            callId = approval.callId ?: approval.id,
                        ),
                    )
                } else if (collab.pendingToolApproval?.jobId == jobId) {
                    collab.clearPendingApproval(taskId)
                }
                if (progress.cancelled || progress.done) break
            }
        }

        val prompt = collab.buildDshPrompt(
            task.title,
            collab.consumeDshSupervisorNote(),
            state.workspaceListingBlock(),
            writeScopes = writeScopes,
        )
        try {
            val result = DshApi.chat(
                text = prompt,
                sessionId = collab.dshSessionFor(task.agentId),
                title = task.title,
                cwd = cwd,
                jobId = jobId,
                requireToolApproval = collab.requireToolApproval,
                writeScopes = writeScopes,
            )
            poll.cancel()
            runCatching { poll.join() }
            if (result.steps.size > seen) {
                collab.appendDshTrace(agentId, result.steps.drop(seen))
            }
            collab.clearPendingApproval(taskId)

            when {
                result.cancelled -> {
                    if (snapshotId != null) {
                        val restored = WorkspaceApi.restoreSnapshot(snapshotId)
                        if (restored.ok) {
                            collab.markRollbackDone(
                                taskId,
                                "取消回滚 · 恢复 ${restored.restored} · 删 ${restored.deleted}",
                            )
                            runCatching { WorkspaceApi.forgetSnapshot(snapshotId) }
                            collab.clearTaskSnapshot(taskId)
                            state.reloadWorkspaceFromDisk()
                        }
                    }
                    val still = collab.tasks.firstOrNull { it.id == taskId }
                    if (still?.status == TaskStatus.WORKING) {
                        collab.leaveIt(taskId)
                    } else {
                        collab.finishDshDispatch(taskId)
                    }
                    state.statusMessage = "小队 · 已取消 DSH"
                }
                result.ok && !result.reply.isNullOrBlank() -> {
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
                }
                else -> {
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
    }
}
