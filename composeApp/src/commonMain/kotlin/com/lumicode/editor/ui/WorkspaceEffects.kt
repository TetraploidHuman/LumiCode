package com.lumicode.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.OverlayMode

@Composable
fun WorkspaceEffects(state: IdeState) {
    LaunchedEffect(Unit) {
        // 先让首帧画出来，再恢复工作区，避免卡在加载动画后的白屏。
        kotlinx.coroutines.yield()
        if (!state.workspaceMounted) {
            runCatching {
                val restored = state.tryRestoreWorkspace()
                if (!restored) state.openWorkspacePicker()
            }.onFailure {
                state.appendTerminal("[!!] 恢复工作区失败 · ${it.message}", com.lumicode.editor.state.LineKind.ERROR)
                state.openWorkspacePicker()
            }
        }
    }

    LaunchedEffect(state.loadTick) {
        if (state.loadTick == 0) return@LaunchedEffect
        for (path in state.drainLoadQueue()) {
            state.flushLoad(path)
        }
    }

    LaunchedEffect(state.persistTick) {
        if (state.persistTick == 0) return@LaunchedEffect
        for (path in state.drainPersistQueue()) {
            state.flushPersist(path)
        }
        if (state.workspaceMounted) state.statusMessage = "工作区 · 已落盘"
    }

    LaunchedEffect(state.deleteTick) {
        if (state.deleteTick == 0) return@LaunchedEffect
        for (path in state.drainDeleteQueue()) {
            state.flushDelete(path)
        }
    }

    LaunchedEffect(state.runToken) {
        if (state.runToken == 0) return@LaunchedEffect
        if (state.shellBusy) return@LaunchedEffect
        val cmd = state.shellDraft.trim().ifEmpty { state.defaultRunCommand() }
        state.shellDraft = cmd
        state.runShellCommand(cmd)
    }
}
