package com.lumicode.editor.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumicode.editor.platform.clockLabel

/** 协作态势：人是上级，Agent 是合作者。 */
enum class CollabPhase {
    IDLE,
    SURVEYING,
    AWAIT_PLAN,
    RUNNING,
    AWAIT_PATCH,
    HANDWRITING,
    DONE,
}

/** 参与深度。 */
enum class CollabMode {
    /** 默认：补丁必经批准，关键节点可选手写。 */
    COWRITE,
    /** 偏计划与批量批 Diff。 */
    DIRECT,
    /** Agent 暂停写盘，人主写。 */
    TAKEOVER,
}

enum class PlanNodeStatus {
    PENDING,
    ACTIVE,
    AWAIT_YOU,
    DONE,
    SKIPPED,
}

data class PlanNode(
    val id: String,
    val title: String,
    val status: PlanNodeStatus,
)

data class PatchProposal(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val summary: String,
    val alignment: String,
    val untouched: String,
    val blindSpots: String,
)

data class CollabChatMessage(
    val fromUser: Boolean,
    val text: String,
    val time: String,
)

data class AgentLogEntry(
    val time: String,
    val text: String,
    val kind: LineKind,
)

/**
 * 人机共作会话（MVP：假数据驱动状态机，尚未接 DSH）。
 *
 * 决策面在 UI；本类只维护态势、任务图、待批补丁与 AGENT 日志。
 */
class CollabState {

    var phase by mutableStateOf(CollabPhase.IDLE)
    var mode by mutableStateOf(CollabMode.COWRITE)

    /** 右栏：参考 | 共作。默认共作，便于体验 MVP。 */
    var rightTab by mutableStateOf(1) // 0 = 参考, 1 = 共作

    var intentGoal by mutableStateOf("为启动流程补一段可读的会话授权说明")
    var intentNonGoal by mutableStateOf("不改构建脚本，不引入新依赖")
    var intentAcceptance by mutableStateOf("Main 启动日志含「会话已授权」；Diff ≤ 2 个文件")

    val planNodes = mutableStateListOf<PlanNode>()
    val patches = mutableStateListOf<PatchProposal>()
    val agentLog = mutableStateListOf<AgentLogEntry>()
    val chat = mutableStateListOf<CollabChatMessage>()

    var chatDraft by mutableStateOf("")
    var selectedPatchPath by mutableStateOf<String?>(null)
    var currentNodeId by mutableStateOf<String?>(null)
    var surveyToken by mutableStateOf(0)
        private set

    /** 接手手写前的阶段与模式，便于恢复。 */
    private var phaseBeforeHandwriting: CollabPhase? = null
    private var modeBeforeHandwriting: CollabMode = CollabMode.COWRITE

    val phaseLabel: String
        get() = when (phase) {
            CollabPhase.IDLE -> "空闲"
            CollabPhase.SURVEYING -> "摸清中"
            CollabPhase.AWAIT_PLAN -> "待你批计划"
            CollabPhase.RUNNING -> "执行中"
            CollabPhase.AWAIT_PATCH -> "待你批补丁"
            CollabPhase.HANDWRITING -> "你已接手"
            CollabPhase.DONE -> "本轮完成"
        }

    val primaryActionLabel: String?
        get() = when (phase) {
            CollabPhase.IDLE -> "提交意图"
            CollabPhase.SURVEYING -> null
            CollabPhase.AWAIT_PLAN -> "确认计划"
            CollabPhase.RUNNING -> "暂停"
            CollabPhase.AWAIT_PATCH -> "批准并继续"
            CollabPhase.HANDWRITING -> "交还 Agent"
            CollabPhase.DONE -> "开始新一轮"
        }

    /** 共写 + 待批补丁时才给「我来写」。 */
    val showHandwritingCta: Boolean
        get() = mode == CollabMode.COWRITE && phase == CollabPhase.AWAIT_PATCH

    val modeHint: String
        get() = when (mode) {
            CollabMode.COWRITE -> "共写 · 过目补丁，关键段可自己写"
            CollabMode.DIRECT -> "指挥 · 只批计划与补丁"
            CollabMode.TAKEOVER -> "接管 · 你主写"
        }

    fun pendingReviewLines(path: String): Set<Int> {
        if (phase != CollabPhase.AWAIT_PATCH && phase != CollabPhase.HANDWRITING) {
            return emptySet()
        }
        val patch = patches.firstOrNull { it.path == path } ?: return emptySet()
        return (patch.startLine..patch.endLine).toSet()
    }

    fun appendAgent(text: String, kind: LineKind = LineKind.INFO) {
        agentLog.add(AgentLogEntry(clockLabel(), text, kind))
        while (agentLog.size > 120) agentLog.removeAt(0)
    }

    fun primaryAction() {
        when (phase) {
            CollabPhase.IDLE -> submitIntent()
            CollabPhase.AWAIT_PLAN -> confirmPlan()
            CollabPhase.RUNNING -> pause()
            CollabPhase.AWAIT_PATCH -> approvePatch()
            CollabPhase.HANDWRITING -> returnToAgent()
            CollabPhase.DONE -> resetRound()
            CollabPhase.SURVEYING -> Unit
        }
    }

    fun submitIntent() {
        if (intentGoal.isBlank()) return
        rightTab = 1
        phase = CollabPhase.SURVEYING
        planNodes.clear()
        patches.clear()
        selectedPatchPath = null
        currentNodeId = null
        chat.add(CollabChatMessage(true, intentGoal.trim(), clockLabel()))
        appendAgent("[共作] 收到意图 · 开始只读摸清", LineKind.INFO)
        surveyToken++
    }

    /** 由 UI LaunchedEffect 在摸清结束后调用。 */
    fun finishSurvey() {
        if (phase != CollabPhase.SURVEYING) return
        planNodes.clear()
        planNodes.addAll(
            listOf(
                PlanNode("n1", "摸清启动与日志路径", PlanNodeStatus.DONE),
                PlanNode("n2", "起草授权说明补丁", PlanNodeStatus.AWAIT_YOU),
                PlanNode("n3", "自检文案与范围", PlanNodeStatus.PENDING),
            ),
        )
        currentNodeId = "n2"
        phase = CollabPhase.AWAIT_PLAN
        appendAgent("[共作] 影响面 · src/Main.kt（预计）", LineKind.OK)
        appendAgent("[共作] 计划已就绪 · 等待上级确认", LineKind.WARN)
        chat.add(
            CollabChatMessage(
                false,
                "建议只改 Main 启动旁路说明；构建与依赖不动。请确认或改计划。",
                clockLabel(),
            ),
        )
    }

    fun confirmPlan() {
        if (phase != CollabPhase.AWAIT_PLAN) return
        markNode("n2", PlanNodeStatus.ACTIVE)
        phase = CollabPhase.RUNNING
        appendAgent("[共作] 计划已授权 · 进入实现节点", LineKind.OK)
        // MVP：立即产出假补丁
        produceDemoPatch()
    }

    fun produceDemoPatch() {
        patches.clear()
        patches.add(
            PatchProposal(
                path = "src/Main.kt",
                startLine = 12,
                endLine = 18,
                summary = "在启动日志旁补充「会话已授权」说明注释",
                alignment = "对应节点：起草授权说明补丁",
                untouched = "未改 build / 依赖 / 其它入口",
                blindSpots = "未跑集成测试；未核对多平台入口文案",
            ),
        )
        selectedPatchPath = "src/Main.kt"
        markNode("n2", PlanNodeStatus.AWAIT_YOU)
        phase = CollabPhase.AWAIT_PATCH
        appendAgent("[共作] 补丁提案就绪 · src/Main.kt:12–18", LineKind.WARN)
        if (mode == CollabMode.COWRITE) {
            appendAgent("[共写] 可过目或手写该段", LineKind.INFO)
        } else if (mode == CollabMode.DIRECT) {
            appendAgent("[指挥] 审摘要后批准即可", LineKind.INFO)
        }
    }

    fun approvePatch() {
        if (phase != CollabPhase.AWAIT_PATCH) return
        markNode("n2", PlanNodeStatus.DONE)
        markNode("n3", PlanNodeStatus.DONE)
        currentNodeId = "n3"
        patches.clear()
        selectedPatchPath = null
        phase = CollabPhase.DONE
        appendAgent("[共作] 补丁已批准 · 本轮归档", LineKind.OK)
        chat.add(CollabChatMessage(false, "本轮完成。可开始新一轮，或继续手改代码。", clockLabel()))
    }

    fun revisePlan() {
        when (phase) {
            CollabPhase.AWAIT_PLAN, CollabPhase.AWAIT_PATCH, CollabPhase.RUNNING -> {
                phase = CollabPhase.AWAIT_PLAN
                patches.clear()
                selectedPatchPath = null
                markNode("n2", PlanNodeStatus.AWAIT_YOU)
                appendAgent("[共作] 打回计划 · 等待上级修改", LineKind.WARN)
            }
            else -> Unit
        }
    }

    fun pause() {
        if (phase != CollabPhase.RUNNING) return
        phase = CollabPhase.AWAIT_PLAN
        appendAgent("[共作] 已暂停 · 回到计划层", LineKind.MUTED)
    }

    fun takeOverHandwriting() {
        if (phase == CollabPhase.IDLE || phase == CollabPhase.SURVEYING) return
        phaseBeforeHandwriting = phase
        modeBeforeHandwriting = if (mode == CollabMode.TAKEOVER) CollabMode.COWRITE else mode
        mode = CollabMode.TAKEOVER
        phase = CollabPhase.HANDWRITING
        appendAgent("[共作] 你已接手 · Agent 旁路待命", LineKind.INFO)
    }

    fun returnToAgent() {
        if (phase != CollabPhase.HANDWRITING) return
        mode = modeBeforeHandwriting
        phase = phaseBeforeHandwriting ?: CollabPhase.AWAIT_PATCH
        phaseBeforeHandwriting = null
        appendAgent("[共作] 已交还 Agent · 恢复${modeLabel(mode)}", LineKind.OK)
    }

    fun applyMode(next: CollabMode) {
        if (next == mode && next != CollabMode.TAKEOVER) return
        val previous = mode
        mode = next
        when (next) {
            CollabMode.TAKEOVER -> takeOverHandwriting()
            CollabMode.COWRITE, CollabMode.DIRECT -> {
                if (phase == CollabPhase.HANDWRITING) returnToAgent()
            }
        }
        // returnToAgent / takeOver 可能改写 mode，再钉一次目标模式
        if (next != CollabMode.TAKEOVER) mode = next
        appendAgent(
            "[共作] 参与深度 ${modeLabel(previous)} → ${modeLabel(next)} · $modeHint",
            LineKind.MUTED,
        )
    }

    fun sendChat() {
        val text = chatDraft.trim()
        if (text.isEmpty()) return
        chat.add(CollabChatMessage(true, text, clockLabel()))
        chatDraft = ""
        appendAgent("[对话] $text", LineKind.MUTED)
        chat.add(
            CollabChatMessage(
                false,
                "已记录。MVP 尚未接模型；请用上方主按钮推进状态机。",
                clockLabel(),
            ),
        )
    }

    fun resetRound() {
        phase = CollabPhase.IDLE
        mode = CollabMode.COWRITE
        planNodes.clear()
        patches.clear()
        selectedPatchPath = null
        currentNodeId = null
        phaseBeforeHandwriting = null
        modeBeforeHandwriting = CollabMode.COWRITE
        appendAgent("[共作] 新一轮待命", LineKind.MUTED)
    }

    fun focusCollab() {
        rightTab = 1
    }

    private fun markNode(id: String, status: PlanNodeStatus) {
        val index = planNodes.indexOfFirst { it.id == id }
        if (index < 0) return
        planNodes[index] = planNodes[index].copy(status = status)
    }

    companion object {
        fun modeLabel(mode: CollabMode): String = when (mode) {
            CollabMode.COWRITE -> "共写"
            CollabMode.DIRECT -> "指挥"
            CollabMode.TAKEOVER -> "接管"
        }

        fun nodeStatusLabel(status: PlanNodeStatus): String = when (status) {
            PlanNodeStatus.PENDING -> "待做"
            PlanNodeStatus.ACTIVE -> "进行中"
            PlanNodeStatus.AWAIT_YOU -> "待你确认"
            PlanNodeStatus.DONE -> "完成"
            PlanNodeStatus.SKIPPED -> "跳过"
        }
    }
}
