package com.lumicode.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.state.CollabMode
import com.lumicode.editor.state.CollabPhase
import com.lumicode.editor.state.CollabState
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.PlanNodeStatus
import com.lumicode.editor.ui.components.AccentTick
import com.lumicode.editor.ui.components.GhostButton
import com.lumicode.editor.ui.components.HGap
import com.lumicode.editor.ui.components.Label
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.components.SolidBarButton
import com.lumicode.editor.ui.components.TabRow
import com.lumicode.editor.ui.components.clickableFlat
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlType
import kotlinx.coroutines.delay

/**
 * 共作台：一阶段一块内容，底部一个主操作。不堆重复按钮。
 */
@Composable
fun CollaboratePanel(state: IdeState, modifier: Modifier = Modifier) {
    val collab = state.collab

    LaunchedEffect(collab.surveyToken) {
        if (collab.surveyToken == 0) return@LaunchedEffect
        if (collab.phase != CollabPhase.SURVEYING) return@LaunchedEffect
        delay(700)
        collab.finishSurvey()
        state.statusMessage = collab.phaseLabel
    }

    LaunchedEffect(collab.phase) {
        if (collab.phase != CollabPhase.IDLE) {
            state.statusMessage = "共作 · ${collab.phaseLabel}"
        }
    }

    Column(
        modifier
            .width(RlDimens.referenceWidth)
            .fillMaxHeight()
            .padding(start = RlDimens.panelGap, top = 16.dp, end = 6.dp),
    ) {
        RightPaneTabs(state)
        Spacer(Modifier.height(14.dp))
        HeaderRow(collab)
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            PhaseBody(collab, state)
        }

        Spacer(Modifier.height(10.dp))
        FooterActions(collab, state)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun RightPaneTabs(state: IdeState) {
    TabRow(
        titles = listOf("01 参考", "02 共作"),
        selected = state.collab.rightTab,
        onSelect = { state.collab.rightTab = it },
        modifier = Modifier.fillMaxWidth(),
        fontSize = 12.sp,
        gap = 18.dp,
    )
}

@Composable
private fun HeaderRow(collab: CollabState) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentTick(length = 16.dp)
            HGap(8.dp)
            LabelRaw(text = collab.phaseLabel, style = RlType.label(13.sp, RlColors.Accent))
            Spacer(Modifier.weight(1f))
            ModePills(collab)
        }
        Spacer(Modifier.height(6.dp))
        LabelRaw(text = collab.modeHint, style = RlType.label(10.5.sp, RlColors.Faint))
    }
}

@Composable
private fun ModePills(collab: CollabState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(
            CollabMode.COWRITE to "共写",
            CollabMode.DIRECT to "指挥",
            CollabMode.TAKEOVER to "接管",
        ).forEach { (mode, label) ->
            val on = collab.mode == mode
            LabelRaw(
                text = label,
                style = RlType.label(11.sp, if (on) RlColors.Ink else RlColors.Faint),
                modifier = Modifier
                    .clickableFlat { collab.applyMode(mode) }
                    .then(if (on) Modifier.wash(RlColors.AccentSoft) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun PhaseBody(collab: CollabState, state: IdeState) {
    when (collab.phase) {
        CollabPhase.IDLE -> IdleBody(collab)
        CollabPhase.SURVEYING -> QuietLine("只读摸清影响面…")
        CollabPhase.AWAIT_PLAN -> PlanBody(collab)
        CollabPhase.RUNNING -> QuietLine("Agent 起草中…")
        CollabPhase.AWAIT_PATCH -> PatchBody(collab, state)
        CollabPhase.HANDWRITING -> HandwritingBody(collab, state)
        CollabPhase.DONE -> DoneBody(collab)
    }
}

@Composable
private fun QuietLine(text: String) {
    LabelRaw(text = text, style = RlType.label(12.sp, RlColors.Muted))
}

@Composable
private fun IdleBody(collab: CollabState) {
    Column(Modifier.fillMaxWidth()) {
        Label("这一轮要做什么", style = RlType.label(12.sp, RlColors.Ink))
        Spacer(Modifier.height(10.dp))
        Field("目标", collab.intentGoal) { collab.intentGoal = it }
        Spacer(Modifier.height(8.dp))
        Field("不做", collab.intentNonGoal) { collab.intentNonGoal = it }
        Spacer(Modifier.height(8.dp))
        Field("怎样算完成", collab.intentAcceptance) { collab.intentAcceptance = it }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        LabelRaw(text = label, style = RlType.label(10.5.sp, RlColors.Faint))
        Spacer(Modifier.height(3.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = RlType.mono.copy(fontSize = 11.5.sp, color = RlColors.InkSoft),
            cursorBrush = SolidColor(RlColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .wash(RlColors.FieldDeep)
                .padding(horizontal = 8.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun PlanBody(collab: CollabState) {
    Column(Modifier.fillMaxWidth()) {
        IntentSummary(collab)
        Spacer(Modifier.height(14.dp))
        Label("步骤", style = RlType.label(12.sp, RlColors.Ink))
        Spacer(Modifier.height(8.dp))
        StepList(collab)
        Spacer(Modifier.height(10.dp))
        LabelRaw(
            text = "确认后 Agent 才开始改代码",
            style = RlType.label(11.sp, RlColors.Muted),
        )
    }
}

@Composable
private fun IntentSummary(collab: CollabState) {
    Column(
        Modifier
            .fillMaxWidth()
            .wash(RlColors.FieldDeep)
            .padding(10.dp),
    ) {
        LabelRaw(text = "意图", style = RlType.label(10.5.sp, RlColors.Faint))
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = collab.intentGoal,
            style = RlType.mono.copy(fontSize = 12.sp, color = RlColors.Ink),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (collab.intentNonGoal.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            LabelRaw(
                text = "不做 · ${collab.intentNonGoal}",
                style = RlType.label(10.5.sp, RlColors.Muted),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun StepList(collab: CollabState) {
    collab.planNodes.forEachIndexed { index, node ->
        val active = node.id == collab.currentNodeId || node.status == PlanNodeStatus.AWAIT_YOU
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (active) Modifier.wash(RlColors.AccentSoft) else Modifier)
                .padding(vertical = 5.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelRaw(
                text = (index + 1).toString().padStart(2, '0'),
                style = RlType.mono.copy(
                    fontSize = 11.sp,
                    color = if (active) RlColors.Accent else RlColors.Faint,
                ),
            )
            HGap(10.dp)
            BasicText(
                text = node.title,
                style = RlType.mono.copy(
                    fontSize = 12.sp,
                    color = when (node.status) {
                        PlanNodeStatus.DONE -> RlColors.Muted
                        PlanNodeStatus.AWAIT_YOU, PlanNodeStatus.ACTIVE -> RlColors.Ink
                        else -> RlColors.InkSoft
                    },
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (node.status == PlanNodeStatus.DONE) {
                LabelRaw(text = "✓", style = RlType.label(11.sp, RlColors.CodeString))
            }
        }
    }
}

@Composable
private fun PatchBody(collab: CollabState, state: IdeState) {
    val patch = collab.patches.firstOrNull()
    Column(Modifier.fillMaxWidth()) {
        IntentSummary(collab)
        Spacer(Modifier.height(14.dp))
        if (collab.planNodes.isNotEmpty()) {
            StepList(collab)
            Spacer(Modifier.height(14.dp))
        }
        Label("待批补丁", style = RlType.label(12.sp, RlColors.Ink))
        Spacer(Modifier.height(8.dp))
        if (patch == null) {
            QuietLine("没有补丁")
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickableFlat {
                        collab.selectedPatchPath = patch.path
                        state.open(patch.path, revealLine = patch.startLine)
                    }
                    .wash(RlColors.FieldDeep)
                    .padding(10.dp),
            ) {
                LabelRaw(
                    text = "${patch.path}  ·  L${patch.startLine}–${patch.endLine}",
                    style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.Accent),
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = patch.summary,
                    style = RlType.body.copy(fontSize = 12.sp, color = RlColors.Ink),
                )
                Spacer(Modifier.height(8.dp))
                LabelRaw(
                    text = "盲区 · ${patch.blindSpots}",
                    style = RlType.label(10.5.sp, RlColors.Muted),
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                LabelRaw(
                    text = "未改 · ${patch.untouched}",
                    style = RlType.label(10.5.sp, RlColors.Faint),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(8.dp))
            LabelRaw(
                text = if (collab.mode == CollabMode.COWRITE) {
                    "共写 · 可先点进文件过目，或自己改这段"
                } else {
                    "指挥 · 看完摘要即可批准"
                },
                style = RlType.label(11.sp, RlColors.Muted),
            )
        }
    }
}

@Composable
private fun HandwritingBody(collab: CollabState, state: IdeState) {
    val patch = collab.patches.firstOrNull()
    Column(Modifier.fillMaxWidth()) {
        QuietLine("你在写代码 · Agent 旁路待命")
        Spacer(Modifier.height(12.dp))
        if (patch != null) {
            GhostButton(
                text = "跳到 ${patch.path}:${patch.startLine}",
                glyph = "→",
                onClick = { state.open(patch.path, revealLine = patch.startLine) },
            )
        }
        Spacer(Modifier.height(8.dp))
        LabelRaw(
            text = "写完后点下方「交还 Agent」继续协作",
            style = RlType.label(11.sp, RlColors.Faint),
        )
    }
}

@Composable
private fun DoneBody(collab: CollabState) {
    Column(Modifier.fillMaxWidth()) {
        LabelRaw(text = "本轮已归档", style = RlType.label(13.sp, RlColors.CodeString))
        Spacer(Modifier.height(8.dp))
        LabelRaw(
            text = collab.intentGoal,
            style = RlType.mono.copy(fontSize = 11.5.sp, color = RlColors.Muted),
            maxLines = 2,
        )
    }
}

@Composable
private fun FooterActions(collab: CollabState, state: IdeState) {
    val primary = collab.primaryActionLabel
    Column(Modifier.fillMaxWidth()) {
        // 次要操作：一行，最多两个
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            when (collab.phase) {
                CollabPhase.AWAIT_PLAN, CollabPhase.AWAIT_PATCH -> {
                    GhostButton(text = "打回", onClick = {
                        collab.revisePlan()
                        state.statusMessage = "共作 · ${collab.phaseLabel}"
                    })
                }
                else -> Unit
            }
            if (collab.showHandwritingCta && collab.phase == CollabPhase.AWAIT_PATCH) {
                GhostButton(text = "我来写这段", onClick = {
                    val patch = collab.patches.firstOrNull()
                    collab.takeOverHandwriting()
                    patch?.let { state.open(it.path, revealLine = it.startLine) }
                    state.statusMessage = "共作 · ${collab.phaseLabel}"
                })
            }
            Spacer(Modifier.weight(1f))
        }
        if (primary != null) {
            Spacer(Modifier.height(6.dp))
            SolidBarButton(
                text = primary,
                trailing = footerTrailing(collab),
                onClick = {
                    collab.primaryAction()
                    state.statusMessage = "共作 · ${collab.phaseLabel}"
                },
                enabled = collab.phase != CollabPhase.IDLE || collab.intentGoal.isNotBlank(),
            )
        }
    }
}

private fun footerTrailing(collab: CollabState): String? = when (collab.phase) {
    CollabPhase.IDLE -> "开始"
    CollabPhase.AWAIT_PLAN -> "授权执行"
    CollabPhase.AWAIT_PATCH -> if (collab.mode == CollabMode.COWRITE) "过目后" else "授权"
    CollabPhase.HANDWRITING -> "继续协作"
    CollabPhase.DONE -> "清空"
    else -> null
}
