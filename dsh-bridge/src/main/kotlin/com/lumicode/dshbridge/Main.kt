package com.lumicode.dshbridge

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BridgeInfo(
    val service: String,
    val upstream: String,
    val provider: String,
    val model: String,
)

fun main() {
    val host = System.getenv("LUMICODE_DSH_BRIDGE_HOST") ?: "127.0.0.1"
    val port = System.getenv("LUMICODE_DSH_BRIDGE_PORT")?.toIntOrNull() ?: 8098
    val client = DshHostClient()

    // Warm auth + cache token file (does not restart dsh).
    val token = resolveLaunchToken()
    if (!token.isNullOrBlank()) {
        persistToken(token)
        println("dsh-bridge: launch token ready (${token.take(6)}…)")
    } else {
        println("dsh-bridge: WARNING — no launch token yet; /v1/health will explain")
    }

    println("dsh-bridge: binding http://$host:$port/")
    println("dsh-bridge: upstream ${client.hostBase}  model ${client.configuredProvider}/${client.configuredModel}")

    Runtime.getRuntime().addShutdownHook(Thread { client.close() })

    embeddedServer(Netty, host = host, port = port) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    isLenient = true
                },
            )
        }
        routing {
            get("/v1/health") {
                call.respond(client.health())
            }
            get("/v1/progress/{jobId}") {
                val jobId = call.parameters["jobId"].orEmpty()
                call.respond(ChatProgress.snapshot(jobId))
            }
            post("/v1/chat") {
                val req = call.receive<ChatRequest>()
                val result = client.chat(req.text, req.sessionId, req.title, req.cwd, req.jobId)
                call.respond(if (result.ok) HttpStatusCode.OK else HttpStatusCode.BadGateway, result)
            }
            get("/") {
                call.respond(
                    BridgeInfo(
                        service = "lumicode-dsh-bridge",
                        upstream = client.hostBase,
                        provider = client.configuredProvider,
                        model = client.configuredModel,
                    ),
                )
            }
        }
    }.start(wait = true)
}
