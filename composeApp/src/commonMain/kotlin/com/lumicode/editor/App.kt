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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lumicode.editor.resources.Res
import com.lumicode.editor.resources.jbmono_bold
import com.lumicode.editor.resources.jbmono_medium
import com.lumicode.editor.resources.jbmono_regular
import com.lumicode.editor.resources.noto_sans_bold
import com.lumicode.editor.resources.noto_sans_regular
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.LineKind
import com.lumicode.editor.state.OverlayMode
import com.lumicode.editor.state.defaultCommands
import com.lumicode.editor.ui.EditorPanel
import com.lumicode.editor.ui.ExplorerPanel
import com.lumicode.editor.ui.NavigationRow
import com.lumicode.editor.ui.OutputPanel
import com.lumicode.editor.ui.OverlayHost
import com.lumicode.editor.ui.ReferencePanel
import com.lumicode.editor.ui.StatusBar
import com.lumicode.editor.ui.TelemetryRail
import com.lumicode.editor.ui.TopBar
import com.lumicode.editor.ui.outlineOf
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlFonts
import com.lumicode.editor.ui.theme.RlMotion
import com.lumicode.editor.ui.theme.RlSettings
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font

/**
 * Root of the ANALYSIS OS shell. Identical on Android, desktop and wasm.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun InstallArchiveFonts() {
    // 内置字体：正文用 Noto Sans CJK，代码/标签用 JetBrains Mono（并把 Noto 的中文字形
    // 合并进同一族，见 tools/build_mono_font.py）。wasm 画布没有系统字体回退，必须自带。
    val sans = FontFamily(
        Font(Res.font.noto_sans_regular, FontWeight.Normal),
        Font(Res.font.noto_sans_bold, FontWeight.Bold),
    )
    val mono = FontFamily(
        Font(Res.font.jbmono_regular, FontWeight.Normal),
        Font(Res.font.jbmono_medium, FontWeight.Medium),
        Font(Res.font.jbmono_bold, FontWeight.Bold),
    )
    SideEffect {
        RlFonts.sans = sans
        RlFonts.mono = mono
    }
}

@Composable
fun App(state: IdeState) {
    InstallArchiveFonts()
    val commands = remember(state) { defaultCommands(state) { state.requestRun() } }
    val clock = rememberClock()
    val fps = rememberFps()
    val rootFocus = remember { FocusRequester() }

    // Simulated analysis pass (F5 / Ctrl+Enter / RUN ANALYSIS).
    LaunchedEffect(state.runToken) {
        if (state.runToken == 0) return@LaunchedEffect
        val file = state.activeFile ?: return@LaunchedEffect
        state.outputVisible = true
        state.statusMessage = "分析运行中"
        state.appendTerminal("[运行] 分析流程 · ${file.name}", LineKind.INFO)
        delay(180)
        state.appendTerminal("      词法分析 .................... 通过", LineKind.OK)
        delay(160)
        val symbols = outlineOf(state.activeContent).size
        state.appendTerminal("      符号 ${symbols.toString().padStart(3, '0')} 个 ................... 通过", LineKind.OK)
        delay(200)
        state.rescanProblems()
        if (state.problems.isEmpty()) {
            state.appendTerminal("      静态检查 .................... 无问题", LineKind.OK)
        } else {
            state.problems.take(3).forEach { problem ->
                state.appendTerminal(
                    "      ${problem.path.substringAfterLast('/')}:${problem.line} ${problem.message}",
                    if (problem.severity == LineKind.ERROR) LineKind.ERROR else LineKind.WARN,
                )
            }
        }
        delay(160)
        state.appendTerminal("      档案 ${file.meta.archiveNo} → 可读取", LineKind.OK)
        state.appendTerminal("[完成] 分析结束 · ${state.problems.size} 个发现", LineKind.INFO)
        state.statusMessage = "分析完成"
        state.appendLog("运行 ${file.name}")
    }

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
                        true
                    }

                    ctrl && event.key == Key.J -> {
                        state.outputVisible = !state.outputVisible
                        true
                    }

                    ctrl && event.key == Key.F -> {
                        state.findVisible = !state.findVisible
                        state.findActiveMatch = 0
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

                    event.key == Key.Escape -> {
                        state.handleEscape()
                        true
                    }

                    else -> false
                }
            }
            .pointerInput(Unit) {
                // 点击任何位置都把键盘焦点交回外壳，避免「按 ESC / 快捷键没反应」
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) rootFocus.requestFocus()
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
                            start = if (state.explorerVisible && !compact) RlDimens.seam else RlDimens.pagePad,
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
                    ReferencePanel(state)
                }
                if (!compact && RlSettings.showRail) TelemetryRail(state, clock, fps)
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
                    state.referenceVisible -> ReferencePanel(state)
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
