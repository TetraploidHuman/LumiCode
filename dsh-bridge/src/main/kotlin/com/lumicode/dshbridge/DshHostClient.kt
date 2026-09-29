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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
     * Create a session (or reuse), select qwen35-250 / qwen35-9b, prompt, poll until a turn response.
     */
    suspend fun chat(text: String, sessionId: String? = null, titleHint: String? = null): ChatResult {
        if (!ensureAuth()) {
            return ChatResult(ok = false, error = lastError ?: "unauthorized")
        }
        val promptText = text.trim()
        if (promptText.isEmpty()) {
            return ChatResult(ok = false, error = "empty prompt")
        }

        return try {
            val sid = sessionId?.takeIf { it.isNotBlank() } ?: createSession()
            selectModel(sid)
            if (!titleHint.isNullOrBlank()) {
                runCatching { rename(sid, titleHint.take(48)) }
            }
            val requestId = "req-" + UUID.randomUUID()
            prompt(sid, requestId, promptText)
            val reply = waitForReply(sid, promptText)
            if (reply == null) {
                ChatResult(ok = false, sessionId = sid, error = "timed out waiting for DSH reply")
            } else {
                ChatResult(ok = true, sessionId = sid, reply = reply, provider = provider, model = model)
            }
        } catch (t: Throwable) {
            lastError = t.message
            ChatResult(ok = false, error = t.message ?: "dsh error")
        }
    }

    private suspend fun createSession(): String {
        val body = rpc(
            "session/create",
            buildJsonObject {
                putJsonObject("request") { }
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

    private suspend fun waitForReply(sessionId: String, promptText: String, timeoutMs: Long = 120_000): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val proj = rpc(
                "session/projections",
                buildJsonObject {
                    putJsonObject("request") {
                        put("sessionId", sessionId)
                    }
                },
            )
            val values = proj.dig("result", "value", "values")?.jsonObject
            val outline = values?.get("turnOutline")?.jsonArray
            if (outline != null && outline.isNotEmpty()) {
                for (i in outline.size - 1 downTo 0) {
                    val turn = outline[i].jsonObject
                    val prompt = turn["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val response = turn["response"]?.jsonPrimitive?.contentOrNull
                    if (!response.isNullOrBlank()) {
                        if (prompt.contains(promptText.take(24)) || i == outline.size - 1) {
                            return response
                        }
                    }
                }
            }
            delay(700)
        }
        return null
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
)

@Serializable
data class ChatResult(
    val ok: Boolean,
    val sessionId: String? = null,
    val reply: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val error: String? = null,
)

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
