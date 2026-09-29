package com.lumicode.editor.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import com.lumicode.editor.state.AgentJournalEntry
import com.lumicode.editor.state.BoardTaskStatus
import com.lumicode.editor.state.BoardTaskView
import com.lumicode.editor.state.CollabState
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.LineKind
import com.lumicode.editor.state.MemberLiveStatus
import com.lumicode.editor.state.RosterRow
import com.lumicode.editor.state.TaskStatus
import com.lumicode.editor.ui.components.GhostButton
import com.lumicode.editor.ui.components.HGap
import com.lumicode.editor.ui.components.Label
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.components.SolidBarButton
import com.lumicode.editor.ui.components.clickableFlat
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlType

/**
 * 与代码编辑平级的小队工作台。
 * [compact] 为真时走手机单列：优先拍板 / 监视，再开路与成员。
 */
@Composable
fun SquadWorkspace(state: IdeState, modifier: Modifier = Modifier, compact: Boolean = false) {
    val collab = state.collab

    CollabDshEffects(state)

    val padH = if (compact) 12.dp else RlDimens.pagePad
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = padH, vertical = if (compact) 4.dp else 8.dp),
    ) {
        SquadHeader(collab, state, compact)
        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))

        if (collab.detailAgentId != null) {
            if (compact) {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    SquadTeammateSession(collab, state, compact = true)
                }
            } else {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.widthIn(max = 280.dp).fillMaxHeight().weight(0.32f, fill = false)) {
                        SquadRosterColumn(collab, state, listCompact = true)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        SquadTeammateSession(collab, state, compact = false)
                    }
                }
            }
        } else if (compact) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SquadAwaiting(collab, state, compact = true)
                Spacer(Modifier.height(14.dp))
                SquadSpawnBar(collab, state, compact = true)
                Spacer(Modifier.height(14.dp))
                SquadRosterColumn(collab, state, listCompact = true)
                Spacer(Modifier.height(14.dp))
                SquadTaskBoard(collab)
                Spacer(Modifier.height(14.dp))
                SquadEvents(collab, compact = true)
                Spacer(Modifier.height(14.dp))
                UserKnownIntelPanel(collab, state, compact = true)
                Spacer(Modifier.height(28.dp))
            }
        } else {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    Modifier
                        .widthIn(min = 260.dp, max = 320.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    SquadRosterColumn(collab, state, listCompact = false)
                    Spacer(Modifier.height(16.dp))
                    SquadSpawnBar(collab, state, compact = false)
                    Spacer(Modifier.height(16.dp))
                    UserKnownIntelPanel(collab, state, compact = false)
                    Spacer(Modifier.height(16.dp))
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    SquadAwaiting(collab, state, compact = false)
                    Spacer(Modifier.height(20.dp))
                    SquadTaskBoard(collab)
                    Spacer(Modifier.height(20.dp))
                    SquadEvents(collab, compact = false)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SquadHeader(collab: CollabState, state: IdeState, compact: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabelRaw(
                text = "小队",
                style = RlType.label(if (compact) 16.sp else 18.sp, RlColors.Ink),
            )
            HGap(10.dp)
            LivePulse(on = collab.isRunning || collab.hasProposal)
            Spacer(Modifier.weight(1f))
            if (!compact) {
                if (collab.workers.isNotEmpty()) {
                    GhostButton(text = "全遣散", onClick = {
                        collab.stop()
                        state.statusMessage = "小队 · ${collab.phaseLabel}"
                    })
                }
                GhostButton(text = "回代码", onClick = { state.openCodePage() })
            }
        }
        if (compact) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (collab.workers.isNotEmpty()) {
                    GhostButton(text = "全遣散", onClick = {
                        collab.stop()
                        state.statusMessage = "小队 · ${collab.phaseLabel}"
                    })
                }
                Spacer(Modifier.weight(1f))
                GhostButton(text = "代码", onClick = { state.openCodePage() })
            }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        if (collab.dshLinked || collab.dshBusy || collab.dshModelLabel != null) {
            LabelRaw(
                text = when {
                    collab.dshBusy -> "DSH · 推理中…"
                    collab.dshLinked -> "DSH · ${collab.dshModelLabel ?: "qwen35-9b"}"
                    else -> "DSH · ${collab.dshModelLabel ?: "未连接"}"
                },
                style = RlType.label(
                    if (compact) 11.sp else 12.sp,
                    if (collab.dshLinked) RlColors.Accent else RlColors.Faint,
                ),
            )
            Spacer(Modifier.height(4.dp))
        }
        BasicText(
            text = collab.briefing,
            style = RlType.mono.copy(fontSize = if (compact) 12.sp else 13.sp, color = RlColors.Accent),
            maxLines = if (compact) 3 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (collab.rosterLine.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = collab.rosterLine,
                style = RlType.mono.copy(fontSize = if (compact) 11.sp else 12.sp, color = RlColors.InkSoft),
                maxLines = if (compact) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!compact) {
            Spacer(Modifier.height(6.dp))
            LabelRaw(
                text = "你是上级 · 同伴各持独立上下文 · 共享任务板协调 · 提案由你拍板",
                style = RlType.label(11.sp, RlColors.Faint),
            )
        }
    }
}

@Composable
private fun LivePulse(on: Boolean) {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "livePulse",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(14.dp)) {
        if (on) {
            Box(
                Modifier
                    .size(14.dp)
                    .graphicsLayer { alpha = pulse * 0.65f }
                    .wash(RlColors.AccentSoft),
            )
        }
        Box(
            Modifier
                .size(6.dp)
                .graphicsLayer { alpha = if (on) 0.55f + 0.45f * pulse else 1f }
                .wash(if (on) RlColors.Accent else RlColors.Faint),
        )
    }
}

@Composable
private fun SquadRosterColumn(collab: CollabState, state: IdeState, listCompact: Boolean) {
    val rows = collab.rosterRows()
    Column(Modifier.fillMaxWidth()) {
        Label("成员（${rows.size}）", style = RlType.label(11.sp, RlColors.Faint))
        Spacer(Modifier.height(if (listCompact) 8.dp else 10.dp))
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (listCompact) 8.dp else 10.dp),
        ) {
            rows.forEach { row ->
                SquadMemberCard(row, collab, state, dense = listCompact)
            }
        }
        if (rows.size <= 1) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .wash(RlColors.FieldDeep)
                    .padding(if (listCompact) 12.dp else 14.dp),
            ) {
                LabelRaw(text = "还没有同伴", style = RlType.label(12.sp, RlColors.Ink))
                Spacer(Modifier.height(6.dp))
                LabelRaw(
                    text = "在下方发布任务并开路，同伴经 DSH 回提案。",
                    style = RlType.label(11.sp, RlColors.Muted),
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun SquadMemberCard(row: RosterRow, collab: CollabState, state: IdeState, dense: Boolean = false) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (row.isLead) Modifier else Modifier.clickableFlat { collab.openDetail(row.id) })
            .wash(if (row.isCurrent && !row.isLead) RlColors.AccentSoft else RlColors.FieldDeep)
            .padding(if (dense) 12.dp else 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(row.status)
            HGap(10.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabelRaw(
                        text = row.name,
                        style = RlType.label(if (dense) 14.sp else 15.sp, if (row.isLead) RlColors.Ink else RlColors.AccentDeep),
                    )
                    HGap(8.dp)
                    LabelRaw(text = row.roleLabel, style = RlType.label(10.sp, RlColors.Faint))
                    if (row.isCurrent && !row.isLead) {
                        HGap(6.dp)
                        LabelRaw(text = "当前", style = RlType.label(10.sp, RlColors.Accent))
                    }
                }
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = row.description.ifBlank { row.dynamic },
                    style = RlType.mono.copy(fontSize = if (dense) 11.sp else 12.sp, color = RlColors.InkSoft),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                LabelRaw(text = liveLabel(row.status), style = RlType.label(11.sp, statusColor(row.status)))
                row.modelHint?.let {
                    Spacer(Modifier.height(2.dp))
                    LabelRaw(text = it, style = RlType.label(10.sp, RlColors.Faint))
                }
            }
        }
        if (!row.isLead) {
            Spacer(Modifier.height(if (dense) 8.dp else 10.dp))
            LabelRaw(text = row.dynamic, style = RlType.label(11.sp, RlColors.Muted), maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(text = if (dense) "会话" else "打开会话", onClick = { collab.openDetail(row.id) })
                Spacer(Modifier.weight(1f))
                GhostButton(text = "遣散", onClick = {
                    collab.dismissAgent(row.id)
                    state.statusMessage = "小队 · ${collab.phaseLabel}"
                })
            }
        }
    }
}

@Composable
private fun StatusDot(status: MemberLiveStatus) {
    val color = statusColor(status)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
        Box(Modifier.size(12.dp).wash(color.copy(alpha = 0.22f)))
        Box(Modifier.size(5.dp).wash(color))
    }
}

private fun liveLabel(s: MemberLiveStatus) = when (s) {
    MemberLiveStatus.RUNNING -> "进行中"
    MemberLiveStatus.INACTIVE -> "空闲"
    MemberLiveStatus.PROVISIONING -> "启动中"
    MemberLiveStatus.FAILED -> "失败"
}

private fun statusColor(s: MemberLiveStatus) = when (s) {
    MemberLiveStatus.RUNNING -> RlColors.Accent
    MemberLiveStatus.INACTIVE -> RlColors.Faint
    MemberLiveStatus.PROVISIONING -> RlColors.Muted
    MemberLiveStatus.FAILED -> RlColors.CodeNumber
}

@Composable
private fun SquadTaskBoard(collab: CollabState) {
    val views = collab.boardView()
    val tracker = rememberSyncedPresence(views) { it.task.id }
    Column(Modifier.fillMaxWidth()) {
        Label("共享任务板（${views.size}）", style = RlType.label(11.sp, RlColors.Faint))
        Spacer(Modifier.height(10.dp))
        AnimatedSection(visible = !tracker.hasPresence) {
            LabelRaw(text = "任务板为空 · 开路后会出现认领条目", style = RlType.label(12.sp, RlColors.Faint))
        }
        PresenceItems(tracker) { v ->
            BoardCard(v, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BoardCard(view: BoardTaskView, modifier: Modifier = Modifier) {
    val t = view.task
    val (dot, label) = when (t.status) {
        BoardTaskStatus.PENDING ->
            if (view.ready) RlColors.Faint to "待认领"
            else RlColors.Accent to "阻塞中"
        BoardTaskStatus.IN_PROGRESS -> RlColors.Accent to "进行中"
        BoardTaskStatus.COMPLETED -> RlColors.CodeString to "已完成"
    }
    Column(
        modifier
            .wash(RlColors.FieldDeep)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).wash(dot))
            HGap(10.dp)
            LabelRaw(text = t.subject, style = RlType.label(14.sp, RlColors.Ink), modifier = Modifier.weight(1f), maxLines = 1)
            LabelRaw(text = label, style = RlType.label(11.sp, dot))
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = t.description,
            style = RlType.mono.copy(fontSize = 12.sp, color = RlColors.InkSoft),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        LabelRaw(
            text = buildString {
                append("负责人 · ${t.ownerName ?: "未认领"}")
                if (t.blockedBy.isNotEmpty()) append("\n依赖 · ${t.blockedBy.joinToString()}")
                if (t.writeScopes.isNotEmpty()) append("\n写入 · ${t.writeScopes.joinToString()}")
            },
            style = RlType.label(11.sp, RlColors.Faint),
            maxLines = 4,
        )
        if (view.writeScopeWarnings.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LabelRaw(
                text = view.writeScopeWarnings.joinToString("；"),
                style = RlType.label(11.sp, RlColors.Accent),
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun SquadAwaiting(collab: CollabState, state: IdeState, compact: Boolean) {
    val scope = rememberCoroutineScope()
    val tracker = rememberSyncedPresence(collab.awaitingYou) { it.id }
    AnimatedSection(visible = tracker.hasPresence) {
        Column(
            Modifier
                .fillMaxWidth()
                .wash(RlColors.AccentSoft)
                .padding(if (compact) 12.dp else 16.dp),
        ) {
            LabelRaw(text = "待你拍板（${collab.awaitingYou.size}）", style = RlType.label(13.sp, RlColors.AccentDeep))
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            PresenceItems(tracker) { task ->
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            LabelRaw(text = "${task.agentName} · ${task.title}", style = RlType.label(13.sp, RlColors.Ink), maxLines = 2)
                            val range = task.proposalPath?.let { "$it:${task.proposalStart}–${task.proposalEnd}" } ?: task.statusLine
                            LabelRaw(text = range, style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.Muted), maxLines = 1)
                        }
                        GhostButton(text = "看代码", onClick = {
                            collab.selectTask(task.id)
                            task.proposalPath?.let { state.open(it, revealLine = task.proposalStart) }
                            state.openCodePage()
                        })
                    }
                    Spacer(Modifier.height(8.dp))
                    ProposalUserBrief(task, collab, compact = compact)
                    Spacer(Modifier.height(8.dp))
                    ProposalActions(
                        onAgree = {
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
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(if (compact) 14.dp else 20.dp))
    }
}

@Composable
private fun SquadEvents(collab: CollabState, compact: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Label("协调事件", style = RlType.label(11.sp, RlColors.Faint))
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .wash(RlColors.FieldDeep)
                .padding(if (compact) 12.dp else 16.dp),
        ) {
            if (collab.teamBoard.isEmpty()) {
                LabelRaw(text = "尚无事件", style = RlType.label(12.sp, RlColors.Faint))
            } else {
                collab.teamBoard.take(if (compact) 8 else 12).forEach { ev ->
                    Row(Modifier.fillMaxWidth()) {
                        LabelRaw(text = ev.time, style = RlType.label(11.sp, RlColors.Faint), modifier = Modifier.width(48.dp))
                        BasicText(
                            text = ev.text,
                            style = RlType.mono.copy(
                                fontSize = if (compact) 11.sp else 12.sp,
                                color = when (ev.kind) {
                                    LineKind.WARN -> RlColors.Accent
                                    LineKind.OK -> RlColors.CodeString
                                    LineKind.MUTED -> RlColors.Faint
                                    else -> RlColors.InkSoft
                                },
                            ),
                            maxLines = if (compact) 3 else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun SquadSpawnBar(collab: CollabState, state: IdeState, compact: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Label(if (compact) "开路" else "开路同伴", style = RlType.label(11.sp, RlColors.Faint))
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = collab.draft,
            onValueChange = { collab.draft = it },
            textStyle = RlType.mono.copy(fontSize = if (compact) 14.sp else 13.sp, color = RlColors.InkSoft),
            cursorBrush = SolidColor(RlColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .wash(RlColors.FieldDeep)
                .padding(horizontal = 12.dp, vertical = if (compact) 14.dp else 12.dp),
        )
        Spacer(Modifier.height(10.dp))
        SolidBarButton(
            text = "开路",
            trailing = if (compact) "发布" else "写入任务板",
            enabled = collab.draft.isNotBlank(),
            onClick = {
                collab.openLane()
                state.statusMessage = "小队 · ${collab.phaseLabel}"
            },
        )
    }
}

@Composable
private fun SquadTeammateSession(collab: CollabState, state: IdeState, compact: Boolean = false) {
    val w = collab.detailAgent ?: run {
        collab.closeDetail()
        return
    }
    val task = w.taskId?.let { id -> collab.tasks.firstOrNull { it.id == id } }
    val peers = collab.workers.filter { it.id != w.id }
    val journal = collab.journalOf(w.id).asReversed()

    Column(
        Modifier
            .fillMaxSize()
            .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(text = if (compact) "← 返回" else "← 返回任务板", onClick = { collab.closeDetail() })
            Spacer(Modifier.weight(1f))
            LabelRaw(text = "同伴 ${w.name}", style = RlType.label(if (compact) 15.sp else 16.sp, RlColors.Ink))
        }
        Spacer(Modifier.height(if (compact) 10.dp else 12.dp))
        LabelRaw(
            text = "${liveLabel(collab.liveStatusOf(w))} · ${w.dynamic}",
            style = RlType.label(12.sp, RlColors.Accent),
            maxLines = 2,
        )
        if (task != null) {
            Spacer(Modifier.height(4.dp))
            LabelRaw(text = "任务 · ${task.title}", style = RlType.mono.copy(fontSize = 12.sp, color = RlColors.InkSoft), maxLines = 2)
        }

        Spacer(Modifier.height(16.dp))
        MyTeamBlock(collab, w.id)

        Spacer(Modifier.height(16.dp))
        Label("工作日志", style = RlType.label(11.sp, RlColors.Faint))
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .then(if (compact) Modifier.fillMaxWidth().heightIn(max = 280.dp) else Modifier.weight(1f).fillMaxWidth())
                .wash(RlColors.FieldDeep)
                .padding(if (compact) 12.dp else 14.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (journal.isEmpty()) {
                LabelRaw(text = "还没有日志", style = RlType.label(12.sp, RlColors.Faint))
            } else {
                journal.take(if (compact) 40 else journal.size).forEach { e ->
                    JournalLineWide(e)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Label("继续下达", style = RlType.label(11.sp, RlColors.Faint))
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = collab.assignDraft,
            onValueChange = { collab.assignDraft = it },
            textStyle = RlType.mono.copy(fontSize = 13.sp, color = RlColors.InkSoft),
            cursorBrush = SolidColor(RlColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .wash(RlColors.FieldDeep)
                .padding(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        SolidBarButton(
            text = "派给 ${w.name}",
            trailing = if (task?.status == TaskStatus.WORKING) "改方向" else "新任务",
            enabled = collab.assignDraft.isNotBlank(),
            onClick = {
                collab.assignToAgent(w.id, collab.assignDraft)
                state.statusMessage = "小队 · ${collab.phaseLabel}"
            },
        )

        if (peers.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Label("同伴消息", style = RlType.label(11.sp, RlColors.Faint))
            Spacer(Modifier.height(6.dp))
            Row {
                peers.forEach { peer ->
                    val on = collab.talkTargetId == peer.id
                    LabelRaw(
                        text = peer.name,
                        style = RlType.label(12.sp, if (on) RlColors.AccentDeep else RlColors.Faint),
                        modifier = Modifier
                            .clickableFlat { collab.talkTargetId = peer.id }
                            .then(if (on) Modifier.wash(RlColors.AccentSoft) else Modifier)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            BasicTextField(
                value = collab.talkDraft,
                onValueChange = { collab.talkDraft = it },
                textStyle = RlType.mono.copy(fontSize = 13.sp, color = RlColors.InkSoft),
                cursorBrush = SolidColor(RlColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .wash(RlColors.FieldDeep)
                    .padding(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Spacer(Modifier.weight(1f))
                GhostButton(text = "发送", onClick = {
                    val to = collab.talkTargetId ?: return@GhostButton
                    collab.talkToPeer(w.id, to, collab.talkDraft)
                    state.statusMessage = "小队 · 同伴交谈中"
                })
            }
        }
    }
}

@Composable
private fun MyTeamBlock(collab: CollabState, agentId: String) {
    val mates = collab.teammatesOf(agentId)
    Column(Modifier.fillMaxWidth()) {
        Label("我眼中的团队（${mates.size}）", style = RlType.label(11.sp, RlColors.Faint))
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth().wash(RlColors.AccentSoft).padding(12.dp)) {
            if (mates.isEmpty()) {
                LabelRaw(text = "目前就我一人", style = RlType.label(12.sp, RlColors.Muted))
            } else {
                mates.forEach { (name, duty) ->
                    Row {
                        LabelRaw(text = name, style = RlType.label(12.sp, RlColors.AccentDeep), modifier = Modifier.width(32.dp))
                        BasicText(
                            text = "负责 · $duty",
                            style = RlType.mono.copy(fontSize = 12.sp, color = RlColors.InkSoft),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun JournalLineWide(e: AgentJournalEntry) {
    val prefix = when {
        e.fromPeer != null -> "←${e.fromPeer}"
        e.toPeer != null -> "→${e.toPeer}"
        else -> e.time
    }
    Row(Modifier.fillMaxWidth()) {
        LabelRaw(
            text = prefix,
            style = RlType.label(
                11.sp,
                if (e.fromPeer != null || e.toPeer != null) RlColors.Accent else RlColors.Faint,
            ),
            modifier = Modifier.width(64.dp),
        )
        BasicText(
            text = e.text,
            style = RlType.mono.copy(
                fontSize = 12.sp,
                color = when (e.kind) {
                    LineKind.OK -> RlColors.CodeString
                    LineKind.WARN -> RlColors.Accent
                    LineKind.MUTED -> RlColors.Faint
                    else -> RlColors.InkSoft
                },
            ),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
