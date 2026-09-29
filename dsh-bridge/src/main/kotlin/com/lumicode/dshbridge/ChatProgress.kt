package com.lumicode.dshbridge

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

@Serializable
data class TraceStep(
    /** think | say | tool | tool_result */
    val kind: String,
    val text: String,
    val name: String? = null,
    val seq: Int? = null,
)

@Serializable
data class ProgressSnapshot(
    val jobId: String,
    val steps: List<TraceStep> = emptyList(),
    val done: Boolean = false,
)

/** In-flight chat traces keyed by client jobId, for live Squad journal polling. */
object ChatProgress {
    private data class Slot(
        val steps: MutableList<TraceStep> = mutableListOf(),
        @Volatile var done: Boolean = false,
    )

    private val jobs = ConcurrentHashMap<String, Slot>()

    fun begin(jobId: String) {
        jobs[jobId] = Slot()
    }

    fun append(jobId: String, step: TraceStep) {
        jobs[jobId]?.let { slot ->
            synchronized(slot.steps) { slot.steps.add(step) }
        }
    }

    fun snapshot(jobId: String): ProgressSnapshot {
        val slot = jobs[jobId] ?: return ProgressSnapshot(jobId = jobId)
        val steps = synchronized(slot.steps) { slot.steps.toList() }
        return ProgressSnapshot(jobId = jobId, steps = steps, done = slot.done)
    }

    fun finish(jobId: String) {
        jobs[jobId]?.done = true
    }

    fun forget(jobId: String) {
        jobs.remove(jobId)
    }
}
