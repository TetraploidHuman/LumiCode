package com.lumicode.editor.dsh

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class DesktopDshBackend(
    private val base: String = System.getenv("LUMICODE_DSH_BRIDGE")?.let { "http://$it" }
        ?: "http://127.0.0.1:8098",
) : DshBackend {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override suspend fun health(): DshHealth =
        parseDshHealth(get("$base/v1/health"))

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
        return parseDshChat(post("$base/v1/chat", body, timeoutSec = 320))
    }

    override suspend fun progress(jobId: String): DshProgress {
        val enc = URLEncoder.encode(jobId, StandardCharsets.UTF_8)
        return parseDshProgress(get("$base/v1/progress/$enc"))
    }

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
        return parseDshCancel(post("$base/v1/cancel", body, timeoutSec = 30))
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
        val raw = post("$base/v1/approve", body, timeoutSec = 30)
        return raw.contains("\"ok\":true")
    }

    private fun get(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }

    private fun post(url: String, body: String, timeoutSec: Long): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSec))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }
}

fun installDesktopDshBackend() {
    DshApi.backend = DesktopDshBackend()
}
