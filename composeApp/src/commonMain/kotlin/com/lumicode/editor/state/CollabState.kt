package com.lumicode.editor.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.lumicode.editor.platform.clockLabel

enum class TaskStatus {
    WORKING,
    PROPOSAL,
    DONE,
    STOPPED,
}

/** 对齐 DSH TeamMemberPhase */
enum class MemberPhase {
    PROVISIONING,
    ACTIVE,
    FAILED,
}

/** 对齐 DSH 运行态（由 phase + 是否在跑推导） */
enum class MemberLiveStatus {
    RUNNING,
    INACTIVE,
    PROVISIONING,
    FAILED,
}

/** 对齐 DSH TeamTaskStatus（共享任务板；不含 deleted 墓碑） */
enum class BoardTaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
}

data class AgentWorker(
    val id: String,
    val name: String,
    val taskId: String?,
    /** 卡片上的当前动态（一行） */
    val dynamic: String,
    /** 职责说明（spawn description） */
    val description: String = "",
    val phase: MemberPhase = MemberPhase.ACTIVE,
    val modelHint: String? = "demo",
)

data class CollabTask(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val agentId: String,
    val agentName: String,
    val beat: Int = 0,
    val statusLine: String = "",
    val proposalPath: String? = null,
    val proposalStart: Int = 0,
    val proposalEnd: Int = 0,
    val proposalSummary: String? = null,
    val proposalNote: String? = null,
    /** 对应共享任务板上的条目 */
    val boardTaskId: String? = null,
)

/**
 * 对齐 DSH `TeamTaskView`：共享任务板一行。
 * ready / 重叠警告在 [CollabState.boardView] 里计算。
 */
data class BoardTask(
    val id: String,
    val subject: String,
    val description: String,
    val status: BoardTaskStatus,
    val ownerName: String? = null,
    val blockedBy: List<String> = emptyList(),
    val writeScopes: List<String> = emptyList(),
)

data class BoardTaskView(
    val task: BoardTask,
    val ready: Boolean,
    val writeScopeWarnings: List<String>,
)

/** Roster 一行：含合成 Lead「你」 */
data class RosterRow(
    val id: String,
    val name: String,
    val roleLabel: String,
    val status: MemberLiveStatus,
    val description: String,
    val modelHint: String?,
    val dynamic: String,
    val isLead: Boolean,
    val isCurrent: Boolean,
)

/** 单条工作日志 / 同伴消息 */
data class AgentJournalEntry(
    val time: String,
    val text: String,
    val kind: LineKind = LineKind.INFO,
    /** 来自另一 Agent 时填写对方名字 */
    val fromPeer: String? = null,
    /** 发给另一 Agent 时填写 */
    val toPeer: String? = null,
)

data class AgentActivity(
    val time: String,
    val agentName: String?,
    val text: String,
    val kind: LineKind = LineKind.INFO,
)

/** 团队白板上的一条协调事件（全员可见）。 */
data class TeamEvent(
    val time: String,
    val text: String,
    val kind: LineKind = LineKind.INFO,
)

data class AgentLogEntry(
    val time: String,
    val text: String,
    val kind: LineKind,
)

/**
 * 按需开路 + 单 Agent 深潜：动态 / 日志 / 续派 / 同伴交流。
 */
class CollabState {

    var rightTab by mutableStateOf(1)
    var constraint by mutableStateOf("不改构建与依赖")
    var draft by mutableStateOf("启动流程补「会话已授权」说明")

    val workers = mutableStateListOf<AgentWorker>()
    val tasks = mutableStateListOf<CollabTask>()
    /** 共享任务板（对齐 DSH agentTeam.tasks） */
    val boardTasks = mutableStateListOf<BoardTask>()
    val activity = mutableStateListOf<AgentActivity>()
    val teamBoard = mutableStateListOf<TeamEvent>()
    val agentLog = mutableStateListOf<AgentLogEntry>()

    /** agentId → 详细工作日志 */
    private val journals = mutableStateMapOf<String, SnapshotStateList<AgentJournalEntry>>()

    var selectedTaskId by mutableStateOf<String?>(null)
    /** 打开某 Agent 的详细面板（对齐「导航到 teammate 会话」） */
    var detailAgentId by mutableStateOf<String?>(null)
    var assignDraft by mutableStateOf("")
    var talkDraft by mutableStateOf("")
    var talkTargetId by mutableStateOf<String?>(null)

    var tickToken by mutableStateOf(0)
        private set
    var replyToken by mutableStateOf(0)
        private set
    var pendingReplyAgentId by mutableStateOf<String?>(null)
        private set
    var pendingReplyFromName by mutableStateOf<String?>(null)
        private set
    var pendingReplyText by mutableStateOf<String?>(null)
        private set

    private var agentSeq = 0
    private var taskSeq = 0
    private var boardSeq = 0

    val phaseLabel: String
        get() {
            val w = tasks.count { it.status == TaskStatus.WORKING }
            val p = tasks.count { it.status == TaskStatus.PROPOSAL }
            return when {
                w > 0 -> "$w 路在跑"
                p > 0 -> "$p 个提案"
                workers.isEmpty() -> "尚未开路"
                else -> "${workers.size} 名 teammate"
            }
        }

    val briefing: String
        get() = when {
            workers.isEmpty() -> "Agent Teams 演示 · 你是 Lead · 按需 spawn teammate"
            else -> {
                val w = tasks.count { it.status == TaskStatus.WORKING }
                val p = tasks.count { it.status == TaskStatus.PROPOSAL }
                "成员 ${workers.size + 1} · 任务板 ${boardTasks.size} · 在跑 $w · 待你定 $p"
            }
        }

    val rosterLine: String
        get() {
            if (workers.isEmpty()) return ""
            return workers.joinToString(" · ") { w ->
                val duty = w.taskId?.let { tid -> tasks.firstOrNull { it.id == tid }?.title }
                    ?: "空闲"
                val short = if (duty.length <= 10) duty else duty.take(10) + "…"
                "${w.name}〔$short〕"
            }
        }

    val awaitingYou: List<CollabTask>
        get() = tasks.filter { it.status == TaskStatus.PROPOSAL }

    val isRunning: Boolean
        get() = tasks.any { it.status == TaskStatus.WORKING }

    val hasProposal: Boolean
        get() = tasks.any { it.status == TaskStatus.PROPOSAL }

    val selectedTask: CollabTask?
        get() = selectedTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
            ?: tasks.firstOrNull { it.status == TaskStatus.PROPOSAL }
            ?: tasks.firstOrNull { it.status == TaskStatus.WORKING }

    val detailAgent: AgentWorker?
        get() = detailAgentId?.let { id -> workers.firstOrNull { it.id == id } }

    /** 对齐 DSH 面板：Lead「你」+ teammates */
    fun rosterRows(): List<RosterRow> {
        val lead = RosterRow(
            id = "lead",
            name = "你",
            roleLabel = "lead",
            status = MemberLiveStatus.INACTIVE,
            description = "上级 · 派活与拍板",
            modelHint = null,
            dynamic = if (workers.isEmpty()) "待开路" else "指挥中",
            isLead = true,
            isCurrent = detailAgentId == null,
        )
        if (workers.isEmpty()) return listOf(lead)
        val mates = workers.map { w ->
            RosterRow(
                id = w.id,
                name = w.name,
                roleLabel = "teammate",
                status = liveStatusOf(w),
                description = w.description.ifBlank {
                    w.taskId?.let { tid -> tasks.firstOrNull { it.id == tid }?.title }.orEmpty()
                },
                modelHint = w.modelHint,
                dynamic = w.dynamic,
                isLead = false,
                isCurrent = detailAgentId == w.id,
            )
        }
        return listOf(lead) + mates
    }

    fun liveStatusOf(w: AgentWorker): MemberLiveStatus = when (w.phase) {
        MemberPhase.PROVISIONING -> MemberLiveStatus.PROVISIONING
        MemberPhase.FAILED -> MemberLiveStatus.FAILED
        MemberPhase.ACTIVE -> {
            val t = w.taskId?.let { id -> tasks.firstOrNull { it.id == id } }
            if (t?.status == TaskStatus.WORKING) MemberLiveStatus.RUNNING
            else MemberLiveStatus.INACTIVE
        }
    }

    /** 对齐 DSH 只读任务板视图 */
    fun boardView(): List<BoardTaskView> {
        val completed = boardTasks.filter { it.status == BoardTaskStatus.COMPLETED }.map { it.id }.toSet()
        return boardTasks.map { t ->
            val ready = t.status == BoardTaskStatus.PENDING &&
                t.blockedBy.all { it in completed }
            val warnings = mutableListOf<String>()
            if (t.status == BoardTaskStatus.IN_PROGRESS || t.status == BoardTaskStatus.PENDING) {
                boardTasks.filter {
                    it.id != t.id &&
                        it.status == BoardTaskStatus.IN_PROGRESS &&
                        it.writeScopes.any { s -> s in t.writeScopes }
                }.forEach { other ->
                    warnings.add("与 ${other.ownerName ?: other.subject} 写入范围重叠")
                }
            }
            BoardTaskView(t, ready, warnings)
        }
    }

    fun pendingReviewLines(path: String): Set<Int> {
        val lines = mutableSetOf<Int>()
        tasks.filter { it.status == TaskStatus.PROPOSAL && it.proposalPath == path }.forEach { t ->
            for (i in t.proposalStart..t.proposalEnd) lines.add(i)
        }
        return lines
    }

    fun journalOf(agentId: String): List<AgentJournalEntry> =
        journals[agentId]?.toList().orEmpty()

    fun latestDynamics(agentId: String, n: Int = 2): List<String> =
        journalOf(agentId).asReversed().take(n).map { e ->
            when {
                e.fromPeer != null -> "←${e.fromPeer} ${e.text}"
                e.toPeer != null -> "→${e.toPeer} ${e.text}"
                else -> e.text
            }
        }

    fun appendAgent(text: String, kind: LineKind = LineKind.INFO) {
        agentLog.add(AgentLogEntry(clockLabel(), text, kind))
        while (agentLog.size > 120) agentLog.removeAt(0)
    }

    private fun ensureJournal(agentId: String): SnapshotStateList<AgentJournalEntry> {
        return journals.getOrPut(agentId) { mutableStateListOf() }
    }

    private fun journal(
        agentId: String,
        text: String,
        kind: LineKind = LineKind.INFO,
        fromPeer: String? = null,
        toPeer: String? = null,
    ) {
        val list = ensureJournal(agentId)
        list.add(AgentJournalEntry(clockLabel(), text, kind, fromPeer, toPeer))
        while (list.size > 80) list.removeAt(0)
    }

    private fun push(agent: String?, text: String, kind: LineKind = LineKind.INFO) {
        activity.add(0, AgentActivity(clockLabel(), agent, text, kind))
        while (activity.size > 40) activity.removeAt(activity.lastIndex)
        appendAgent(if (agent != null) "[$agent] $text" else "[项目] $text", kind)
    }

    private fun teamNote(text: String, kind: LineKind = LineKind.INFO) {
        teamBoard.add(0, TeamEvent(clockLabel(), text, kind))
        while (teamBoard.size > 30) teamBoard.removeAt(teamBoard.lastIndex)
        push(null, text, kind)
    }

    /** 某 Agent 眼中的同事清单（不含自己）。 */
    fun teammatesOf(agentId: String): List<Pair<String, String>> {
        return workers.filter { it.id != agentId }.map { w ->
            val duty = w.taskId?.let { tid ->
                tasks.firstOrNull { it.id == tid }?.title
            } ?: "空闲"
            w.name to duty
        }
    }

    /** 写入「团队简报」到某 Agent 日志。 */
    private fun briefTeamTo(agentId: String, reason: String) {
        val others = teammatesOf(agentId)
        if (others.isEmpty()) {
            journal(agentId, "$reason · 目前就我一人，暂无同事可协调", LineKind.MUTED)
            return
        }
        journal(
            agentId,
            "$reason · 同事 ${others.size} 人：${
                others.joinToString("；") { (n, d) ->
                    val short = if (d.length <= 10) d else d.take(10) + "…"
                    "$n〔$short〕"
                }
            }",
            LineKind.INFO,
        )
    }

    /** 全员刷新对团队的认知。 */
    private fun broadcastRoster(trigger: String) {
        val snapshot = workers.map { w ->
            val duty = w.taskId?.let { tid -> tasks.firstOrNull { it.id == tid }?.title } ?: "空闲"
            "${w.name}=${if (duty.length <= 8) duty else duty.take(8) + "…"}"
        }.joinToString(" · ")
        teamNote("团队看板 · $trigger · $snapshot", LineKind.MUTED)
        workers.forEach { w ->
            briefTeamTo(w.id, "同步团队")
        }
    }

    /**
     * 简单协调：若多人可能动同一文件区，提出对齐。
     * 演示里提案都落 Main.kt，故第二路起会触发协调。
     */
    private fun coordinateIfNeeded(agentId: String, agentName: String, title: String) {
        val peers = workers.filter { it.id != agentId && it.taskId != null }
        if (peers.isEmpty()) return
        val peerNames = peers.joinToString("、") { it.name }
        val note = "协调：我和 $peerNames 可能都动到 src/Main.kt，先对齐接口边界，避免互相覆盖"
        journal(agentId, note, LineKind.WARN)
        setWorker(agentId, workers.first { it.id == agentId }.taskId, "协调中 · 对齐边界")
        peers.forEach { peer ->
            journal(peer.id, "$agentName 提议对齐 Main.kt 边界（其任务：「${title.take(12)}」）", LineKind.WARN, fromPeer = agentName)
            journal(agentId, "已知会 ${peer.name}", LineKind.MUTED, toPeer = peer.name)
        }
        teamNote("$agentName 发起协调 · 与 $peerNames 对齐 Main.kt", LineKind.WARN)

        // 安排一名同伴回复协调
        val responder = peers.first()
        pendingReplyAgentId = responder.id
        pendingReplyFromName = agentName
        pendingReplyText = "协调对齐 Main.kt 边界"
        replyToken++
    }

    private fun setWorker(
        id: String,
        taskId: String?,
        dynamic: String,
        phase: MemberPhase? = null,
        description: String? = null,
    ) {
        val i = workers.indexOfFirst { it.id == id }
        if (i < 0) return
        val cur = workers[i]
        workers[i] = cur.copy(
            taskId = taskId,
            dynamic = dynamic,
            phase = phase ?: cur.phase,
            description = description ?: cur.description,
        )
    }

    private fun updateBoard(id: String, transform: (BoardTask) -> BoardTask) {
        val i = boardTasks.indexOfFirst { it.id == id }
        if (i < 0) return
        boardTasks[i] = transform(boardTasks[i])
    }

    private fun addBoardTask(
        subject: String,
        description: String,
        ownerName: String?,
        status: BoardTaskStatus,
        blockedBy: List<String> = emptyList(),
        writeScopes: List<String> = listOf("src/Main.kt"),
    ): BoardTask {
        boardSeq++
        val t = BoardTask(
            id = "b$boardSeq",
            subject = subject,
            description = description,
            status = status,
            ownerName = ownerName,
            blockedBy = blockedBy,
            writeScopes = writeScopes,
        )
        boardTasks.add(0, t)
        return t
    }

    private fun updateTask(id: String, transform: (CollabTask) -> CollabTask) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i < 0) return
        tasks[i] = transform(tasks[i])
    }

    private fun nextAgentName(): String {
        agentSeq++
        val names = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛")
        return names.getOrElse(agentSeq - 1) { "Agent$agentSeq" }
    }

    fun openDetail(agentId: String) {
        detailAgentId = agentId
        assignDraft = ""
        talkDraft = ""
        talkTargetId = workers.firstOrNull { it.id != agentId }?.id
    }

    fun closeDetail() {
        detailAgentId = null
        assignDraft = ""
        talkDraft = ""
        talkTargetId = null
    }

    /** 新开一路：spawn teammate + 任务板上认领一条 in_progress。 */
    fun openLane(title: String = draft) {
        val q = title.trim()
        if (q.isEmpty()) return

        agentSeq = maxOf(agentSeq, workers.size)
        val name = nextAgentName()
        val agentId = "a$agentSeq"
        taskSeq++
        val taskId = "t$taskSeq"
        val short = if (q.length <= 14) q else q.take(14) + "…"

        val board = addBoardTask(
            subject = short,
            description = q,
            ownerName = name,
            status = BoardTaskStatus.IN_PROGRESS,
        )

        workers.add(
            0,
            AgentWorker(
                id = agentId,
                name = name,
                taskId = taskId,
                dynamic = "provisioning · 接单",
                description = q,
                phase = MemberPhase.PROVISIONING,
                modelHint = "demo",
            ),
        )
        ensureJournal(agentId)
        journal(agentId, "spawn · fresh 上下文 · 任务「$q」", LineKind.INFO)
        journal(agentId, "约束 · $constraint", LineKind.MUTED)
        journal(agentId, "任务板认领 ${board.id} · writeScopes ${board.writeScopes.joinToString()}", LineKind.MUTED)

        tasks.add(
            0,
            CollabTask(
                id = taskId,
                title = q,
                status = TaskStatus.WORKING,
                agentId = agentId,
                agentName = name,
                beat = 0,
                statusLine = "刚接单",
                boardTaskId = board.id,
            ),
        )

        briefTeamTo(agentId, "入场认人")
        val seniors = workers.filter { it.id != agentId }
        if (seniors.isEmpty()) {
            teamNote("$name 入场 · Lead 下第一名 teammate", LineKind.INFO)
        } else {
            teamNote(
                "$name 入场 · 「${if (q.length <= 12) q else q.take(12) + "…"}」· teammate ${workers.size}",
                LineKind.INFO,
            )
            seniors.forEach { s ->
                journal(
                    s.id,
                    "新同事 $name 入场，负责「${if (q.length <= 14) q else q.take(14) + "…"}」",
                    LineKind.INFO,
                )
                briefTeamTo(s.id, "同事变动")
            }
            seniors.forEach { s ->
                journal(agentId, "我是 $name，来做「$short」，请多关照", LineKind.MUTED, toPeer = s.name)
                journal(s.id, "我是 $name，来做「$short」，请多关照", LineKind.MUTED, fromPeer = name)
            }
        }

        selectedTaskId = taskId
        draft = ""
        rightTab = 1
        push(name, "spawn teammate · $q", LineKind.INFO)
        tickToken++
    }

    fun dispatch(title: String = draft) = openLane(title)
    fun go(direction: String = draft) = openLane(direction.ifBlank { draft })

    fun redirect(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        openLane(q)
    }

    /**
     * 给已有 Agent 继续派活（不新建 Agent）。
     * 若它正忙：记下续派并改当前任务方向重跑；若空闲：新开任务绑回它。
     */
    fun assignToAgent(agentId: String, title: String) {
        val q = title.trim()
        if (q.isEmpty()) return
        val w = workers.firstOrNull { it.id == agentId } ?: return
        val short = if (q.length <= 14) q else q.take(14) + "…"

        val current = w.taskId?.let { tid -> tasks.firstOrNull { it.id == tid } }
        if (current != null &&
            (current.status == TaskStatus.WORKING || current.status == TaskStatus.PROPOSAL)
        ) {
            journal(agentId, "上级续派 · 改做「$q」", LineKind.WARN)
            push(w.name, "续派 · $q", LineKind.WARN)
            updateTask(current.id) {
                it.copy(
                    title = q,
                    status = TaskStatus.WORKING,
                    beat = 0,
                    statusLine = "续派重跑",
                    proposalPath = null,
                    proposalSummary = null,
                    proposalNote = null,
                )
            }
            current.boardTaskId?.let { bid ->
                updateBoard(bid) {
                    it.copy(
                        subject = short,
                        description = q,
                        status = BoardTaskStatus.IN_PROGRESS,
                        ownerName = w.name,
                    )
                }
            }
            setWorker(agentId, current.id, "续派 · $short")
            selectedTaskId = current.id
            briefTeamTo(agentId, "续派后复盘团队")
            coordinateIfNeeded(agentId, w.name, q)
        } else {
            taskSeq++
            val taskId = "t$taskSeq"
            journal(agentId, "上级新派 · 「$q」", LineKind.INFO)
            push(w.name, "接新活 · $q", LineKind.INFO)
            tasks.add(
                0,
                CollabTask(
                    id = taskId,
                    title = q,
                    status = TaskStatus.WORKING,
                    agentId = agentId,
                    agentName = w.name,
                    beat = 0,
                    statusLine = "新派接单",
                    boardTaskId = addBoardTask(
                        subject = short,
                        description = q,
                        ownerName = w.name,
                        status = BoardTaskStatus.IN_PROGRESS,
                    ).id,
                ),
            )
            setWorker(agentId, taskId, "接单 · $short")
            selectedTaskId = taskId
            briefTeamTo(agentId, "接活前认人")
            broadcastRoster("${w.name} 改持新活")
            coordinateIfNeeded(agentId, w.name, q)
        }
        assignDraft = ""
        tickToken++
    }

    /** Agent 之间交流：写入双方日志，并安排对方延迟回复。 */
    fun talkToPeer(fromId: String, toId: String, text: String) {
        val msg = text.trim()
        if (msg.isEmpty()) return
        if (fromId == toId) return
        val from = workers.firstOrNull { it.id == fromId } ?: return
        val to = workers.firstOrNull { it.id == toId } ?: return

        journal(fromId, msg, LineKind.INFO, toPeer = to.name)
        journal(toId, msg, LineKind.INFO, fromPeer = from.name)
        setWorker(fromId, from.taskId, "刚问 ${to.name}")
        setWorker(toId, to.taskId, "收到 ${from.name}")
        push(from.name, "→${to.name}：$msg", LineKind.MUTED)

        pendingReplyAgentId = toId
        pendingReplyFromName = from.name
        pendingReplyText = msg
        replyToken++
        talkDraft = ""
    }

    /** UI 延迟后调用：同伴回复。 */
    fun deliverPeerReply() {
        val toId = pendingReplyAgentId ?: return
        val fromName = pendingReplyFromName ?: return
        val asked = pendingReplyText ?: return
        val to = workers.firstOrNull { it.id == toId } ?: return
        val reply = when {
            asked.contains("协调") || asked.contains("对齐") || asked.contains("边界") ->
                "同意对齐。我先避开你的行段，改完在白板上同步"
            asked.contains("文件") || asked.contains("路径") || asked.contains("重叠") ->
                "我这边也动 Main.kt；我们分块：你改上半，我守下半，冲突再喊"
            asked.contains("一起") || asked.contains("帮忙") ->
                "可以，我忙完当前刀就接你这段"
            else ->
                "收到。我看了团队分工，继续我的「${
                    to.taskId?.let { tid -> tasks.firstOrNull { it.id == tid }?.title }?.take(10) ?: to.dynamic
                }」，有依赖再同步"
        }
        journal(toId, reply, LineKind.OK, toPeer = fromName)
        workers.firstOrNull { it.name == fromName }?.id?.let { fromId ->
            journal(fromId, reply, LineKind.OK, fromPeer = to.name)
        }
        setWorker(toId, to.taskId, "已回 ${fromName}")
        push(to.name, "→$fromName：$reply", LineKind.MUTED)
        if (to.taskId != null) {
            journal(toId, "把同伴意见记入本路备注", LineKind.MUTED)
        }
        pendingReplyAgentId = null
        pendingReplyFromName = null
        pendingReplyText = null
    }

    fun selectTask(id: String) {
        selectedTaskId = id
    }

    fun tickAll() {
        tasks.filter { it.status == TaskStatus.WORKING }.map { it.id }.forEach { advanceTask(it) }
    }

    private fun advanceTask(taskId: String) {
        val task = tasks.firstOrNull { it.id == taskId } ?: return
        if (task.status != TaskStatus.WORKING) return
        val beat = task.beat + 1
        val agentId = task.agentId
        val agent = task.agentName
        val short = task.title.let { if (it.length <= 12) it else it.take(12) + "…" }
        when (beat) {
            1 -> {
                updateTask(taskId) { it.copy(beat = beat, statusLine = "摸相关文件…") }
                setWorker(agentId, taskId, "摸清中 · 扫调用链", phase = MemberPhase.ACTIVE)
                journal(agentId, "打开相关文件，梳理调用", LineKind.MUTED)
                briefTeamTo(agentId, "动手前认人")
                push(agent, "摸相关文件", LineKind.MUTED)
                coordinateIfNeeded(agentId, agent, task.title)
            }
            2 -> {
                updateTask(taskId) { it.copy(beat = beat, statusLine = "起草中…") }
                setWorker(agentId, taskId, "起草中 · 写草案")
                journal(agentId, "开始起草改动草案", LineKind.INFO)
                val mates = teammatesOf(agentId)
                if (mates.isNotEmpty()) {
                    journal(
                        agentId,
                        "起草时留意同事：${mates.joinToString("、") { it.first }}",
                        LineKind.MUTED,
                    )
                }
                push(agent, "开始起草", LineKind.INFO)
            }
            else -> {
                // 按在场顺序错开行段，模拟协调后的分块（甲上半 / 乙下半…）
                val slot = workers.indexOfFirst { it.id == agentId }.coerceAtLeast(0)
                val base = 12 + slot * 8
                val note = if (workers.size > 1) {
                    "演示未写盘 · 已与同伴错开 Main.kt L$base–${base + 5}"
                } else {
                    "演示未写盘"
                }
                updateTask(taskId) {
                    it.copy(
                        beat = beat,
                        status = TaskStatus.PROPOSAL,
                        statusLine = "提案已放上桌 · 等你定",
                        proposalPath = "src/Main.kt",
                        proposalStart = base,
                        proposalEnd = base + 5,
                        proposalSummary = "针对「${it.title}」的草案",
                        proposalNote = note,
                    )
                }
                setWorker(agentId, taskId, "等你定 · 提案在桌")
                journal(agentId, "提案上桌 · Main.kt:$base–${base + 5} · 请上级拍板", LineKind.WARN)
                teamNote("$agent 提案就绪 · Main.kt:$base–${base + 5} · 等上级", LineKind.WARN)
                teammatesOf(agentId).forEach { (peerName, _) ->
                    val peerId = workers.firstOrNull { it.name == peerName }?.id ?: return@forEach
                    journal(
                        peerId,
                        "$agent 已交提案（L$base–${base + 5}），我守自己的块",
                        LineKind.MUTED,
                        fromPeer = agent,
                    )
                }
                push(agent, "提案上桌 · Main.kt:$base–${base + 5}", LineKind.WARN)
                if (selectedTaskId == null || selectedTaskId == taskId) selectedTaskId = taskId
            }
        }
    }

    fun goAlong(taskId: String? = selectedTaskId) {
        val id = taskId ?: return
        val task = tasks.firstOrNull { it.id == id } ?: return
        if (task.status != TaskStatus.PROPOSAL) return
        push(task.agentName, "上级顺着 · 本路收束（演示未写盘）", LineKind.OK)
        journal(task.agentId, "上级顺着 · 本刀收束", LineKind.OK)
        teamNote("上级批准 ${task.agentName} · 「${task.title.take(14)}」", LineKind.OK)
        updateTask(id) { it.copy(status = TaskStatus.DONE, statusLine = "已收") }
        task.boardTaskId?.let { bid ->
            updateBoard(bid) { it.copy(status = BoardTaskStatus.COMPLETED) }
            // 解锁依赖本任务的 pending
            boardTasks.filter { bid in it.blockedBy && it.status == BoardTaskStatus.PENDING }
                .forEach { blocked ->
                    teamNote("任务板解锁 · ${blocked.subject} 已就绪", LineKind.INFO)
                }
        }
        setWorker(task.agentId, null, "空闲 · 可再派活")
        journal(task.agentId, "空闲，等上级下一句", LineKind.MUTED)
        teammatesOf(task.agentId).forEach { (peerName, _) ->
            val peerId = workers.firstOrNull { it.name == peerName }?.id ?: return@forEach
            journal(peerId, "上级已批准 ${task.agentName}，我继续本职", LineKind.MUTED)
        }
        selectedTaskId = tasks.firstOrNull {
            it.status == TaskStatus.PROPOSAL || it.status == TaskStatus.WORKING
        }?.id
    }

    /**
     * 演示：两人 teammate + 任务板依赖（对齐 Claude/DSH：认领、阻塞、重叠写入提示）。
     */
    fun seedDemoPair() {
        if (workers.isNotEmpty()) stop()
        agentSeq = 0
        taskSeq = 0
        boardSeq = 0
        boardTasks.clear()

        openLane("登录页补「会话已授权」提示文案")
        val firstBoardId = boardTasks.firstOrNull()?.id

        openLane("把会话状态抽到独立 SessionStore")

        // 第三条：被前两条阻塞的联调（只在板上，等人批完前两条才 ready）
        addBoardTask(
            subject = "联调自检",
            description = "登录提示与 SessionStore 都就绪后跑一遍冒烟",
            ownerName = null,
            status = BoardTaskStatus.PENDING,
            blockedBy = boardTasks.filter { it.status == BoardTaskStatus.IN_PROGRESS }.map { it.id },
            writeScopes = listOf("src/Main.kt", "test/"),
        )

        teamNote(
            "演示小队 · Lead=你 · teammate=${workers.size} · 任务板=${boardTasks.size}" +
                (firstBoardId?.let { " · 首条 $it" } ?: ""),
            LineKind.INFO,
        )
        rightTab = 1
    }

    /** 详情页快捷话术，方便演示同伴协调。 */
    fun talkPresets(fromId: String): List<String> {
        val peers = teammatesOf(fromId)
        if (peers.isEmpty()) return emptyList()
        val other = peers.first().first
        return listOf(
            "我们对齐一下 Main.kt 边界？",
            "你那边动哪些文件？有重叠吗？",
            "$other，我这边接口草好了，你按这个接",
        )
    }

    fun tryAgain(taskId: String? = selectedTaskId) {
        val id = taskId ?: return
        val task = tasks.firstOrNull { it.id == id } ?: return
        push(task.agentName, "上级：这一路重来", LineKind.INFO)
        journal(task.agentId, "上级要求重跑本路", LineKind.WARN)
        updateTask(id) {
            it.copy(
                status = TaskStatus.WORKING,
                beat = 0,
                statusLine = "重跑",
                proposalPath = null,
                proposalSummary = null,
                proposalNote = null,
            )
        }
        setWorker(task.agentId, id, "重跑中")
        tickToken++
    }

    fun leaveIt(taskId: String? = selectedTaskId) {
        val id = taskId ?: return
        val task = tasks.firstOrNull { it.id == id } ?: return
        push(task.agentName, "上级：关掉这路", LineKind.MUTED)
        journal(task.agentId, "上级关掉本路任务", LineKind.MUTED)
        updateTask(id) {
            it.copy(status = TaskStatus.STOPPED, statusLine = "已关掉", proposalPath = null)
        }
        // 关掉任务但 Agent 仍可留着；若用户从卡片「遣散」再移除
        setWorker(task.agentId, null, "空闲 · 任务已关")
        selectedTaskId = tasks.firstOrNull {
            it.status == TaskStatus.PROPOSAL || it.status == TaskStatus.WORKING
        }?.id
    }

    fun stopTask(taskId: String) = leaveIt(taskId)

    /** 遣散 Agent（从在场列表移除）。 */
    fun dismissAgent(agentId: String) {
        val w = workers.firstOrNull { it.id == agentId } ?: return
        w.taskId?.let { tid ->
            val t = tasks.firstOrNull { it.id == tid }
            if (t != null && (t.status == TaskStatus.WORKING || t.status == TaskStatus.PROPOSAL)) {
                updateTask(tid) {
                    it.copy(status = TaskStatus.STOPPED, statusLine = "随 Agent 遣散", proposalPath = null)
                }
            }
        }
        push(w.name, "被遣散离场", LineKind.MUTED)
        workers.removeAll { it.id == agentId }
        journals.remove(agentId)
        if (detailAgentId == agentId) closeDetail()
        if (workers.isNotEmpty()) {
            teamNote("${w.name} 离场 · 剩余 ${workers.size} 人", LineKind.MUTED)
            workers.forEach { briefTeamTo(it.id, "同事离场") }
        } else {
            teamNote("全员离场", LineKind.MUTED)
        }
    }

    fun stop() {
        tasks.filter { it.status == TaskStatus.WORKING || it.status == TaskStatus.PROPOSAL }
            .toList()
            .forEach { t ->
                updateTask(t.id) { it.copy(status = TaskStatus.STOPPED, statusLine = "全停") }
            }
        workers.toList().forEach { dismissAgent(it.id) }
        selectedTaskId = null
        push(null, "全部遣散", LineKind.WARN)
    }

    fun resetRound() {
        tasks.clear()
        workers.clear()
        boardTasks.clear()
        journals.clear()
        activity.clear()
        teamBoard.clear()
        selectedTaskId = null
        closeDetail()
        draft = "启动流程补「会话已授权」说明"
        agentSeq = 0
        taskSeq = 0
        boardSeq = 0
        appendAgent("[项目] 清空 · 需要时再 spawn", LineKind.MUTED)
    }

    fun focusCollab() {
        rightTab = 1
    }
}
