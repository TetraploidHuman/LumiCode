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
    ): DshChatResult {
        val body = buildString {
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
            append('}')
        }
        return parseDshChat(fetchTextJs("$BASE/v1/chat", body).awaitText())
    }

    private companion object {
        val BASE: String get() = "${appBasePath()}/api/dsh"
    }
}

fun installWasmDshBackend() {
    DshApi.backend = WasmDshBackend()
}
