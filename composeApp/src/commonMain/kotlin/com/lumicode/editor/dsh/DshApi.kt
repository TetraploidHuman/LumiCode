package com.lumicode.editor.dsh

/**
 * Browser → `/api/dsh/…` → lumicode-dsh-bridge → dsh-web (qwen35-250 / qwen35-9b).
 *
 * File read/write is DSH's job (session cwd). LumiCode only sends the workspace path
 * and refreshes the editor from disk after the agent finishes.
 *
 * While a chat runs, pass a [jobId] and poll [progress] to stream tool/think steps
 * into the Squad work log (same events DSH's rail shows).
 */
data class DshHealth(
    val ok: Boolean,
    val provider: String? = null,
    val model: String? = null,
    val error: String? = null,
)

/** One durable DSH step: think / say / tool / tool_result / approval. */
data class DshTraceStep(
    val kind: String,
    val text: String,
    val name: String? = null,
    val seq: Int? = null,
)

data class DshPendingApproval(
    val id: String,
    val toolName: String,
    val argsPreview: String = "",
    val callId: String? = null,
)

data class DshChatResult(
    val ok: Boolean,
    val reply: String? = null,
    val sessionId: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val error: String? = null,
    val steps: List<DshTraceStep> = emptyList(),
    val cancelled: Boolean = false,
)

data class DshProgress(
    val jobId: String,
    val steps: List<DshTraceStep> = emptyList(),
    val done: Boolean = false,
    val cancelled: Boolean = false,
    val sessionId: String? = null,
    val partialReply: String? = null,
    val pendingApproval: DshPendingApproval? = null,
)

data class DshCancelResult(
    val ok: Boolean,
    val sessionId: String? = null,
    val jobId: String? = null,
    val error: String? = null,
)

interface DshBackend {
    suspend fun health(): DshHealth
    suspend fun chat(
        text: String,
        sessionId: String? = null,
        title: String? = null,
        cwd: String? = null,
        jobId: String? = null,
        requireToolApproval: Boolean = false,
        writeScopes: List<String> = emptyList(),
    ): DshChatResult

    suspend fun progress(jobId: String): DshProgress

    suspend fun cancel(sessionId: String? = null, jobId: String? = null): DshCancelResult

    suspend fun approve(jobId: String, callId: String? = null): Boolean
}

object DshApi {
    var backend: DshBackend? = null

    suspend fun health(): DshHealth =
        backend?.health() ?: DshHealth(ok = false, error = "DSH backend not installed")

    suspend fun chat(
        text: String,
        sessionId: String? = null,
        title: String? = null,
        cwd: String? = null,
        jobId: String? = null,
        requireToolApproval: Boolean = false,
        writeScopes: List<String> = emptyList(),
    ): DshChatResult =
        backend?.chat(text, sessionId, title, cwd, jobId, requireToolApproval, writeScopes)
            ?: DshChatResult(ok = false, error = "DSH backend not installed")

    suspend fun progress(jobId: String): DshProgress =
        backend?.progress(jobId) ?: DshProgress(jobId = jobId)

    suspend fun cancel(sessionId: String? = null, jobId: String? = null): DshCancelResult =
        backend?.cancel(sessionId, jobId)
            ?: DshCancelResult(ok = false, error = "DSH backend not installed")

    suspend fun approve(jobId: String, callId: String? = null): Boolean =
        backend?.approve(jobId, callId) ?: false
}

fun parseDshHealth(raw: String): DshHealth =
    DshHealth(
        ok = raw.boolField("ok"),
        provider = raw.stringField("provider"),
        model = raw.stringField("model"),
        error = raw.stringField("error"),
    )

fun parseDshChat(raw: String): DshChatResult =
    DshChatResult(
        ok = raw.boolField("ok"),
        reply = raw.stringField("reply"),
        sessionId = raw.stringField("sessionId"),
        provider = raw.stringField("provider"),
        model = raw.stringField("model"),
        error = raw.stringField("error"),
        steps = parseTraceSteps(raw),
        cancelled = raw.boolField("cancelled"),
    )

fun parseDshProgress(raw: String): DshProgress =
    DshProgress(
        jobId = raw.stringField("jobId").orEmpty(),
        steps = parseTraceSteps(raw),
        done = raw.boolField("done"),
        cancelled = raw.boolField("cancelled"),
        sessionId = raw.stringField("sessionId"),
        partialReply = raw.stringField("partialReply"),
        pendingApproval = parsePendingApproval(raw),
    )

fun parseDshCancel(raw: String): DshCancelResult =
    DshCancelResult(
        ok = raw.boolField("ok"),
        sessionId = raw.stringField("sessionId"),
        jobId = raw.stringField("jobId"),
        error = raw.stringField("error"),
    )

fun parsePendingApproval(raw: String): DshPendingApproval? {
    val obj = raw.jsonObjectBody("pendingApproval") ?: return null
    val id = obj.stringField("id") ?: return null
    return DshPendingApproval(
        id = id,
        toolName = obj.stringField("toolName").orEmpty(),
        argsPreview = obj.stringField("argsPreview").orEmpty(),
        callId = obj.stringField("callId"),
    )
}

fun parseTraceSteps(raw: String): List<DshTraceStep> {
    val arr = raw.jsonArrayBody("steps") ?: return emptyList()
    return arr.jsonObjectSlices().mapNotNull { obj ->
        val kind = obj.stringField("kind") ?: return@mapNotNull null
        DshTraceStep(
            kind = kind,
            text = obj.stringField("text").orEmpty(),
            name = obj.stringField("name"),
            seq = obj.intField("seq"),
        )
    }
}

fun jsonString(value: String): String =
    buildString(value.length + 8) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

fun buildDshChatBody(
    text: String,
    sessionId: String?,
    title: String?,
    cwd: String?,
    jobId: String?,
    requireToolApproval: Boolean,
    writeScopes: List<String>,
): String = buildString {
    append("{\"text\":")
    append(jsonString(text))
    if (!sessionId.isNullOrBlank()) {
        append(",\"sessionId\":")
        append(jsonString(sessionId))
    }
    if (!title.isNullOrBlank()) {
        append(",\"title\":")
        append(jsonString(title))
    }
    if (!cwd.isNullOrBlank()) {
        append(",\"cwd\":")
        append(jsonString(cwd))
    }
    if (!jobId.isNullOrBlank()) {
        append(",\"jobId\":")
        append(jsonString(jobId))
    }
    if (requireToolApproval) {
        append(",\"requireToolApproval\":true")
    }
    if (writeScopes.isNotEmpty()) {
        append(",\"writeScopes\":[")
        writeScopes.forEachIndexed { i, s ->
            if (i > 0) append(',')
            append(jsonString(s))
        }
        append(']')
    }
    append('}')
}

fun newDshJobId(): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    return buildString(20) {
        append("job-")
        repeat(16) { append(alphabet.random()) }
    }
}

private fun String.boolField(key: String): Boolean {
    val re = Regex(""""$key"\s*:\s*(true|false)""")
    return re.find(this)?.groupValues?.getOrNull(1) == "true"
}

private fun String.stringField(key: String): String? {
    val re = Regex(""""$key"\s*:\s*(null|"([^"\\]|\\.)*")""")
    val m = re.find(this) ?: return null
    val raw = m.groupValues[1]
    if (raw == "null") return null
    return raw.removeSurrounding("\"")
        .replace("\\n", "\n")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}

private fun String.intField(key: String): Int? {
    val re = Regex(""""$key"\s*:\s*(-?\d+)""")
    return re.find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()
}

private fun String.jsonArrayBody(key: String): String? {
    val keyPat = "\"$key\""
    val at = indexOf(keyPat)
    if (at < 0) return null
    var j = at + keyPat.length
    while (j < length && this[j].isWhitespace()) j++
    if (j >= length || this[j] != ':') return null
    j++
    while (j < length && this[j].isWhitespace()) j++
    if (j >= length || this[j] != '[') return null
    val start = j
    var depth = 0
    var inStr = false
    var esc = false
    while (j < length) {
        val ch = this[j]
        if (inStr) {
            when {
                esc -> esc = false
                ch == '\\' -> esc = true
                ch == '"' -> inStr = false
            }
            j++
            continue
        }
        when (ch) {
            '"' -> {
                inStr = true
                j++
            }
            '[' -> {
                depth++
                j++
            }
            ']' -> {
                depth--
                j++
                if (depth == 0) return substring(start, j)
            }
            else -> j++
        }
    }
    return null
}

private fun String.jsonObjectBody(key: String): String? {
    val keyPat = "\"$key\""
    val at = indexOf(keyPat)
    if (at < 0) return null
    var j = at + keyPat.length
    while (j < length && this[j].isWhitespace()) j++
    if (j >= length || this[j] != ':') return null
    j++
    while (j < length && this[j].isWhitespace()) j++
    if (j >= length) return null
    if (this[j] == 'n') {
        // null
        return null
    }
    if (this[j] != '{') return null
    val start = j
    var depth = 0
    var inStr = false
    var esc = false
    while (j < length) {
        val ch = this[j]
        if (inStr) {
            when {
                esc -> esc = false
                ch == '\\' -> esc = true
                ch == '"' -> inStr = false
            }
            j++
            continue
        }
        when (ch) {
            '"' -> {
                inStr = true
                j++
            }
            '{' -> {
                depth++
                j++
            }
            '}' -> {
                depth--
                j++
                if (depth == 0) return substring(start, j)
            }
            else -> j++
        }
    }
    return null
}

private fun String.jsonObjectSlices(): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < length) {
        if (this[i] != '{') {
            i++
            continue
        }
        val start = i
        var depth = 0
        var inStr = false
        var esc = false
        while (i < length) {
            val ch = this[i]
            if (inStr) {
                when {
                    esc -> esc = false
                    ch == '\\' -> esc = true
                    ch == '"' -> inStr = false
                }
                i++
                continue
            }
            when (ch) {
                '"' -> {
                    inStr = true
                    i++
                }
                '{' -> {
                    depth++
                    i++
                }
                '}' -> {
                    depth--
                    i++
                    if (depth == 0) {
                        out += substring(start, i)
                        break
                    }
                }
                else -> i++
            }
        }
        if (depth != 0) break
    }
    return out
}
