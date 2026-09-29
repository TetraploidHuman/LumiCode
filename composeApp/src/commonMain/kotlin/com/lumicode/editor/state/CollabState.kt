package com.lumicode.editor.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.lumicode.editor.dsh.DshHealth
import com.lumicode.editor.dsh.DshTraceStep
import com.lumicode.editor.platform.clockLabel
import com.lumicode.editor.workspace.AgentFileEdit
import com.lumicode.editor.workspace.WorkspaceFileChange
import com.lumicode.editor.workspace.buildAgentPromptFooter
import com.lumicode.editor.workspace.parseAgentReply

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
    val modelHint: String? = null,
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
    /** 给人看的结论（禁止堆未解释的探索黑话） */
    val userBrief: String? = null,
    /** 探索期出现、用户未必认识的说法 → 人话对照 */
    val exploredTerms: List<ExploredTerm> = emptyList(),
    /** 对应共享任务板上的条目 */
    val boardTaskId: String? = null,
    /** DSH 提案中的磁盘改动（同意后落盘） */
    val proposalEdits: List<AgentFileEdit> = emptyList(),
    /** Pre-task workspace snapshot for change list / rollback. */
    val snapshotId: String? = null,
    /** Files changed since [snapshotId] (added / modified / deleted). */
    val changedFiles: List<WorkspaceFileChange> = emptyList(),
)

/** 探索期术语：汇报时对照用户已知情报，未知名才用人话拆开。 */
data class ExploredTerm(
    val term: String,
    val plain: String,
    val where: String? = null,
)

/** 用户已知情报里的一条。 */
data class KnownFact(
    val id: String,
    val label: String,
    val detail: String = "",
)

/** Live dangerous-tool approval request from the DSH bridge. */
data class PendingToolApproval(
    val taskId: String,
    val jobId: String,
    val toolName: String,
    val argsPreview: String,
    val callId: String? = null,
)

/** 用户理解程度：影响汇报措辞深浅。 */
enum class UnderstandingLevel {
    BEGINNER,
    FAMILIAR,
    EXPERT,
}

fun UnderstandingLevel.labelZh(): String = when (this) {
    UnderstandingLevel.BEGINNER -> "入门"
    UnderstandingLevel.FAMILIAR -> "熟悉本项目"
    UnderstandingLevel.EXPERT -> "深耕"
}

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

    /** 用户已知情报：汇报前先对照，已知的不重复解释。 */
    val knownFacts = mutableStateListOf<KnownFact>()
    var understandingLevel by mutableStateOf(UnderstandingLevel.FAMILIAR)
    var knownDraft by mutableStateOf("")
    private var knownSeq = 0

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

    /** 经 lumicode-dsh-bridge 连到本机已运行的 dsh-web。 */
    var dshLinked by mutableStateOf(false)
    var dshModelLabel by mutableStateOf<String?>(null)
    /** True while any DSH job is in flight. */
    var dshBusy by mutableStateOf(false)
    /** Monotonic token bumped when a new task is queued for DSH (triggers dispatcher). */
    var dshDispatchToken by mutableStateOf(0)
        private set
    /** Task ids waiting to be started (concurrent-safe queue). */
    val dshDispatchQueue = mutableStateListOf<String>()
    /** taskId → in-flight bridge jobId */
    private val dshJobByTask = mutableStateMapOf<String, String>()
    /** taskId → sessionId while running */
    private val dshSessionByTask = mutableStateMapOf<String, String>()
    private val activeDshTasks = mutableStateListOf<String>()
    private val dshSessionByAgent = mutableMapOf<String, String>()
    private var dshSupervisorNote: String? = null
    /** Ask before write/edit/bash (bridge cancels turn until approve). */
    var requireToolApproval by mutableStateOf(true)
    /** Live tool approval prompt (from bridge progress). */
    var pendingToolApproval by mutableStateOf<PendingToolApproval?>(null)

    private var agentSeq = 0
    private var taskSeq = 0
    private var boardSeq = 0

    fun isTermKnown(term: String): Boolean {
        val key = term.trim().lowercase()
        if (key.isEmpty()) return false
        return knownFacts.any { fact ->
            val L = fact.label.trim().lowercase()
            L == key || key in L || L in key
        }
    }

    fun termsNeedingExplain(terms: List<ExploredTerm>): List<ExploredTerm> =
        terms.filter { !isTermKnown(it.term) }

    fun termsAssumedKnown(terms: List<ExploredTerm>): List<ExploredTerm> =
        terms.filter { isTermKnown(it.term) }

    fun onDshHealth(health: DshHealth) {
        dshLinked = health.ok
        dshModelLabel = when {
            health.ok && health.provider != null && health.model != null ->
                "${health.provider} / ${health.model}"
            health.ok -> "DSH"
            else -> health.error ?: "DSH 未就绪"
        }
    }

    fun dshSessionFor(agentId: String): String? = dshSessionByAgent[agentId]

    fun consumeDshSupervisorNote(): String? {
        val note = dshSupervisorNote
        dshSupervisorNote = null
        return note
    }

    fun buildDshPrompt(
        taskTitle: String,
        supervisorNote: String? = null,
        workspaceBlock: String? = null,
        writeScopes: List<String> = emptyList(),
    ): String {
        val known = knownFacts.take(8).joinToString("\n") { "- ${it.label}：${it.detail}" }
        return buildString {
            appendLine("你是 LumiCode 编辑器的同伴 Agent，正在小队里执行一项代码任务。")
            appendLine("任务：$taskTitle")
            appendLine("约束：$constraint")
            appendLine("用户理解口径：${understandingLevel.labelZh()}")
            if (writeScopes.isNotEmpty()) {
                appendLine()
                appendLine("【硬性写盘范围】只允许改动下列相对路径前缀（越界即失败）：")
                writeScopes.forEach { appendLine("- $it") }
            }
            if (!workspaceBlock.isNullOrBlank()) {
                appendLine()
                appendLine("工作区摘要（真实磁盘；请用工具自行打开需要的文件，勿依赖本摘要代替读盘）：")
                appendLine(workspaceBlock)
            }
            if (known.isNotBlank()) {
                appendLine()
                appendLine("用户已知情报（不要重复解释这些）：")
                appendLine(known)
            }
            supervisorNote?.trim()?.takeIf { it.isNotEmpty() }?.let {
                appendLine()
                appendLine("上级补充意见：$it")
            }
            append(buildAgentPromptFooter())
        }
    }

    fun writeScopesForTask(taskId: String): List<String> {
        val task = tasks.firstOrNull { it.id == taskId } ?: return emptyList()
        val board = task.boardTaskId?.let { bid -> boardTasks.firstOrNull { it.id == bid } }
        return board?.writeScopes.orEmpty().filter { it.isNotBlank() }
    }

    fun isBoardReady(boardTaskId: String?): Boolean {
        if (boardTaskId == null) return true
        val board = boardTasks.firstOrNull { it.id == boardTaskId } ?: return true
        if (board.blockedBy.isEmpty()) return true
        val completed = boardTasks.filter { it.status == BoardTaskStatus.COMPLETED }.map { it.id }.toSet()
        return board.blockedBy.all { it in completed }
    }

    private fun scheduleDshTask(taskId: String) {
        if (!isBoardReady(tasks.firstOrNull { it.id == taskId }?.boardTaskId)) {
            journal(
                tasks.firstOrNull { it.id == taskId }?.agentId ?: return,
                "任务板未就绪 · 依赖尚未完成，暂缓派往 DSH",
                LineKind.WARN,
            )
            updateTask(taskId) { it.copy(statusLine = "等待依赖") }
            return
        }
        if (taskId !in dshDispatchQueue && taskId !in activeDshTasks) {
            dshDispatchQueue.add(taskId)
            dshDispatchToken++
        }
    }

    fun takeNextDshDispatch(): String? {
        if (dshDispatchQueue.isEmpty()) return null
        val id = dshDispatchQueue.removeAt(0)
        if (id !in activeDshTasks) activeDshTasks.add(id)
        refreshDshBusy()
        return id
    }

    fun bindDshJob(taskId: String, jobId: String, sessionId: String?) {
        dshJobByTask[taskId] = jobId
        if (!sessionId.isNullOrBlank()) {
            dshSessionByTask[taskId] = sessionId
            tasks.firstOrNull { it.id == taskId }?.agentId?.let { aid ->
                dshSessionByAgent[aid] = sessionId
            }
        }
    }

    fun dshJobFor(taskId: String): String? = dshJobByTask[taskId]

    fun dshSessionForTask(taskId: String): String? =
        dshSessionByTask[taskId] ?: tasks.firstOrNull { it.id == taskId }?.let { dshSessionFor(it.agentId) }

    fun updateTaskLiveStatus(taskId: String, line: String) {
        updateTask(taskId) { t ->
            if (t.status != TaskStatus.WORKING) t else t.copy(statusLine = line)
        }
        val agentId = tasks.firstOrNull { it.id == taskId }?.agentId ?: return
        setWorker(agentId, taskId, line.take(40), phase = MemberPhase.ACTIVE)
    }

    fun finishDshDispatch(taskId: String) {
        activeDshTasks.remove(taskId)
        dshJobByTask.remove(taskId)
        dshSessionByTask.remove(taskId)
        refreshDshBusy()
        // Kick any tasks that were waiting on board deps.
        tasks.filter {
            it.status == TaskStatus.WORKING &&
                it.id !in activeDshTasks &&
                it.id !in dshDispatchQueue &&
                isBoardReady(it.boardTaskId) &&
                it.statusLine.contains("等待依赖")
        }.forEach { scheduleDshTask(it.id) }
    }

    private fun refreshDshBusy() {
        dshBusy = activeDshTasks.isNotEmpty() || dshDispatchQueue.isNotEmpty()
    }

    fun completeDshTask(
        taskId: String,
        reply: String,
        sessionId: String?,
        snapshotId: String? = null,
        changedFiles: List<WorkspaceFileChange> = emptyList(),
    ) {
        val task = tasks.firstOrNull { it.id == taskId } ?: return
        sessionId?.let { dshSessionByAgent[task.agentId] = it }
        val agentId = task.agentId
        val agent = task.agentName
        // 仍尝试解析旧格式 edits（兼容）；主路径是 DSH 工具已写盘，摘要给人看。
        val parsed = parseAgentReply(reply)
        val brief = parsed.summary.ifBlank { reply.trim() }.ifBlank { "（无摘要）" }
        val first = parsed.edits.firstOrNull()
            ?: changedFiles.firstOrNull()?.let {
                AgentFileEdit(it.path, 0, 0, "")
            }
        val note = buildString {
            append(dshModelLabel ?: "DSH · qwen35-9b")
            append(" · 文件由 DSH 工具改盘")
            if (changedFiles.isNotEmpty()) append(" · ${changedFiles.size} 个文件变动")
        }
        updateTask(taskId) {
            it.copy(
                beat = 1,
                status = TaskStatus.PROPOSAL,
                statusLine = if (changedFiles.isEmpty()) {
                    "DSH 已执行 · 等你确认"
                } else {
                    "改了 ${changedFiles.size} 个文件 · 等你确认或回滚"
                },
                proposalPath = first?.path ?: changedFiles.firstOrNull()?.path,
                proposalStart = first?.startLine ?: 0,
                proposalEnd = first?.endLine ?: 0,
                proposalSummary = brief,
                proposalNote = note,
                userBrief = brief,
                exploredTerms = emptyList(),
                proposalEdits = parsed.edits,
                snapshotId = snapshotId ?: it.snapshotId,
                changedFiles = changedFiles,
            )
        }
        setWorker(agentId, taskId, "等你定 · 可确认或回滚", phase = MemberPhase.ACTIVE)
        journal(agentId, "DSH 回执 · ${dshModelLabel ?: "qwen35-9b"}（工具写盘）", LineKind.WARN)
        journal(agentId, brief.lines().firstOrNull()?.take(160) ?: brief.take(160), LineKind.INFO)
        if (changedFiles.isNotEmpty()) {
            journal(agentId, "改动记录 · ${formatChangedFiles(changedFiles)}", LineKind.WARN)
        }
        teamNote("$agent DSH 完成 · ${changedFiles.size} 处改动", LineKind.WARN)
        finishDshDispatch(taskId)
        if (pendingToolApproval?.taskId == taskId) pendingToolApproval = null
    }

    fun bindTaskSnapshot(taskId: String, snapshotId: String?) {
        updateTask(taskId) { it.copy(snapshotId = snapshotId, changedFiles = emptyList()) }
    }

    fun clearTaskSnapshot(taskId: String) {
        updateTask(taskId) { it.copy(snapshotId = null, changedFiles = emptyList()) }
    }

    fun clearDshSessions() {
        dshSessionByAgent.clear()
        dshSessionByTask.clear()
    }

    /** Soft-cancel request: IdeState/Effects call bridge cancel then restore. */
    fun requestCancelDsh(taskId: String): String? {
        val jobId = dshJobByTask[taskId]
        dshDispatchQueue.removeAll { it == taskId }
        if (pendingToolApproval?.taskId == taskId) pendingToolApproval = null
        return jobId
    }

    fun setPendingApproval(approval: PendingToolApproval?) {
        pendingToolApproval = approval
    }

    fun clearPendingApproval(taskId: String? = null) {
        if (taskId == null || pendingToolApproval?.taskId == taskId) {
            pendingToolApproval = null
        }
    }

    /** Live DSH tool/think/say lines into this agent's work log (like DSH rail). */
    fun appendDshTrace(agentId: String, steps: List<DshTraceStep>) {
        if (steps.isEmpty()) return
        for (step in steps) {
            when (step.kind) {
                "think" -> journal(agentId, "思考 · ${step.text}", LineKind.MUTED)
                "say" -> journal(agentId, step.text, LineKind.INFO)
                "tool" -> {
                    val args = step.text.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                    journal(agentId, "工具 · ${step.name ?: "call"}$args", LineKind.WARN)
                    setWorker(
                        agentId,
                        workers.firstOrNull { it.id == agentId }?.taskId,
                        "工具 · ${step.name ?: "call"}",
                        phase = MemberPhase.ACTIVE,
                    )
                }
                "tool_result" -> {
                    val label = if (step.name == "error") "工具失败" else "工具结果"
                    journal(agentId, "$label · ${step.text}", LineKind.MUTED)
                }
                else -> journal(agentId, step.text, LineKind.INFO)
            }
        }
    }

    fun failDshTask(taskId: String, error: String) {
        val task = tasks.firstOrNull { it.id == taskId } ?: return
        journal(task.agentId, "DSH 失败 · $error", LineKind.WARN)
        updateTask(taskId) {
            it.copy(status = TaskStatus.STOPPED, statusLine = "DSH 失败")
        }
        setWorker(task.agentId, null, "DSH 失败 · $error", phase = MemberPhase.FAILED)
        finishDshDispatch(taskId)
        if (pendingToolApproval?.taskId == taskId) pendingToolApproval = null
    }

    private fun kickTask(taskId: String, agentId: String, title: String) {
        if (!dshLinked) {
            journal(agentId, "DSH 未连接 · 请确认桥接与本机 dsh-web 在跑", LineKind.WARN)
            updateTask(taskId) { it.copy(statusLine = "DSH 未连接") }
            setWorker(agentId, taskId, "DSH 未连接", phase = MemberPhase.FAILED)
            return
        }
        setWorker(agentId, taskId, "已发往 DSH · qwen35-9b", phase = MemberPhase.ACTIVE)
        journal(agentId, "经 lumicode-dsh-bridge → dsh-web（qwen35-250 / qwen35-9b）", LineKind.MUTED)
        scheduleDshTask(taskId)
    }

    fun addKnownFact(label: String, detail: String = "") {
        val L = label.trim()
        if (L.isEmpty()) return
        if (isTermKnown(L)) return
        knownSeq++
        knownFacts.add(0, KnownFact(id = "k$knownSeq", label = L, detail = detail.trim()))
        knownDraft = ""
        teamNote("已知情报 +「$L」", LineKind.MUTED)
    }

    fun removeKnownFact(id: String) {
        val gone = knownFacts.firstOrNull { it.id == id } ?: return
        knownFacts.removeAll { it.id == id }
        teamNote("已知情报 −「${gone.label}」", LineKind.MUTED)
    }

    fun markTermKnown(term: ExploredTerm) {
        addKnownFact(term.term, term.plain)
    }

    fun cycleUnderstandingLevel() {
        understandingLevel = when (understandingLevel) {
            UnderstandingLevel.BEGINNER -> UnderstandingLevel.FAMILIAR
            UnderstandingLevel.FAMILIAR -> UnderstandingLevel.EXPERT
            UnderstandingLevel.EXPERT -> UnderstandingLevel.BEGINNER
        }
        teamNote("汇报口径改为「${understandingLevel.labelZh()}」", LineKind.INFO)
    }

    val phaseLabel: String
        get() {
            val w = tasks.count { it.status == TaskStatus.WORKING }
            val p = tasks.count { it.status == TaskStatus.PROPOSAL }
            return when {
                w > 0 -> "$w 路在跑"
                p > 0 -> "$p 个提案"
                workers.isEmpty() -> "尚未开路"
                else -> "${workers.size} 名同伴"
            }
        }

    val briefing: String
        get() = when {
            workers.isEmpty() -> "小队 · 你是上级 · 按需开路"
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
            roleLabel = "上级",
            status = MemberLiveStatus.INACTIVE,
            description = "派活与拍板",
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
                roleLabel = "同伴",                status = liveStatusOf(w),
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
        while (list.size > 220) list.removeAt(0)
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
                modelHint = if (dshLinked) "qwen35-9b" else null,
            ),
        )
        ensureJournal(agentId)
        journal(agentId, "开路 · 独立上下文 · 任务「$q」", LineKind.INFO)
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
            teamNote("$name 入场 · 上级下的第一名同伴", LineKind.INFO)
        } else {
            teamNote(
                "$name 入场 · 「${if (q.length <= 12) q else q.take(12) + "…"}」· 同伴 ${workers.size}",
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
        push(name, "开路 · $q", LineKind.INFO)
        kickTask(taskId, agentId, q)
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
        }
        assignDraft = ""
        workers.firstOrNull { it.id == agentId }?.taskId?.let { tid ->
            kickTask(tid, agentId, q)
        }
    }

    /** Agent 之间交流：写入双方日志（需对方在线查看，无自动代答）。 */
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
        talkDraft = ""
    }

    fun selectTask(id: String) {
        selectedTaskId = id
    }

    fun goAlong(taskId: String? = selectedTaskId) {
        val id = taskId ?: return
        val task = tasks.firstOrNull { it.id == id } ?: return
        if (task.status != TaskStatus.PROPOSAL) return
        push(task.agentName, "上级同意 · 本路收束", LineKind.OK)
        journal(task.agentId, "上级同意 · 本刀收束", LineKind.OK)
        teamNote("上级同意 ${task.agentName} · 「${task.title.take(14)}」", LineKind.OK)
        updateTask(id) { it.copy(status = TaskStatus.DONE, statusLine = "已同意") }
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
            journal(peerId, "上级已同意 ${task.agentName}，我继续本职", LineKind.MUTED)
        }
        selectedTaskId = tasks.firstOrNull {
            it.status == TaskStatus.PROPOSAL || it.status == TaskStatus.WORKING
        }?.id
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
                userBrief = null,
                exploredTerms = emptyList(),
                changedFiles = emptyList(),
                // snapshotId kept until IdeState restores + CollabDshEffects makes a new one
            )
        }
        setWorker(task.agentId, id, "重跑中")
        kickTask(id, task.agentId, task.title)
    }

    /** 上级给出修改意见，同伴按意见重做。 */
    fun giveOpinion(taskId: String? = selectedTaskId, note: String) {
        val q = note.trim()
        if (q.isEmpty()) return
        val id = taskId ?: return
        val task = tasks.firstOrNull { it.id == id } ?: return
        if (task.status != TaskStatus.PROPOSAL) return
        val short = if (q.length <= 36) q else q.take(36) + "…"
        push(task.agentName, "上级意见 · $short", LineKind.WARN)
        journal(task.agentId, "上级意见 · $q", LineKind.WARN)
        teamNote("上级意见给 ${task.agentName} · $short", LineKind.WARN)
        updateTask(id) {
            it.copy(
                status = TaskStatus.WORKING,
                beat = 0,
                statusLine = "按意见改",
                proposalPath = null,
                proposalSummary = null,
                proposalNote = q,
                userBrief = null,
                exploredTerms = emptyList(),
            )
        }
        setWorker(task.agentId, id, "按意见改写中")
        dshSupervisorNote = q
        kickTask(id, task.agentId, task.title)
    }

    fun leaveIt(taskId: String? = selectedTaskId) {
        val id = taskId ?: return
        val task = tasks.firstOrNull { it.id == id } ?: return
        requestCancelDsh(id)
        push(task.agentName, "上级：取消这路", LineKind.MUTED)
        journal(task.agentId, "上级取消本路任务", LineKind.MUTED)
        updateTask(id) {
            it.copy(
                status = TaskStatus.STOPPED,
                statusLine = "已取消",
                proposalPath = null,
                changedFiles = emptyList(),
                // snapshotId cleared by IdeState after restore
            )
        }
        finishDshDispatch(id)
        // 取消任务但同伴仍可留着；若用户从卡片「遣散」再移除
        setWorker(task.agentId, null, "空闲 · 任务已取消")
        selectedTaskId = tasks.firstOrNull {
            it.status == TaskStatus.PROPOSAL || it.status == TaskStatus.WORKING
        }?.id
    }

    fun markRollbackDone(taskId: String, detail: String) {
        val task = tasks.firstOrNull { it.id == taskId } ?: return
        journal(task.agentId, "已回滚磁盘 · $detail", LineKind.OK)
        clearTaskSnapshot(taskId)
    }

    fun stopTask(taskId: String) = leaveIt(taskId)

    /** 遣散 Agent（从在场列表移除）。 */
    fun dismissAgent(agentId: String) {
        val w = workers.firstOrNull { it.id == agentId } ?: return
        w.taskId?.let { tid ->
            val t = tasks.firstOrNull { it.id == tid }
            if (t != null && (t.status == TaskStatus.WORKING || t.status == TaskStatus.PROPOSAL)) {
                updateTask(tid) {
                    it.copy(status = TaskStatus.STOPPED, statusLine = "随同伴遣散", proposalPath = null)
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
        val running = activeDshTasks.toList() + dshDispatchQueue.toList()
        dshDispatchQueue.clear()
        running.distinct().forEach { tid ->
            requestCancelDsh(tid)
            finishDshDispatch(tid)
        }
        pendingToolApproval = null
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
        appendAgent("[项目] 清空 · 需要时再开路", LineKind.MUTED)
    }

    fun focusCollab() {
        rightTab = 1
    }
}

internal fun formatChangedFiles(changes: List<WorkspaceFileChange>, limit: Int = 8): String {
    if (changes.isEmpty()) return "无"
    val body = changes.take(limit).joinToString("；") { c ->
        val tag = when (c.kind) {
            "added" -> "+"
            "deleted" -> "−"
            else -> "~"
        }
        "$tag${c.path}"
    }
    return if (changes.size > limit) "$body …共${changes.size}个" else body
}
