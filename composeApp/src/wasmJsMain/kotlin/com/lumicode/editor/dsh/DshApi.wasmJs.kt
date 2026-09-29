package com.lumicode.editor.dsh

import com.lumicode.editor.workspace.appBasePath
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.suspendCancellableCoroutine

@JsFun(
    """
    (url, body) => fetch(url, {
        method: body == null ? 'GET' : 'POST',
        headers: body == null ? undefined : { 'Content-Type': 'application/json' },
        body: body ?? undefined
    }).then(r => r.text())
    """,
)
private external fun fetchTextJs(url: String, body: String?): Promise<JsString>

private suspend fun Promise<JsString>.awaitText(): String =
    suspendCancellableCoroutine { cont ->
        then(
            onFulfilled = { value ->
                cont.resume(value.toString())
                null
            },
            onRejected = { err ->
                cont.resumeWithException(IllegalStateException(err?.toString() ?: "fetch failed"))
                null
            },
        )
    }

class WasmDshBackend : DshBackend {
    override suspend fun health(): DshHealth =
        parseDshHealth(fetchTextJs("$BASE/v1/health", null).awaitText())

    override suspend fun chat(
        text: String,
        sessionId: String?,
        title: String?,
        cwd: String?,
        jobId: String?,
        requireToolApproval: Boolean,
        writeScopes: List<String>,
    ): DshChatResult {
        val body = buildDshChatBody(
            text, sessionId, title, cwd, jobId, requireToolApproval, writeScopes,
        )
        return parseDshChat(fetchTextJs("$BASE/v1/chat", body).awaitText())
    }

    override suspend fun progress(jobId: String): DshProgress =
        parseDshProgress(fetchTextJs("$BASE/v1/progress/${encodeURIComponent(jobId)}", null).awaitText())

    override suspend fun cancel(sessionId: String?, jobId: String?): DshCancelResult {
        val body = buildString {
            append('{')
            var first = true
            if (!sessionId.isNullOrBlank()) {
                append("\"sessionId\":")
                append(jsonString(sessionId))
                first = false
            }
            if (!jobId.isNullOrBlank()) {
                if (!first) append(',')
                append("\"jobId\":")
                append(jsonString(jobId))
            }
            append('}')
        }
        return parseDshCancel(fetchTextJs("$BASE/v1/cancel", body).awaitText())
    }

    override suspend fun approve(jobId: String, callId: String?): Boolean {
        val body = buildString {
            append("{\"jobId\":")
            append(jsonString(jobId))
            if (!callId.isNullOrBlank()) {
                append(",\"callId\":")
                append(jsonString(callId))
            }
            append('}')
        }
        return fetchTextJs("$BASE/v1/approve", body).awaitText().contains("\"ok\":true")
    }

    private companion object {
        val BASE: String get() = "${appBasePath()}/api/dsh"
    }
}

@JsFun("(s) => encodeURIComponent(s)")
private external fun encodeURIComponent(s: String): String

fun installWasmDshBackend() {
    DshApi.backend = WasmDshBackend()
}
