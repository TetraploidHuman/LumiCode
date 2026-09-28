package com.lumicode.editor.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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
import com.lumicode.editor.ui.components.TabRow
import com.lumicode.editor.ui.components.clickableFlat
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlDimens
import com.lumicode.editor.ui.theme.RlType
import kotlinx.coroutines.delay

@Composable
fun CollaboratePanel(state: IdeState, modifier: Modifier = Modifier) {
    val collab = state.collab

    LaunchedEffect(collab.tickToken) {
        while (collab.isRunning) {
            delay(560)
            if (!collab.isRunning) break
            collab.tickAll()
            state.statusMessage = "共作 · ${collab.briefing}"
            collab.selectedTask?.takeIf { it.status == TaskStatus.PROPOSAL }?.let { t ->
                t.proposalPath?.let { path ->
                    state.open(path, revealLine = t.proposalStart)
                }
            }
        }
    }

    LaunchedEffect(collab.replyToken) {
        if (collab.replyToken == 0) return@LaunchedEffect
        delay(700)
        collab.deliverPeerReply()
        state.statusMessage = "共作 · ${collab.briefing}"
    }

    Column(
        modifier
            .width(RlDimens.referenceWidth)
            .fillMaxHeight()
            .padding(start = RlDimens.panelGap, top = 16.dp, end = 6.dp),
    ) {
        RightPaneTabs(state)
        Spacer(Modifier.height(12.dp))

        if (collab.detailAgentId != null) {
            AgentDetailPane(collab, state)
        } else {
            SquadHome(collab, state)
        }
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
private fun SquadHome(collab: CollabState, state: IdeState) {
    Column(Modifier.fillMaxSize()) {
        Header(collab, state)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (collab.workers.isEmpty()) {
                EmptyHint(collab, state)
            } else {
                if (collab.awaitingYou.isNotEmpty()) {
                    AwaitingYouStrip(collab, state)
                    Spacer(Modifier.height(14.dp))
                }
                RosterSection(collab, state)
                Spacer(Modifier.height(14.dp))
                SharedTaskBoard(collab)
                Spacer(Modifier.height(14.dp))
                TeamEvents(collab)
                Spacer(Modifier.height(14.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        OpenLaneBar(collab, state)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header(collab: CollabState, state: IdeState) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(on = collab.isRunning || collab.hasProposal)
            HGap(8.dp)
            LabelRaw(text = "Agent Teams", style = RlType.label(13.sp, RlColors.Ink))
            Spacer(Modifier.weight(1f))
            if (collab.workers.isNotEmpty()) {
                GhostButton(text = "全遣散", onClick = {
                    collab.stop()
                    state.statusMessage = "共作 · ${collab.phaseLabel}"
                })
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = collab.briefing,
            style = RlType.mono.copy(fontSize = 12.sp, color = RlColors.Accent),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (collab.rosterLine.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = collab.rosterLine,
                style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.InkSoft),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveDot(on: Boolean) {
    androidx.compose.foundation.layout.Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(12.dp),
    ) {
        if (on) {
            androidx.compose.foundation.layout.Box(Modifier.size(12.dp).wash(RlColors.AccentSoft))
        }
        androidx.compose.foundation.layout.Box(
            Modifier.size(5.dp).wash(if (on) RlColors.Accent else RlColors.Faint),
        )
    }
}

@Composable
private fun EmptyHint(collab: CollabState, state: IdeState) {
    Column(
        Modifier
            .fillMaxWidth()
            .wash(RlColors.FieldDeep)
            .padding(12.dp),
    ) {
        LabelRaw(text = "小队还没 teammate", style = RlType.label(12.sp, RlColors.Ink))
        Spacer(Modifier.height(6.dp))
        LabelRaw(
            text = "对齐 DSH / Claude Teams 面板：Roster + 共享任务板（依赖 / 写入范围 / 就绪）。你是 Lead。",
            style = RlType.label(11.sp, RlColors.Muted),
        )
        Spacer(Modifier.height(10.dp))
        SolidBarButton(
            text = "演示：两人 Teams",
            trailing = "roster · 任务板 · 阻塞",
            onClick = {
                collab.seedDemoPair()
                state.statusMessage = "共作 · ${collab.briefing}"
            },
        )
    }
}

@Composable
private fun AwaitingYouStrip(collab: CollabState, state: IdeState) {
    val list = collab.awaitingYou
    Column(
        Modifier
            .fillMaxWidth()
            .wash(RlColors.AccentSoft)
            .padding(10.dp),
    ) {
        LabelRaw(
            text = "待你拍板（${list.size}）",
            style = RlType.label(11.sp, RlColors.AccentDeep),
        )
        Spacer(Modifier.height(6.dp))
        list.forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    LabelRaw(
                        text = "${task.agentName} · ${task.title}",
                        style = RlType.label(11.sp, RlColors.Ink),
                        maxLines = 1,
                    )
                    val range = task.proposalPath?.let {
                        "$it:${task.proposalStart}–${task.proposalEnd}"
                    } ?: task.statusLine
                    LabelRaw(text = range, style = RlType.mono.copy(fontSize = 10.sp, color = RlColors.Muted), maxLines = 1)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(text = "顺着", onClick = {
                    collab.selectTask(task.id)
                    task.proposalPath?.let { state.open(it, revealLine = task.proposalStart) }
                    collab.goAlong(task.id)
                    state.statusMessage = "共作 · ${collab.phaseLabel}"
                })
                GhostButton(text = "重来", onClick = {
                    collab.tryAgain(task.id)
                    state.statusMessage = "共作 · ${collab.phaseLabel}"
                })
                GhostButton(text = "关掉", onClick = {
                    collab.leaveIt(task.id)
                    state.statusMessage = "共作 · ${collab.phaseLabel}"
                })
                Spacer(Modifier.weight(1f))
                GhostButton(text = "看代码", onClick = {
                    collab.selectTask(task.id)
                    task.proposalPath?.let { state.open(it, revealLine = task.proposalStart) }
                })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TeamEvents(collab: CollabState) {
    Column(Modifier.fillMaxWidth()) {
        Label("协调事件", style = RlType.label(10.5.sp, RlColors.Faint))
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .wash(RlColors.FieldDeep)
                .padding(10.dp),
        ) {
            if (collab.teamBoard.isEmpty()) {
                LabelRaw(text = "尚无事件", style = RlType.label(11.sp, RlColors.Faint))
            } else {
                collab.teamBoard.take(6).forEach { ev ->
                    Row(Modifier.fillMaxWidth()) {
                        LabelRaw(
                            text = ev.time,
                            style = RlType.label(9.5.sp, RlColors.Faint),
                            modifier = Modifier.width(40.dp),
                        )
                        BasicText(
                            text = ev.text,
                            style = RlType.mono.copy(
                                fontSize = 10.5.sp,
                                color = when (ev.kind) {
                                    LineKind.WARN -> RlColors.Accent
                                    LineKind.OK -> RlColors.CodeString
                                    LineKind.MUTED -> RlColors.Faint
                                    else -> RlColors.InkSoft
                                },
                            ),
                            maxLines = 2,
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
private fun RosterSection(collab: CollabState, state: IdeState) {
    val rows = collab.rosterRows()
    Column(Modifier.fillMaxWidth()) {
        Label("成员（${rows.size}）", style = RlType.label(10.5.sp, RlColors.Faint))
        Spacer(Modifier.height(6.dp))
        rows.forEach { row ->
            RosterMemberRow(row, collab, state)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun RosterMemberRow(
    row: RosterRow,
    collab: CollabState,
    state: IdeState,
) {
    val wash = when {
        row.isCurrent -> RlColors.AccentSoft
        row.isLead -> RlColors.FieldDeep
        else -> RlColors.FieldDeep
    }
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (row.isLead) Modifier
                else Modifier.clickableFlat { collab.openDetail(row.id) },
            )
            .wash(wash)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(row.status)
            HGap(8.dp)
            LabelRaw(
                text = row.name,
                style = RlType.label(13.sp, if (row.isLead) RlColors.Ink else RlColors.AccentDeep),
                modifier = Modifier.width(28.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabelRaw(
                        text = row.roleLabel,
                        style = RlType.label(10.sp, RlColors.Faint),
                    )
                    if (row.isCurrent && !row.isLead) {
                        HGap(6.dp)
                        LabelRaw(text = "当前会话", style = RlType.label(10.sp, RlColors.Accent))
                    }
                    if (row.isLead) {
                        HGap(6.dp)
                        LabelRaw(text = "Lead", style = RlType.label(10.sp, RlColors.Accent))
                    }
                }
                BasicText(
                    text = row.description.ifBlank { row.dynamic },
                    style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.InkSoft),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                LabelRaw(
                    text = liveStatusLabel(row.status),
                    style = RlType.label(10.sp, statusColor(row.status)),
                )
                row.modelHint?.let {
                    LabelRaw(text = it, style = RlType.label(9.5.sp, RlColors.Faint))
                }
            }
        }
        if (!row.isLead) {
            Spacer(Modifier.height(6.dp))
            LabelRaw(
                text = row.dynamic,
                style = RlType.label(10.5.sp, RlColors.Muted),
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(text = "打开会话", onClick = { collab.openDetail(row.id) })
                if (collab.workers.size > 1) {
                    GhostButton(text = "交谈", onClick = { collab.openDetail(row.id) })
                }
                Spacer(Modifier.weight(1f))
                GhostButton(text = "遣散", onClick = {
                    collab.dismissAgent(row.id)
                    state.statusMessage = "共作 · ${collab.phaseLabel}"
                })
            }
        }
    }
}

@Composable
private fun StatusDot(status: MemberLiveStatus) {
    val color = statusColor(status)
    androidx.compose.foundation.layout.Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(10.dp),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.size(10.dp).wash(color.copy(alpha = 0.25f)))
        androidx.compose.foundation.layout.Box(Modifier.size(5.dp).wash(color))
    }
}

private fun liveStatusLabel(s: MemberLiveStatus): String = when (s) {
    MemberLiveStatus.RUNNING -> "running"
    MemberLiveStatus.INACTIVE -> "inactive"
    MemberLiveStatus.PROVISIONING -> "provisioning"
    MemberLiveStatus.FAILED -> "failed"
}

private fun statusColor(s: MemberLiveStatus) = when (s) {
    MemberLiveStatus.RUNNING -> RlColors.Accent
    MemberLiveStatus.INACTIVE -> RlColors.Faint
    MemberLiveStatus.PROVISIONING -> RlColors.Muted
    MemberLiveStatus.FAILED -> RlColors.CodeNumber
}

@Composable
private fun SharedTaskBoard(collab: CollabState) {
    val views = collab.boardView()
    Column(Modifier.fillMaxWidth()) {
        Label("共享任务板（${views.size}）", style = RlType.label(10.5.sp, RlColors.Faint))
        Spacer(Modifier.height(6.dp))
        if (views.isEmpty()) {
            LabelRaw(text = "任务板为空", style = RlType.label(11.sp, RlColors.Faint))
        } else {
            views.forEach { v ->
                BoardTaskCard(v)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun BoardTaskCard(view: BoardTaskView) {
    val t = view.task
    val (dot, label) = when (t.status) {
        BoardTaskStatus.PENDING ->
            if (view.ready) RlColors.Faint to "pending · ready"
            else RlColors.Accent to "pending · blocked"
        BoardTaskStatus.IN_PROGRESS ->
            RlColors.Accent to "in_progress"
        BoardTaskStatus.COMPLETED ->
            RlColors.CodeString to "completed"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .wash(RlColors.FieldDeep)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.size(6.dp).wash(dot))
            HGap(8.dp)
            LabelRaw(
                text = t.subject,
                style = RlType.label(12.sp, RlColors.Ink),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            LabelRaw(text = label, style = RlType.label(10.sp, dot))
        }
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = t.description,
            style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.InkSoft),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        LabelRaw(
            text = buildString {
                append("负责人 · ${t.ownerName ?: "未认领"}")
                if (t.blockedBy.isNotEmpty()) append(" · 依赖 ${t.blockedBy.joinToString()}")
                if (t.writeScopes.isNotEmpty()) append(" · 写入 ${t.writeScopes.joinToString()}")
            },
            style = RlType.label(10.sp, RlColors.Faint),
            maxLines = 2,
        )
        if (view.writeScopeWarnings.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            LabelRaw(
                text = view.writeScopeWarnings.joinToString("；"),
                style = RlType.label(10.sp, RlColors.Accent),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun AgentDetailPane(collab: CollabState, state: IdeState) {
    val w = collab.detailAgent ?: run {
        collab.closeDetail()
        return
    }
    val task = w.taskId?.let { id -> collab.tasks.firstOrNull { it.id == id } }
    val peers = collab.workers.filter { it.id != w.id }
    val journal = collab.journalOf(w.id).asReversed()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(text = "← 返回", onClick = { collab.closeDetail() })
            Spacer(Modifier.weight(1f))
            LabelRaw(text = "teammate ${w.name}", style = RlType.label(13.sp, RlColors.Ink))
        }
        Spacer(Modifier.height(10.dp))
        LabelRaw(
            text = "${liveStatusLabel(collab.liveStatusOf(w))} · ${w.dynamic}",
            style = RlType.label(11.sp, RlColors.Accent),
            maxLines = 2,
        )
        if (task != null) {
            Spacer(Modifier.height(4.dp))
            LabelRaw(
                text = "任务 · ${task.title}",
                style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.InkSoft),
                maxLines = 2,
            )
        }

        Spacer(Modifier.height(12.dp))
        MyTeamView(collab, w.id)

        Spacer(Modifier.height(12.dp))
        Label("详细工作日志", style = RlType.label(10.5.sp, RlColors.Faint))
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .wash(RlColors.FieldDeep)
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (journal.isEmpty()) {
                LabelRaw(text = "还没有日志", style = RlType.label(11.sp, RlColors.Faint))
            } else {
                journal.forEach { e ->
                    JournalLine(e)
                    Spacer(Modifier.height(5.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Label("继续下达任务", style = RlType.label(10.5.sp, RlColors.Faint))
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = collab.assignDraft,
            onValueChange = { collab.assignDraft = it },
            textStyle = RlType.mono.copy(fontSize = 11.5.sp, color = RlColors.InkSoft),
            cursorBrush = SolidColor(RlColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .wash(RlColors.FieldDeep)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(6.dp))
        SolidBarButton(
            text = "派给 ${w.name}",
            trailing = if (task?.status == TaskStatus.WORKING) "改方向重跑" else "新任务",
            enabled = collab.assignDraft.isNotBlank(),
            onClick = {
                collab.assignToAgent(w.id, collab.assignDraft)
                state.statusMessage = "共作 · ${collab.phaseLabel}"
            },
        )

        if (peers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Label("与同伴交流", style = RlType.label(10.5.sp, RlColors.Faint))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                peers.forEach { peer ->
                    val on = collab.talkTargetId == peer.id
                    LabelRaw(
                        text = peer.name,
                        style = RlType.label(11.sp, if (on) RlColors.AccentDeep else RlColors.Faint),
                        modifier = Modifier
                            .clickableFlat { collab.talkTargetId = peer.id }
                            .then(if (on) Modifier.wash(RlColors.AccentSoft) else Modifier)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                collab.talkPresets(w.id).forEach { preset ->
                    LabelRaw(
                        text = preset.take(8) + if (preset.length > 8) "…" else "",
                        style = RlType.label(10.sp, RlColors.Muted),
                        modifier = Modifier
                            .clickableFlat { collab.talkDraft = preset }
                            .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicTextField(
                value = collab.talkDraft,
                onValueChange = { collab.talkDraft = it },
                textStyle = RlType.mono.copy(fontSize = 11.5.sp, color = RlColors.InkSoft),
                cursorBrush = SolidColor(RlColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .wash(RlColors.FieldDeep)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Spacer(Modifier.weight(1f))
                GhostButton(
                    text = "发送给同伴",
                    onClick = {
                        val to = collab.talkTargetId ?: return@GhostButton
                        collab.talkToPeer(w.id, to, collab.talkDraft)
                        state.statusMessage = "共作 · 同伴交谈中"
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MyTeamView(collab: CollabState, agentId: String) {
    val mates = collab.teammatesOf(agentId)
    Column(Modifier.fillMaxWidth()) {
        Label(
            "我眼中的团队（${mates.size} 名同事）",
            style = RlType.label(10.5.sp, RlColors.Faint),
        )
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .wash(RlColors.AccentSoft)
                .padding(8.dp),
        ) {
            if (mates.isEmpty()) {
                LabelRaw(
                    text = "目前就我一人 · 再开一路后会自动认人",
                    style = RlType.label(11.sp, RlColors.Muted),
                )
            } else {
                mates.forEach { (name, duty) ->
                    Row(Modifier.fillMaxWidth()) {
                        LabelRaw(
                            text = name,
                            style = RlType.label(11.sp, RlColors.AccentDeep),
                            modifier = Modifier.width(28.dp),
                        )
                        BasicText(
                            text = "负责 · $duty",
                            style = RlType.mono.copy(fontSize = 11.sp, color = RlColors.InkSoft),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                }
            }
        }
    }
}

@Composable
private fun JournalLine(e: AgentJournalEntry) {
    val prefix = when {
        e.fromPeer != null -> "←${e.fromPeer}"
        e.toPeer != null -> "→${e.toPeer}"
        else -> e.time
    }
    Row(Modifier.fillMaxWidth()) {
        LabelRaw(
            text = prefix,
            style = RlType.label(
                9.5.sp,
                when {
                    e.fromPeer != null || e.toPeer != null -> RlColors.Accent
                    else -> RlColors.Faint
                },
            ),
            modifier = Modifier.width(52.dp),
        )
        BasicText(
            text = e.text,
            style = RlType.mono.copy(
                fontSize = 11.sp,
                color = when (e.kind) {
                    LineKind.OK -> RlColors.CodeString
                    LineKind.WARN -> RlColors.Accent
                    LineKind.MUTED -> RlColors.Faint
                    else -> RlColors.InkSoft
                },
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OpenLaneBar(collab: CollabState, state: IdeState) {
    Column(Modifier.fillMaxWidth()) {
        LabelRaw(
            text = if (collab.workers.isEmpty()) "spawn 第一名 teammate" else "再 spawn 一路（新上下文）",
            style = RlType.label(10.5.sp, RlColors.Faint),
        )
        Spacer(Modifier.height(4.dp))
        if (collab.workers.size == 1) {
            Row {
                LabelRaw(
                    text = "建议：抽 SessionStore",
                    style = RlType.label(10.sp, RlColors.Muted),
                    modifier = Modifier
                        .clickableFlat {
                            collab.draft = "把会话状态抽到独立 SessionStore"
                        }
                        .padding(end = 10.dp, bottom = 4.dp),
                )
                LabelRaw(
                    text = "建议：补单测",
                    style = RlType.label(10.sp, RlColors.Muted),
                    modifier = Modifier
                        .clickableFlat {
                            collab.draft = "为登录提示补一则 UI 单测"
                        }
                        .padding(bottom = 4.dp),
                )
            }
        }
        BasicTextField(
            value = collab.draft,
            onValueChange = { collab.draft = it },
            textStyle = RlType.mono.copy(fontSize = 12.sp, color = RlColors.InkSoft),
            cursorBrush = SolidColor(RlColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .wash(RlColors.FieldDeep)
                .padding(horizontal = 10.dp, vertical = 9.dp),
        )
        Spacer(Modifier.height(8.dp))
        SolidBarButton(
            text = if (collab.workers.isEmpty()) "spawn teammate" else "再 spawn",
            trailing = "写入任务板",
            enabled = collab.draft.isNotBlank(),
            onClick = {
                collab.openLane()
                state.statusMessage = "共作 · ${collab.phaseLabel}"
            },
        )
    }
}
