package com.lumicode.editor.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.state.BoardTaskStatus
import com.lumicode.editor.state.CollabState
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.MemberLiveStatus
import com.lumicode.editor.state.TaskStatus
import com.lumicode.editor.ui.components.GhostButton
import com.lumicode.editor.ui.components.HGap
import com.lumicode.editor.ui.components.Label
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.components.SolidBarButton
import com.lumicode.editor.ui.components.TabRow
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlType

/**
 * 右栏小队：精简但仍可交互（spawn / 拍板 / 进度）。
 * 完整 Roster / 双列任务板 / 深会话在 [SquadWorkspace]。
 */
@Composable
fun CollaboratePanel(state: IdeState, modifier: Modifier = Modifier) {
    val collab = state.collab

    CollabDshEffects(state)

    Column(
        modifier
            .width(RlDimens.referenceWidth)
            .fillMaxHeight()
            .padding(start = RlDimens.panelGap, top = 16.dp, end = 6.dp),
    ) {
        RightPaneTabs(state)
        Spacer(Modifier.height(12.dp))
        SquadGlance(collab, state)
    }
}

@Composable
fun RightPaneTabs(state: IdeState) {
    TabRow(
        titles = listOf("01 参考", "02 小队"),
        selected = state.collab.rightTab,
        onSelect = { state.collab.rightTab = it },
        modifier = Modifier.fillMaxWidth(),
        fontSize = 12.sp,
        gap = 18.dp,
    )
}

@Composable
private fun SquadGlance(collab: CollabState, state: IdeState) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlanceDot(on = collab.isRunning || collab.hasProposal)
            HGap(8.dp)
            LabelRaw(text = "小队", style = RlType.label(13.sp, RlColors.Ink))
            Spacer(Modifier.weight(1f))
            GhostButton(text = "打开小队", onClick = { state.openSquadPage() })
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = collab.briefing,
            style = RlType.mono.copy(fontSize = 11.5.sp, color = RlColors.Accent),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))
            UserKnownIntelPanel(collab, state, compact = true)
            Spacer(Modifier.height(12.dp))
            Label("进度", style = RlType.label(10.5.sp, RlColors.Faint))
            Spacer(Modifier.height(6.dp))
            ProgressBlock(collab)

            run {
                val proposals = collab.awaitingYou
                val tracker = rememberSyncedPresence(proposals) { it.id }
                AnimatedSection(visible = tracker.hasPresence) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Label("待你拍板", style = RlType.label(10.5.sp, RlColors.Faint))
                        Spacer(Modifier.height(6.dp))
                        PresenceItems(tracker) { task ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .wash(RlColors.AccentSoft)
                                    .padding(8.dp),
                            ) {
                                LabelRaw(
                                    text = "${task.agentName} · ${task.title}",
                                    style = RlType.label(11.sp, RlColors.Ink),
                                    maxLines = 2,
                                )
                                val range = task.proposalPath?.let {
                                    "$it:${task.proposalStart}–${task.proposalEnd}"
                                } ?: task.statusLine
                                LabelRaw(text = range, style = RlType.mono.copy(fontSize = 10.sp, color = RlColors.Muted), maxLines = 1)
                                Spacer(Modifier.height(6.dp))
                                ProposalUserBrief(task, collab, compact = true)
                                Spacer(Modifier.height(6.dp))
                                ProposalActions(
                                    onAgree = {
                                        collab.selectTask(task.id)
                                        task.proposalPath?.let { state.open(it, revealLine = task.proposalStart) }
                                        scope.launch { state.applyAgentProposal(task.id) }
                                    },
                                    onRetry = {
                                        collab.tryAgain(task.id)
                                        state.statusMessage = "小队 · ${collab.phaseLabel}"
                                    },
                                    onOpinion = { note ->
                                        collab.giveOpinion(task.id, note)
                                        state.statusMessage = "小队 · 已送出意见"
                                    },
                                    onCancel = {
                                        collab.leaveIt(task.id)
                                        state.statusMessage = "小队 · ${collab.phaseLabel}"
                                    },
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Label("在场", style = RlType.label(10.5.sp, RlColors.Faint))
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .wash(RlColors.FieldDeep)
                    .padding(10.dp),
            ) {
                if (collab.workers.isEmpty()) {
                    LabelRaw(text = "尚无同伴 · 下方发布任务即可开路", style = RlType.label(11.sp, RlColors.Faint))
                } else {
                    collab.workers.forEach { w ->
                        val st = collab.liveStatusOf(w)
                        val task = w.taskId?.let { id -> collab.tasks.firstOrNull { it.id == id } }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LabelRaw(
                                text = w.name,
                                style = RlType.label(12.sp, RlColors.AccentDeep),
                                modifier = Modifier.width(28.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                BasicText(
                                    text = w.dynamic,
                                    style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.InkSoft),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (task != null) {
                                    BasicText(
                                        text = task.title,
                                        style = RlType.mono.copy(fontSize = 10.sp, color = RlColors.Faint),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            LabelRaw(
                                text = when (st) {
                                    MemberLiveStatus.RUNNING -> "跑"
                                    MemberLiveStatus.PROVISIONING -> "启"
                                    MemberLiveStatus.FAILED -> "败"
                                    MemberLiveStatus.INACTIVE -> "闲"
                                },
                                style = RlType.label(
                                    10.sp,
                                    if (st == MemberLiveStatus.RUNNING) RlColors.Accent else RlColors.Faint,
                                ),
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(8.dp))
        Label(
            if (collab.workers.isEmpty()) "发布任务（开路）" else "再开一路",
            style = RlType.label(10.5.sp, RlColors.Faint),
        )
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = collab.draft,
            onValueChange = { collab.draft = it },
            textStyle = RlType.mono.copy(fontSize = 12.sp, color = RlColors.InkSoft),
            cursorBrush = SolidColor(RlColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .wash(RlColors.FieldDeep)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        SolidBarButton(
            text = if (collab.workers.isEmpty()) "发布并开路" else "再开一路",
            trailing = "写入任务板",
            enabled = collab.draft.isNotBlank(),
            onClick = {
                collab.openLane()
                state.statusMessage = "小队 · ${collab.phaseLabel}"
            },
        )
        if (collab.workers.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(text = "全遣散", onClick = {
                    collab.stop()
                    state.statusMessage = "小队 · ${collab.phaseLabel}"
                })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProgressBlock(collab: CollabState) {
    val views = collab.boardView().take(6)
    val tracker = rememberSyncedPresence(views) { it.task.id }
    Column(
        Modifier
            .fillMaxWidth()
            .wash(RlColors.FieldDeep)
            .padding(10.dp),
    ) {
        AnimatedSection(visible = !tracker.hasPresence) {
            LabelRaw(text = "任务板空 · 发布后出现进度", style = RlType.label(11.sp, RlColors.Faint))
        }
        PresenceItems(tracker) { v ->
            val t = v.task
            val tag = when (t.status) {
                BoardTaskStatus.PENDING -> if (v.ready) "待认领" else "阻塞"
                BoardTaskStatus.IN_PROGRESS -> "进行中"
                BoardTaskStatus.COMPLETED -> "完成"
            }
            val color = when (t.status) {
                BoardTaskStatus.PENDING -> if (v.ready) RlColors.Faint else RlColors.Accent
                BoardTaskStatus.IN_PROGRESS -> RlColors.Accent
                BoardTaskStatus.COMPLETED -> RlColors.CodeString
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelRaw(text = tag, style = RlType.label(10.sp, color), modifier = Modifier.width(42.dp))
                Column(Modifier.weight(1f)) {
                    LabelRaw(text = t.subject, style = RlType.label(11.sp, RlColors.Ink), maxLines = 1)
                    LabelRaw(
                        text = t.ownerName ?: "未认领",
                        style = RlType.mono.copy(fontSize = 10.sp, color = RlColors.Faint),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
        }
        if (collab.boardTasks.size > 6) {
            LabelRaw(
                text = "…另 ${collab.boardTasks.size - 6} 条 · 打开小队可看全",
                style = RlType.label(10.sp, RlColors.Faint),
            )
        }
    }
}

@Composable
private fun GlanceDot(on: Boolean) {
    val pulse by rememberInfiniteTransition(label = "glance").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glancePulse",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(12.dp),
    ) {
        if (on) {
            Box(
                Modifier
                    .size(12.dp)
                    .graphicsLayer { alpha = pulse * 0.65f }
                    .wash(RlColors.AccentSoft),
            )
        }
        Box(
            Modifier
                .size(5.dp)
                .graphicsLayer { alpha = if (on) 0.55f + 0.45f * pulse else 1f }
                .wash(if (on) RlColors.Accent else RlColors.Faint),
        )
    }
}
