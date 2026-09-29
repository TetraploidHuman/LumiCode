package com.lumicode.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.OverlayMode
import com.lumicode.editor.state.ShellPage
import com.lumicode.editor.state.defaultCommands
import com.lumicode.editor.ui.CollaboratePanel
import com.lumicode.editor.ui.EditorPanel
import com.lumicode.editor.ui.ExplorerPanel
import com.lumicode.editor.ui.NavigationRow
import com.lumicode.editor.ui.OutputPanel
import com.lumicode.editor.ui.OverlayHost
import com.lumicode.editor.ui.ReferencePanel
import com.lumicode.editor.ui.SquadWorkspace
import com.lumicode.editor.ui.StatusBar
import com.lumicode.editor.ui.WorkspaceEffects
import com.lumicode.editor.ui.TelemetryRail
import com.lumicode.editor.ui.TopBar
import com.lumicode.editor.ui.theme.InstallArchiveFonts
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlMotion
import com.lumicode.editor.ui.theme.RlSettings

/**
 * Root of the ANALYSIS OS shell. Identical on Android, desktop and wasm.
 */
@Composable
fun App(state: IdeState) {
    InstallArchiveFonts()
    WorkspaceEffects(state)
    val commands = remember(state) { defaultCommands(state) { state.requestRun() } }
    val clock = rememberClock()
    val fps = rememberFps()
    val rootFocus = remember { FocusRequester() }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(RlColors.FieldTop, RlColors.Field, RlColors.FieldBottom),
                ),
            )
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val ctrl = event.isCtrlPressed || event.isMetaPressed
                val shift = event.isShiftPressed
                val overlayOpen = state.overlay != OverlayMode.NONE
                when {
                    ctrl && event.key == Key.K -> {
                        state.toggleOverlay(OverlayMode.COMMAND_INDEX)
                        true
                    }

                    ctrl && event.key == Key.P -> {
                        state.toggleOverlay(OverlayMode.QUICK_OPEN)
                        true
                    }

                    ctrl && shift && event.key == Key.F -> {
                        state.toggleOverlay(OverlayMode.WORKSPACE_SEARCH)
                        true
                    }

                    ctrl && event.key == Key.G -> {
                        state.openOverlay(OverlayMode.GOTO_LINE)
                        true
                    }

                    event.key == Key.Escape -> {
                        state.handleEscape()
                        true
                    }

                    overlayOpen -> false

                    ctrl && event.key == Key.S -> {
                        state.save()
                        true
                    }

                    ctrl && event.key == Key.N -> {
                        state.newFile()
                        true
                    }

                    ctrl && event.key == Key.B -> {
                        state.explorerVisible = !state.explorerVisible
                        state.persistPanelPrefs()
                        true
                    }

                    ctrl && event.key == Key.J -> {
                        state.outputVisible = !state.outputVisible
                        state.persistPanelPrefs()
                        true
                    }

                    ctrl && event.key == Key.R -> {
                        state.referenceVisible = !state.referenceVisible
                        state.persistPanelPrefs()
                        true
                    }

                    ctrl && shift && event.key == Key.A -> {
                        state.openSquadPage()
                        true
                    }

                    ctrl && event.key == Key.E -> {
                        state.exportBundle()
                        true
                    }

                    ctrl && event.key == Key.Tab -> {
                        state.cycleTab(if (shift) -1 else 1)
                        true
                    }

                    ctrl && event.key == Key.F -> {
                        state.openFind(replace = false)
                        true
                    }

                    ctrl && event.key == Key.H -> {
                        state.openFind(replace = true)
                        true
                    }

                    ctrl && event.key == Key.Slash -> {
                        state.toggleOverlay(OverlayMode.SHORTCUTS)
                        true
                    }

                    ctrl && event.key == Key.W -> {
                        state.activePath?.let { state.close(it) }
                        true
                    }

                    event.key == Key.F5 -> {
                        state.requestRun()
                        true
                    }

                    else -> false
                }
            }
            .pointerInput(Unit) {
                // 只在「没人处理」的点击上把焦点交回外壳。
                // 不能用 Initial 抢焦点：会先让编辑器失焦再聚焦，CoreTextField 的
                // BringIntoView 会把滚动拽回点击前的旧光标（滚轮后再点就回弹）。
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        if (event.type == PointerEventType.Press &&
                            event.changes.none { it.isConsumed }
                        ) {
                            rootFocus.requestFocus()
                        }
                    }
                }
            }
            .focusable(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 900.dp
        LaunchedEffect(compact) {
            if (compact && !state.compactApplied) {
                state.compactApplied = true
                state.explorerVisible = false
                state.referenceVisible = false
                state.outputVisible = false
                // 手机上写代码少，默认进小队看进度与拍板
                state.openSquadPage()
            }
        }

        Column(Modifier.fillMaxSize()) {
            TopBar(
                state,
                compact = compact,
                onOpenSettings = { state.openOverlay(OverlayMode.SETTINGS) },
                onOpenOverview = { state.openOverlay(OverlayMode.OVERVIEW) },
            )
            NavigationRow(
                state,
                compact = compact,
                onOpenOverview = { state.openOverlay(OverlayMode.OVERVIEW) },
            )

            Row(Modifier.weight(1f).fillMaxWidth()) {
                when (state.shellPage) {
                    ShellPage.SQUAD -> {
                        SquadWorkspace(state, Modifier.weight(1f).fillMaxWidth(), compact = compact)
                    }
                    ShellPage.CODE -> {
                // 侧栏的出现/消失也走宽度动画：中栏是 weight(1f)，会跟着一起收放，
                // 所以代码区是"被让出空间"而不是"被闪一下"
                AnimatedVisibility(
                    visible = state.explorerVisible && !compact,
                    enter = expandHorizontally(
                        animationSpec = RlMotion.enter(200),
                        expandFrom = Alignment.Start,
                    ) + fadeIn(RlMotion.enter(160)),
                    exit = shrinkHorizontally(
                        animationSpec = RlMotion.exit(150),
                        shrinkTowards = Alignment.Start,
                    ) + fadeOut(RlMotion.exit(90)),
                    label = "explorer",
                ) {
                    ExplorerPanel(state)
                }
                // 不再有"编辑卡片"：整个中栏就是一块连续的平面，
                // 代码、标签、页脚都直接落在场上，只靠留白分区。
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            start = if (state.explorerVisible && !compact) RlDimens.panelGap else RlDimens.pagePad,
                            end = RlDimens.pagePad,
                            bottom = 10.dp,
                        ),
                ) {
                    EditorPanel(
                        state,
                        onRun = { state.requestRun() },
                        compact = compact,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    // 控制台：从下沿撑开（编辑器同步收窄），收起比展开更快
                    AnimatedVisibility(
                        visible = state.outputVisible,
                        enter = expandVertically(
                            animationSpec = RlMotion.enter(200),
                            expandFrom = Alignment.Bottom,
                        ) + fadeIn(RlMotion.enter(160)),
                        exit = shrinkVertically(
                            animationSpec = RlMotion.exit(150),
                            shrinkTowards = Alignment.Bottom,
                        ) + fadeOut(RlMotion.exit(90)),
                        label = "console",
                    ) {
                        Column {
                            Spacer(Modifier.height(RlDimens.seam))
                            OutputPanel(
                                state,
                                modifier = Modifier.height(190.dp).fillMaxWidth(),
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = state.referenceVisible && !compact,
                    enter = expandHorizontally(
                        animationSpec = RlMotion.enter(200),
                        expandFrom = Alignment.End,
                    ) + fadeIn(RlMotion.enter(160)),
                    exit = shrinkHorizontally(
                        animationSpec = RlMotion.exit(150),
                        shrinkTowards = Alignment.End,
                    ) + fadeOut(RlMotion.exit(90)),
                    label = "reference",
                ) {
                    if (state.collab.rightTab == 1) {
                        CollaboratePanel(state)
                    } else {
                        ReferencePanel(state)
                    }
                }
                if (!compact && RlSettings.showRail) TelemetryRail(state, fps)
                    }
                }
            }

            StatusBar(state, clock, compact = compact)
        }

        // 窄屏：资源管理器 / 参考区从左侧滑入，遮罩同时淡入
        val drawerOpen = compact && (state.explorerVisible || state.referenceVisible)
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(RlMotion.enter(150)),
            exit = fadeOut(RlMotion.exit()),
            label = "drawerScrim",
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x33121211))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        state.explorerVisible = false
                        state.referenceVisible = false
                    },
            )
        }
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally(RlMotion.enter(200)) { -it } + fadeIn(RlMotion.enter(150)),
            exit = slideOutHorizontally(RlMotion.exit(150)) { -it } + fadeOut(RlMotion.exit(90)),
            label = "drawer",
        ) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .padding(top = 96.dp)
                    .background(RlColors.Field)
                    .zIndex(2f),
            ) {
                when {
                    state.explorerVisible -> ExplorerPanel(state, showFooter = false)
                    state.referenceVisible -> {
                        if (state.collab.rightTab == 1) {
                            CollaboratePanel(state)
                        } else {
                            ReferencePanel(state)
                        }
                    }
                }
            }
        }

        OverlayHost(state, commands, compact)
        }
    }

    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    // Overlays and the find bar own focus while they are open. When they close, focus
    // would otherwise be cleared and global shortcuts would stop being dispatched, so
    // the shell takes it back.
    LaunchedEffect(state.overlay, state.findVisible) {
        if (state.overlay == OverlayMode.NONE && !state.findVisible) rootFocus.requestFocus()
    }
}
