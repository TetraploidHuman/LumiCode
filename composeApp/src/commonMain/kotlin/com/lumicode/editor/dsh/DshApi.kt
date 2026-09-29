package com.lumicode.editor.dsh

/**
 * Browser → `/api/dsh/…` → lumicode-dsh-bridge → dsh-web (qwen35-250 / qwen35-9b).
 */
data class DshHealth(
    val ok: Boolean,
    val provider: String? = null,
    val model: String? = null,
    val error: String? = null,
)

data class DshChatResult(
    val ok: Boolean,
    val reply: String? = null,
    val sessionId: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val error: String? = null,
)

interface DshBackend {
    suspend fun health(): DshHealth
    suspend fun chat(text: String, sessionId: String? = null, title: String? = null): DshChatResult
}

object DshApi {
    var backend: DshBackend? = null

    suspend fun health(): DshHealth =
        backend?.health() ?: DshHealth(ok = false, error = "DSH backend not installed")

    suspend fun chat(text: String, sessionId: String? = null, title: String? = null): DshChatResult =
        backend?.chat(text, sessionId, title)
            ?: DshChatResult(ok = false, error = "DSH backend not installed")
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
    )

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

private fun String.boolField(key: String): Boolean {
    val re = Regex(""""$key"\s*:\s*(true|false)""")
    return re.find(this)?.groupValues?.getOrNull(1) == "true"
}

private fun String.stringField(key: String): String? {
    val re = Regex(""""$key"\s*:\s*(null|"([^"\\]|\\.)*")""")
    val m = re.find(this) ?: return null
    val raw = m.groupValues[1]
    if (raw == "null") return null
    return raw.removeSurrounding("\"").replace("\\n", "\n").replace("\\\"", "\"")
}
