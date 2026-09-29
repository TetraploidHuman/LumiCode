package com.lumicode.dshbridge

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.Serializable

@Serializable
data class TraceStep(
    /** think | say | tool | tool_result | approval */
    val kind: String,
    val text: String,
    val name: String? = null,
    val seq: Int? = null,
)

@Serializable
data class PendingApproval(
    val id: String,
    val toolName: String,
    val argsPreview: String = "",
    val callId: String? = null,
)

@Serializable
data class ProgressSnapshot(
    val jobId: String,
    val steps: List<TraceStep> = emptyList(),
    val done: Boolean = false,
    val cancelled: Boolean = false,
    val sessionId: String? = null,
    val partialReply: String? = null,
    val pendingApproval: PendingApproval? = null,
)

/** In-flight chat traces keyed by client jobId, for live Squad journal polling. */
object ChatProgress {
    private data class Slot(
        val steps: MutableList<TraceStep> = mutableListOf(),
        @Volatile var done: Boolean = false,
        @Volatile var cancelled: Boolean = false,
        @Volatile var sessionId: String? = null,
        @Volatile var partialReply: String? = null,
        @Volatile var pendingApproval: PendingApproval? = null,
        val cancelFlag: AtomicBoolean = AtomicBoolean(false),
        /** Tool callIds the supervisor already allowed for this job. */
        val approvedCallIds: MutableSet<String> = mutableSetOf(),
        @Volatile var awaitApproval: Boolean = false,
    )

    private val jobs = ConcurrentHashMap<String, Slot>()
    private val sessionToJob = ConcurrentHashMap<String, String>()

    fun begin(jobId: String) {
        jobs[jobId] = Slot()
    }

    fun bindSession(jobId: String, sessionId: String) {
        jobs[jobId]?.sessionId = sessionId
        sessionToJob[sessionId] = jobId
    }

    fun append(jobId: String, step: TraceStep) {
        jobs[jobId]?.let { slot ->
            synchronized(slot.steps) { slot.steps.add(step) }
            if (step.kind == "say" && step.text.isNotBlank()) {
                slot.partialReply = step.text
            }
        }
    }

    fun setPartialReply(jobId: String, text: String) {
        jobs[jobId]?.partialReply = text
    }

    fun setPendingApproval(jobId: String, approval: PendingApproval?) {
        val slot = jobs[jobId] ?: return
        slot.pendingApproval = approval
        slot.awaitApproval = approval != null
    }

    fun approve(jobId: String, callId: String?) {
        val slot = jobs[jobId] ?: return
        if (!callId.isNullOrBlank()) {
            synchronized(slot.approvedCallIds) { slot.approvedCallIds.add(callId) }
        }
        slot.pendingApproval = null
        slot.awaitApproval = false
    }

    fun isCallApproved(jobId: String, callId: String?): Boolean {
        if (callId.isNullOrBlank()) return false
        val slot = jobs[jobId] ?: return false
        return synchronized(slot.approvedCallIds) { callId in slot.approvedCallIds }
    }

    fun isAwaitingApproval(jobId: String): Boolean = jobs[jobId]?.awaitApproval == true

    fun requestCancel(jobId: String): Boolean {
        val slot = jobs[jobId] ?: return false
        slot.cancelFlag.set(true)
        slot.cancelled = true
        slot.awaitApproval = false
        slot.pendingApproval = null
        return true
    }

    fun requestCancelBySession(sessionId: String): String? {
        val jobId = sessionToJob[sessionId] ?: return null
        requestCancel(jobId)
        return jobId
    }

    fun isCancelled(jobId: String): Boolean =
        jobs[jobId]?.cancelFlag?.get() == true

    fun snapshot(jobId: String): ProgressSnapshot {
        val slot = jobs[jobId] ?: return ProgressSnapshot(jobId = jobId)
        val steps = synchronized(slot.steps) { slot.steps.toList() }
        return ProgressSnapshot(
            jobId = jobId,
            steps = steps,
            done = slot.done,
            cancelled = slot.cancelled,
            sessionId = slot.sessionId,
            partialReply = slot.partialReply,
            pendingApproval = slot.pendingApproval,
        )
    }

    fun finish(jobId: String) {
        jobs[jobId]?.done = true
        jobs[jobId]?.awaitApproval = false
    }

    fun forget(jobId: String) {
        val sid = jobs[jobId]?.sessionId
        jobs.remove(jobId)
        if (sid != null) sessionToJob.remove(sid, jobId)
    }
}
