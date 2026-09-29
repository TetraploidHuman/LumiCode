package com.lumicode.dshbridge

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Talks to an already-running `dsh web` Host. Never starts or restarts DSH. */
class DshHostClient(
    private val baseUrl: String = System.getenv("DSH_WEB_URL") ?: "http://127.0.0.1:3080",
    private val provider: String = System.getenv("DSH_PROVIDER") ?: "qwen35-250",
    private val model: String = System.getenv("DSH_MODEL") ?: "qwen35-9b",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val http = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 180_000
        }
    }

    private val cookieJar = AtomicReference<String?>(null)
    @Volatile var lastError: String? = null
        private set

    val configuredProvider: String get() = provider
    val configuredModel: String get() = model
    val hostBase: String get() = baseUrl

    suspend fun ensureAuth(): Boolean {
        if (cookieJar.get() != null) return true
        val token = resolveLaunchToken()
        if (token.isNullOrBlank()) {
            lastError = "no DSH launch token (set DSH_WEB_TOKEN or ~/.config/lumicode/dsh-web.token)"
            return false
        }
        return exchangeToken(token)
    }

    suspend fun health(): HealthSnapshot {
        if (!ensureAuth()) {
            return HealthSnapshot(ok = false, dsh = false, provider = provider, model = model, error = lastError)
        }
        return try {
            val catalog = rpc("session/modelCatalog", buildJsonObject { })
            val def = catalog["result"]?.jsonObject
                ?.get("value")?.jsonObject
                ?.get("default")?.jsonObject
            HealthSnapshot(
                ok = true,
                dsh = true,
                provider = def?.get("provider")?.jsonPrimitive?.contentOrNull ?: provider,
                model = def?.get("model")?.jsonPrimitive?.contentOrNull ?: model,
                error = null,
            )
        } catch (t: Throwable) {
            lastError = t.message
            HealthSnapshot(ok = false, dsh = false, provider = provider, model = model, error = t.message)
        }
    }

    /**
     * Create a session (or reuse), select model, prompt, poll until a turn response.
     * [cwd] is the absolute project directory for DSH filesystem tools.
     * When [jobId] is set, tool/think steps are published to [ChatProgress] for live polling.
     * [requireToolApproval] pauses on write/edit/bash until the client approves or cancels.
     * [writeScopes] optional path prefixes; tools outside scopes trigger cancel.
     */
    suspend fun chat(
        text: String,
        sessionId: String? = null,
        titleHint: String? = null,
        cwd: String? = null,
        jobId: String? = null,
        requireToolApproval: Boolean = false,
        writeScopes: List<String> = emptyList(),
    ): ChatResult {
        if (!ensureAuth()) {
            return ChatResult(ok = false, error = lastError ?: "unauthorized")
        }
        val promptText = text.trim()
        if (promptText.isEmpty()) {
            return ChatResult(ok = false, error = "empty prompt")
        }
        val projectCwd = cwd?.trim()?.takeIf { it.isNotEmpty() }
        val progressKey = jobId?.trim()?.takeIf { it.isNotEmpty() }
        if (progressKey != null) ChatProgress.begin(progressKey)

        return try {
            val sid = sessionId?.takeIf { it.isNotBlank() }
                ?: createSession(cwd = projectCwd)
            if (progressKey != null) ChatProgress.bindSession(progressKey, sid)
            selectModel(sid)
            if (!titleHint.isNullOrBlank()) {
                runCatching { rename(sid, titleHint.take(48)) }
            }
            val requestId = "req-" + UUID.randomUUID()
            prompt(sid, requestId, promptText)
            val steps = mutableListOf<TraceStep>()
            val reply = waitForReply(
                sessionId = sid,
                promptText = promptText,
                timeoutMs = 300_000,
                jobId = progressKey,
                requireToolApproval = requireToolApproval,
                writeScopes = writeScopes,
            ) { step ->
                steps += step
                if (progressKey != null) ChatProgress.append(progressKey, step)
            }
            if (progressKey != null) ChatProgress.finish(progressKey)
            when {
                progressKey != null && ChatProgress.isCancelled(progressKey) ->
                    ChatResult(
                        ok = false,
                        sessionId = sid,
                        error = "cancelled",
                        steps = steps,
                        cancelled = true,
                    )
                reply == null ->
                    ChatResult(
                        ok = false,
                        sessionId = sid,
                        error = "timed out waiting for DSH reply",
                        steps = steps,
                    )
                else ->
                    ChatResult(
                        ok = true,
                        sessionId = sid,
                        reply = reply,
                        provider = provider,
                        model = model,
                        steps = steps,
                    )
            }
        } catch (t: Throwable) {
            lastError = t.message
            if (progressKey != null) ChatProgress.finish(progressKey)
            ChatResult(ok = false, error = t.message ?: "dsh error")
        }
    }

    /** Ask DSH to cancel the active turn; also marks the bridge job cancelled. */
    suspend fun cancel(sessionId: String? = null, jobId: String? = null): CancelResult {
        val sid = sessionId?.takeIf { it.isNotBlank() }
            ?: jobId?.let { ChatProgress.snapshot(it).sessionId }
        val jid = jobId?.takeIf { it.isNotBlank() }
            ?: sid?.let { ChatProgress.requestCancelBySession(it) }
        if (!jid.isNullOrBlank()) ChatProgress.requestCancel(jid)
        if (sid.isNullOrBlank()) {
            return CancelResult(ok = false, error = "missing sessionId/jobId")
        }
        if (!ensureAuth()) {
            return CancelResult(ok = false, sessionId = sid, error = lastError ?: "unauthorized")
        }
        return try {
            rpc(
                "session/cancel",
                buildJsonObject {
                    putJsonObject("request") {
                        put("sessionId", sid)
                    }
                },
            )
            CancelResult(ok = true, sessionId = sid, jobId = jid)
        } catch (t: Throwable) {
            CancelResult(ok = false, sessionId = sid, jobId = jid, error = t.message)
        }
    }

    fun approveTool(jobId: String, callId: String?): Boolean {
        if (jobId.isBlank()) return false
        ChatProgress.approve(jobId, callId)
        return true
    }

    private suspend fun createSession(cwd: String? = null): String {
        val body = rpc(
            "session/create",
            buildJsonObject {
                putJsonObject("request") {
                    if (!cwd.isNullOrBlank()) put("cwd", cwd)
                }
            },
        )
        val sid = body.dig("result", "value", "sessionId")?.jsonPrimitive?.contentOrNull
            ?: error("session/create missing sessionId: $body")
        return sid
    }

    private suspend fun selectModel(sessionId: String) {
        rpc(
            "session/selectModel",
            buildJsonObject {
                putJsonObject("request") {
                    put("sessionId", sessionId)
                    put("provider", provider)
                    put("model", model)
                }
            },
        )
    }

    private suspend fun rename(sessionId: String, title: String) {
        rpc(
            "session/rename",
            buildJsonObject {
                putJsonObject("request") {
                    put("sessionId", sessionId)
                    put("title", title)
                }
            },
        )
    }

    private suspend fun prompt(sessionId: String, requestId: String, text: String) {
        val body = rpc(
            "session/prompt",
            buildJsonObject {
                putJsonObject("request") {
                    put("requestId", requestId)
                    put("sessionId", sessionId)
                    put("mode", "queue")
                    putJsonArray("content") {
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", text)
                            },
                        )
                    }
                }
            },
        )
        val ok = body.dig("result", "ok").asBool() == true
        val accepted = body.dig("result", "value", "accepted").asBool()
        if (!ok && accepted != true) {
            error("session/prompt rejected: $body")
        }
    }

    private suspend fun waitForReply(
        sessionId: String,
        promptText: String,
        timeoutMs: Long = 120_000,
        jobId: String?,
        requireToolApproval: Boolean,
        writeScopes: List<String>,
        onStep: (TraceStep) -> Unit,
    ): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastSeq = -1
        val seen = HashSet<String>()
        while (System.currentTimeMillis() < deadline) {
            if (jobId != null && ChatProgress.isCancelled(jobId)) {
                runCatching { cancelSessionQuiet(sessionId) }
                return null
            }
            // Block while supervisor reviews a dangerous tool.
            if (jobId != null && ChatProgress.isAwaitingApproval(jobId)) {
                delay(300)
                continue
            }
            val proj = rpc(
                "session/projections",
                buildJsonObject {
                    putJsonObject("request") {
                        put("sessionId", sessionId)
                    }
                },
            )
            val baseline = proj.dig("result", "value")?.jsonObject
            val asOf = baseline?.get("asOfSeq")?.jsonPrimitive?.intOrNull
            if (asOf != null && asOf > lastSeq) {
                try {
                    drainTrace(
                        sessionId = sessionId,
                        throughSeq = asOf,
                        afterSeq = lastSeq,
                        seen = seen,
                        jobId = jobId,
                        requireToolApproval = requireToolApproval,
                        writeScopes = writeScopes,
                        onStep = onStep,
                    )
                } catch (t: Throwable) {
                    System.err.println("dsh-bridge: drainTrace failed seq=$lastSeq..$asOf: ${t.message}")
                }
                lastSeq = asOf
            }
            if (jobId != null && ChatProgress.isCancelled(jobId)) {
                runCatching { cancelSessionQuiet(sessionId) }
                return null
            }
            val values = baseline?.get("values")?.jsonObject
            val outline = values?.get("turnOutline")?.jsonArray
            if (outline != null && outline.isNotEmpty()) {
                for (i in outline.size - 1 downTo 0) {
                    val turn = outline[i].jsonObject
                    val prompt = turn["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val response = turn["response"]?.jsonPrimitive?.contentOrNull
                    if (!response.isNullOrBlank()) {
                        if (prompt.contains(promptText.take(24)) || i == outline.size - 1) {
                            if (jobId != null) ChatProgress.setPartialReply(jobId, response)
                            return response
                        }
                    }
                }
            }
            delay(400)
        }
        return null
    }

    private suspend fun cancelSessionQuiet(sessionId: String) {
        rpc(
            "session/cancel",
            buildJsonObject {
                putJsonObject("request") {
                    put("sessionId", sessionId)
                }
            },
        )
    }

    /** Pull durable session events up to [throughSeq] and emit new tool/think/say steps. */
    private suspend fun drainTrace(
        sessionId: String,
        throughSeq: Int,
        afterSeq: Int,
        seen: MutableSet<String>,
        jobId: String?,
        requireToolApproval: Boolean,
        writeScopes: List<String>,
        onStep: (TraceStep) -> Unit,
    ) {
        val page = rpc(
            "session/page",
            buildJsonObject {
                putJsonObject("request") {
                    putJsonObject("address") {
                        put("kind", "session")
                        put("sessionId", sessionId)
                    }
                    put("throughSeq", throughSeq)
                    put("maxMessages", 120)
                }
            },
        )
        val records = page.dig("result", "value", "records")?.jsonArray ?: return
        val events = records.mapNotNull { rec ->
            val event = rec.jsonObject["event"]?.jsonObject ?: rec.jsonObject
            val seq = event["seq"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            seq to event
        }.sortedBy { it.first }
        for ((seq, event) in events) {
            if (seq <= afterSeq) continue
            emitTraceFromEvent(
                seq = seq,
                event = event,
                seen = seen,
                jobId = jobId,
                requireToolApproval = requireToolApproval,
                writeScopes = writeScopes,
                sessionId = sessionId,
                onStep = onStep,
            )
            if (jobId != null && ChatProgress.isCancelled(jobId)) return
            if (jobId != null && ChatProgress.isAwaitingApproval(jobId)) return
        }
    }

    private suspend fun emitTraceFromEvent(
        seq: Int,
        event: JsonObject,
        seen: MutableSet<String>,
        jobId: String?,
        requireToolApproval: Boolean,
        writeScopes: List<String>,
        sessionId: String,
        onStep: (TraceStep) -> Unit,
    ) {
        val type = event["type"]?.jsonPrimitive?.contentOrNull ?: return
        val data = event["data"]?.jsonObject ?: JsonObject(emptyMap())
        when (type) {
            "tool/call" -> {
                if (!seen.add("tc:$seq")) return
                val name = data["name"]?.jsonPrimitive?.contentOrNull ?: "tool"
                val args = data["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val callId = data["callId"]?.jsonPrimitive?.contentOrNull
                onStep(
                    TraceStep(
                        kind = "tool",
                        name = name,
                        text = compactToolArgs(args),
                        seq = seq,
                    ),
                )
                val dangerous = name in DANGEROUS_TOOLS
                val path = extractToolPath(args)
                val outOfScope = writeScopes.isNotEmpty() && path != null &&
                    writeScopes.none { scope -> pathBelongsToScope(path, scope) }
                if (outOfScope) {
                    onStep(
                        TraceStep(
                            kind = "approval",
                            name = "scope-violation",
                            text = "工具路径越界 · $path ∉ ${writeScopes.joinToString()}",
                            seq = seq,
                        ),
                    )
                    if (jobId != null) ChatProgress.requestCancel(jobId)
                    runCatching { cancelSessionQuiet(sessionId) }
                    return
                }
                if (dangerous && requireToolApproval && jobId != null &&
                    !ChatProgress.isCallApproved(jobId, callId)
                ) {
                    // Tool may already be executing on DSH; cancel turn and ask supervisor.
                    ChatProgress.setPendingApproval(
                        jobId,
                        PendingApproval(
                            id = callId ?: "seq-$seq",
                            toolName = name,
                            argsPreview = compactToolArgs(args),
                            callId = callId,
                        ),
                    )
                    onStep(
                        TraceStep(
                            kind = "approval",
                            name = name,
                            text = "等待上级批准 · ${compactToolArgs(args)}",
                            seq = seq,
                        ),
                    )
                    runCatching { cancelSessionQuiet(sessionId) }
                }
            }
            "tool/result" -> {
                if (!seen.add("tr:$seq")) return
                val message = data["message"]?.jsonObject
                val err = message?.get("isError").asBool() == true
                val body = contentBlocksText(message?.get("content")).ifBlank {
                    data["error"]?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull.orEmpty()
                }
                onStep(
                    TraceStep(
                        kind = "tool_result",
                        name = if (err) "error" else null,
                        text = body.take(500),
                        seq = seq,
                    ),
                )
            }
            "assistant/message", "assistant/attempt" -> {
                val message = data["message"]?.jsonObject ?: return
                val content = message["content"] as? JsonArray ?: return
                val hasToolCall = content.any {
                    it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "tool-call"
                }
                content.forEachIndexed { index, el ->
                    val block = el as? JsonObject ?: return@forEachIndexed
                    val blockType = block["type"]?.jsonPrimitive?.contentOrNull ?: return@forEachIndexed
                    val text = block["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    when (blockType) {
                        "reasoning" -> {
                            if (text.isBlank() || !seen.add("r:$seq:$index")) return@forEachIndexed
                            onStep(TraceStep(kind = "think", text = text.take(900), seq = seq))
                        }
                        "text" -> {
                            if (text.isBlank() || !seen.add("t:$seq:$index")) return@forEachIndexed
                            val kind = if (hasToolCall) "think" else "say"
                            onStep(TraceStep(kind = kind, text = text.take(900), seq = seq))
                            if (kind == "say" && jobId != null) {
                                ChatProgress.setPartialReply(jobId, text)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun exchangeToken(token: String): Boolean {
        val url = baseUrl.trimEnd('/') + "/?token=" + token
        val response: HttpResponse = http.get(url) {
            header(HttpHeaders.Accept, "text/html")
        }
        // Host returns 303 + Set-Cookie; do not follow — the follow would drop the cookie.
        val setCookie = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        val cookie = setCookie
            .asSequence()
            .map { it.substringBefore(';').trim() }
            .firstOrNull { it.startsWith("dsh-auth-") }
        val accepted = response.status == HttpStatusCode.SeeOther ||
            response.status == HttpStatusCode.Found ||
            response.status == HttpStatusCode.OK
        if (cookie == null || !accepted || response.status == HttpStatusCode.Unauthorized) {
            lastError = "token exchange failed (${response.status})"
            cookieJar.set(null)
            return false
        }
        cookieJar.set(cookie)
        lastError = null
        return true
    }

    private suspend fun rpc(method: String, args: JsonObject): JsonObject {
        val cookie = cookieJar.get() ?: error("not authenticated")
        val rpcId = UUID.randomUUID().toString()
        val envelope = buildJsonObject {
            put("type", "client-request")
            put("rpcId", rpcId)
            put("method", method)
            putJsonObject("payload") {
                put("args", args)
            }
        }
        val path = "/api/" + method
        val response = http.post(baseUrl.trimEnd('/') + path) {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.Origin, baseUrl.trimEnd('/'))
            contentType(ContentType.Application.Json)
            setBody(envelope)
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            cookieJar.set(null)
            error("DSH 401 — relaunch token expired; refresh DSH_WEB_TOKEN")
        }
        val text = response.bodyAsText()
        val parsed = json.parseToJsonElement(text).jsonObject
        val result = parsed["result"]?.jsonObject
        val okFlag = result?.get("ok").asBool()
        if (okFlag == false) {
            val message = result?.dig("error", "message")?.jsonPrimitive?.contentOrNull
                ?: text.take(240)
            error("$method failed: $message")
        }
        return parsed
    }

    fun close() {
        http.close()
    }
}

@Serializable
data class HealthSnapshot(
    val ok: Boolean,
    val dsh: Boolean,
    val provider: String,
    val model: String,
    val error: String? = null,
)

@Serializable
data class ChatRequest(
    val text: String,
    val sessionId: String? = null,
    val title: String? = null,
    /** Absolute workspace path; bound as DSH session cwd so file tools hit the right tree. */
    val cwd: String? = null,
    /** Client-generated id so Squad can poll /v1/progress/{jobId} while chat runs. */
    val jobId: String? = null,
    /** Pause (cancel turn) on write/edit/bash until /v1/approve. */
    val requireToolApproval: Boolean = false,
    /** Relative path prefixes the agent may write; violations cancel the turn. */
    val writeScopes: List<String> = emptyList(),
)

@Serializable
data class ChatResult(
    val ok: Boolean,
    val sessionId: String? = null,
    val reply: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val error: String? = null,
    val steps: List<TraceStep> = emptyList(),
    val cancelled: Boolean = false,
)

@Serializable
data class CancelRequest(
    val sessionId: String? = null,
    val jobId: String? = null,
)

@Serializable
data class CancelResult(
    val ok: Boolean,
    val sessionId: String? = null,
    val jobId: String? = null,
    val error: String? = null,
)

@Serializable
data class ApproveRequest(
    val jobId: String,
    val callId: String? = null,
)

private val DANGEROUS_TOOLS = setOf("write", "edit", "bash", "str_replace_editor")

private fun compactToolArgs(raw: String): String {
    val oneLine = raw.replace(Regex("\\s+"), " ").trim()
    return oneLine.take(420)
}

private fun extractToolPath(argsJson: String): String? {
    val m = Regex(""""(?:file_path|path)"\s*:\s*"((?:\\.|[^"\\])*)"""").find(argsJson)
        ?: return null
    return m.groupValues[1].replace("\\/", "/").replace("\\\\", "\\")
}

private fun pathBelongsToScope(path: String, scope: String): Boolean {
    val p = path.replace('\\', '/').trimStart('/')
    val s = scope.replace('\\', '/').trim().trim('/')
    if (s.isEmpty()) return true
    return p == s || p.startsWith("$s/") || p.endsWith("/$s") || p.contains("/$s/")
}

private fun contentBlocksText(content: JsonElement?): String {
    val arr = content as? JsonArray ?: return when (content) {
        is JsonPrimitive -> content.contentOrNull.orEmpty()
        else -> content?.toString().orEmpty()
    }
    return buildString {
        for (el in arr) {
            val block = el as? JsonObject ?: continue
            val t = block["text"]?.jsonPrimitive?.contentOrNull ?: continue
            if (isNotEmpty()) append('\n')
            append(t)
        }
    }.replace(Regex("\\s+"), " ").trim()
}

private fun JsonObject.dig(vararg path: String): JsonElement? {
    var cur: JsonElement? = this
    for (key in path) {
        cur = (cur as? JsonObject)?.get(key) ?: return null
    }
    return cur
}

private fun JsonElement?.asBool(): Boolean? = when (this) {
    is JsonPrimitive -> this.contentOrNull?.toBooleanStrictOrNull()
        ?: when (this.content) {
            "true" -> true
            "false" -> false
            else -> null
        }
    else -> null
}

/**
 * Resolve the one-shot launch token for the *currently running* dsh-web process.
 * Prefer env / file; fall back to journalctl of lumicode-compatible user unit `dsh-web.service`.
 */
fun resolveLaunchToken(): String? {
    System.getenv("DSH_WEB_TOKEN")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    val home = System.getProperty("user.home")
    val file = File(home, ".config/lumicode/dsh-web.token")
    if (file.isFile) {
        file.readText().trim().takeIf { it.isNotEmpty() }?.let { return it }
    }

    return discoverTokenFromJournal()
}

fun discoverTokenFromJournal(): String? {
    return try {
        val proc = ProcessBuilder(
            "journalctl", "--user", "-u", "dsh-web.service", "--no-pager", "-o", "cat",
        ).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        // last matching line for the live process
        Regex("""dsh web: http://[^?\s]+\?token=([A-Za-z0-9_-]+)""")
            .findAll(out)
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
    } catch (_: Throwable) {
        null
    }
}

/** Persist discovered token so future starts don't depend on journal retention. */
fun persistToken(token: String) {
    val dir = File(System.getProperty("user.home"), ".config/lumicode")
    dir.mkdirs()
    File(dir, "dsh-web.token").writeText(token.trim() + "\n")
}
