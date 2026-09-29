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
            if (!jobId.isNullOrBlank()) {
                append(",\"jobId\":")
                append(jsonString(jobId))
            }
            append('}')
        }
        return parseDshChat(post("$base/v1/chat", body))
    }

    override suspend fun progress(jobId: String): DshProgress {
        val enc = URLEncoder.encode(jobId, StandardCharsets.UTF_8)
        return parseDshProgress(get("$base/v1/progress/$enc"))
    }

    private fun get(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }

    private fun post(url: String, body: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(320))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }
}

fun installDesktopDshBackend() {
    DshApi.backend = DesktopDshBackend()
}
