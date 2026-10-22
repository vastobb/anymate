package com.anymate.studio.mcp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.anymate.studio.Prefs
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.engine.ExportManager
import com.anymate.studio.engine.Rasterizer
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Embedded Model Context Protocol server (Streamable HTTP transport).
 *
 * Artboards are rendered by the app itself and returned as snapshot images;
 * every editing tool maps to the same engine the artist touches. No root, no
 * companion daemon, no cloud requirement.
 */
class McpServer(
    private val context: Context,
    private val engine: AnimationEngine,
    private val registry: ToolRegistry,
    private val exporter: ExportManager,
    private val prefs: Prefs
) : NanoHTTPD("0.0.0.0", prefs.mcpPort.coerceIn(1024, 65535)) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionId: String = UUID.randomUUID().toString()
    var token: String = prefs.mcpToken
        private set

    var running by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    var requestCount by mutableIntStateOf(0)
        private set
    var toolCallCount by mutableIntStateOf(0)
        private set

    val port: Int get() = prefs.mcpPort.coerceIn(1024, 65535)

    val localIp: String
        get() = try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull()?.hostAddress ?: "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }

    val baseUrl: String get() = "http://$localIp:$port"
    val mcpUrl: String get() = "$baseUrl/mcp"
    val loopbackUrl: String get() = "http://127.0.0.1:$port/mcp"

    fun startServer(): Boolean {
        if (running) return true
        return try {
            start(5000, true)
            running = true
            lastError = null
            registry.serverBaseUrl = baseUrl
            registry.accessToken = token
            mainHandler.post {
                engine.logTool(
                    "mcp_start",
                    "Server live at $mcpUrl",
                    true
                )
            }
            true
        } catch (t: Throwable) {
            lastError = t.message ?: "Unable to start MCP server"
            running = false
            false
        }
    }

    fun stopServer() {
        if (!running) return
        try {
            stop()
        } catch (_: Throwable) {
        }
        running = false
    }

    fun updateToken(newToken: String) {
        token = newToken
        registry.accessToken = newToken
    }

    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val uri = session.uri ?: "/"
        if (session.method == NanoHTTPD.Method.OPTIONS) {
            return withCors(newFixedLengthResponse(NanoHTTPD.Response.Status.NO_CONTENT, "text/plain", ""))
        }
        return try {
            when {
                uri == "/mcp" -> withCors(handleMcp(session))
                uri == "/health" -> withCors(handleHealth())
                uri == "/" -> withCors(handleLanding())
                uri == "/snapshot.png" -> withCors(handleSnapshotPng(session))
                uri.startsWith("/exports/") -> withCors(handleExport(session, uri))
                else -> withCors(
                    newFixedLengthResponse(
                        NanoHTTPD.Response.Status.NOT_FOUND,
                        "text/plain",
                        "AnyMate MCP server. Try /health or POST /mcp."
                    )
                )
            }
        } catch (t: Throwable) {
            withCors(
                newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "application/json",
                    """{"error":"${escape(t.message ?: "server error")}"}"""
                )
            )
        }
    }

    // ---- HTTP endpoints ---------------------------------------------------
    private fun handleHealth(): NanoHTTPD.Response {
        val payload = buildJsonObject {
            put("name", "AnyMate Studio")
            put("status", "ok")
            put("version", "1.0.0")
            put("frames", engine.frames.size)
            put("fps", engine.fps)
            put("mcp", "/mcp")
            put("auth", if (token.isBlank()) "disabled" else "bearer")
        }
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", payload.toString())
    }

    private fun handleLanding(): NanoHTTPD.Response {
        val html = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>AnyMate MCP</title>
            <style>
            :root{color-scheme:dark}body{font:15px ui-sans-serif,system-ui;background:#0c0b0f;color:#f7f4ff;margin:0;display:grid;place-items:center;min-height:100vh}
            .card{max-width:540px;padding:40px;border:1px solid rgba(255,255,255,.1);border-radius:24px;background:linear-gradient(160deg,#1b1724,#121016)}
            h1{margin:0 0 6px;font-size:28px;letter-spacing:-.02em}.tag{color:#a78bfa;font-size:11px;letter-spacing:.18em;text-transform:uppercase}
            code{background:#0c0b0f;border:1px solid rgba(255,255,255,.08);padding:3px 7px;border-radius:6px;color:#22d3ee}
            p{color:#b0a9bf;line-height:1.6}.row{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid rgba(255,255,255,.06)}
            </style></head><body><div class="card">
            <div class="tag">Model Context Protocol</div><h1>AnyMate Studio</h1>
            <p>A 2D animation studio on this phone. Connect an AI agent to <code>${'$'}{baseUrl}/mcp</code>.</p>
            <div class="row"><span>Status</span><b style="color:#34d399">● Running</b></div>
            <div class="row"><span>Frames</span><b>${engine.frames.size}</b></div>
            <div class="row"><span>FPS</span><b>${engine.fps}</b></div>
            <div class="row"><span>Auth</span><b>${if (token.isBlank()) "off" else "Bearer token"}</b></div>
            <p style="margin-top:22px;font-size:12px">The token is shown in AnyMate → Settings → MCP Server. Snapshots: <code>/snapshot.png</code> · Health: <code>/health</code></p>
            </div></body></html>
        """.trimIndent()
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html", html)
    }

    private fun handleSnapshotPng(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (!authorized(session)) return unauthorized()
        val bytes = renderSnapshotBytes(engine, 1024)
        val response = newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "image/png",
            bytes.inputStream(), bytes.size.toLong()
        )
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun handleExport(session: NanoHTTPD.IHTTPSession, uri: String): NanoHTTPD.Response {
        if (!authorized(session)) return unauthorized()
        val name = File(uri.removePrefix("/exports/").substringBefore('?')).name
        val file = File(exporter.exportsDir, name)
        if (!file.exists() || !file.isFile) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Export not found.")
        }
        val mime = when (file.extension.lowercase()) {
            "gif" -> "image/gif"
            "png" -> "image/png"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
        val response = newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, mime, FileInputStream(file), file.length()
        )
        response.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
        return response
    }

    // ---- MCP JSON-RPC -----------------------------------------------------
    private fun handleMcp(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (!authorized(session)) return unauthorized()
        return when (session.method) {
            NanoHTTPD.Method.GET -> {
                val response = newFixedLengthResponse(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "text/plain",
                    "This AnyMate server is stateless. Send JSON-RPC over POST /mcp."
                )
                response.addHeader("Allow", "POST, DELETE, OPTIONS")
                response
            }
            NanoHTTPD.Method.DELETE -> {
                newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", """{"ok":true}""")
            }
            NanoHTTPD.Method.POST -> handlePost(session)
            else -> {
                val response = newFixedLengthResponse(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Use POST."
                )
                response.addHeader("Allow", "POST, DELETE, OPTIONS")
                response
            }
        }
    }

    private fun handlePost(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (contentLength > 8L * 1024 * 1024) {
            return newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST, "application/json",
                """{"error":"Request too large. Keep batches under 8 MB."}"""
            )
        }
        val files = HashMap<String, String>()
        var body = ""
        if (contentLength in 1..(8L * 1024 * 1024)) {
            body = readBody(session, contentLength)
        }
        if (body.isBlank()) {
            try {
                session.parseBody(files)
            } catch (t: Throwable) {
                // fall through to the empty-body response
            }
            body = files["postData"] ?: ""
        }
        if (body.isBlank()) {
            return newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST, "application/json",
                """{"error":"Empty request body."}"""
            )
        }
        val parsed = try {
            json.parseToJsonElement(body)
        } catch (t: Throwable) {
            return jsonRpcHttpError(-32700, "Parse error", JsonNull)
        }

        mainHandler.post {
            requestCount++
            if (body.contains("tools/call")) toolCallCount++
        }

        val accept = session.headers["accept"] ?: ""
        return if (parsed is JsonArray) {
            val responses = ArrayList<JsonElement>()
            for (element in parsed) {
                if (element is JsonObject) {
                    handleRpc(element)?.let { responses.add(it) }
                } else {
                    responses.add(errorResponse(JsonNull, -32600, "Invalid Request"))
                }
            }
            if (responses.isEmpty()) {
                accepted()
            } else {
                val array = JsonArray(responses)
                if (wantsSseOnly(accept)) sseResponse(array.toString()) else jsonResponse(array.toString())
            }
        } else if (parsed is JsonObject) {
            val response = handleRpc(parsed)
            if (response == null) accepted()
            else if (wantsSseOnly(accept)) sseResponse(response.toString())
            else jsonResponse(response.toString())
        } else {
            jsonRpcHttpError(-32600, "Invalid Request", JsonNull)
        }
    }

    private fun handleRpc(request: JsonObject): JsonObject? {
        val id = request["id"]
        val method = request["method"]?.let {
            runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
        }
        if (method == null) {
            return if (id == null) null else errorResponse(id, -32600, "Invalid Request: method missing")
        }
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())
        if (id == null) {
            // Notifications require no response.
            return null
        }
        return try {
            val result: JsonElement = when (method) {
                "initialize" -> initializeResult(params)
                "ping" -> buildJsonObject { }
                "tools/list" -> buildJsonObject { put("tools", registry.toolSchemas) }
                "tools/call" -> callTool(params)
                "resources/list" -> resourcesList()
                "resources/templates/list" -> buildJsonObject { put("resourceTemplates", JsonArray(emptyList())) }
                "resources/read" -> readResource(params)
                "prompts/list" -> promptsList()
                "prompts/get" -> promptGet(params)
                "logging/setLevel" -> buildJsonObject { }
                "completion/complete" -> buildJsonObject {
                    put("completion", buildJsonObject {
                        put("values", JsonArray(emptyList()))
                        put("hasMore", false)
                    })
                }
                else -> return errorResponse(id, -32601, "Method not found: $method")
            }
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("result", result)
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            errorResponse(id, -32603, "Internal error: ${t.message ?: t::class.simpleName}")
        }
    }

    private fun initializeResult(params: JsonObject): JsonObject {
        val requested = params["protocolVersion"]?.jsonPrimitive?.contentOrNull
        val supported = listOf("2025-06-18", "2025-03-26", "2024-11-05")
        val version = if (requested != null && supported.contains(requested)) requested else "2025-06-18"
        return buildJsonObject {
            put("protocolVersion", version)
            put("capabilities", buildJsonObject {
                put("tools", buildJsonObject { put("listChanged", false) })
                put("resources", buildJsonObject { put("subscribe", false); put("listChanged", false) })
                put("prompts", buildJsonObject { put("listChanged", false) })
            })
            put("serverInfo", buildJsonObject {
                put("name", "anymate")
                put("title", "AnyMate Studio")
                put("version", "1.0.0")
                put("websiteUrl", "https://github.com/vastobb/anymate")
            })
            put(
                "instructions",
                """
                AnyMate is a live 2D animation studio running on an Android phone. You and the artist share one canvas.
                Coordinates are project pixels: (0,0) is top-left; x grows right, y grows down.
                Workflow:
                1. Call anymate_get_state to learn canvas size, fps, frames, layers.
                2. Call anymate_snapshot to see the current frame.
                3. Draw with anymate_draw_stroke / draw_shape / draw_polygon / draw_text / fill.
                4. Add or duplicate frames and draw each pose. For high-speed sequences (60+ frames), use anymate_batch to reduce HTTP overhead.
                5. Call anymate_play so the artist immediately sees the loop.
                Every mutation is visible live. Prefer 1-4 simple shapes per frame over hugely dense strokes. Keep art inside the canvas. Use layers (0 is bottom) for backgrounds, characters and FX. If a layer is locked, unlock with anymate_set_layer first.
                The built-in offline director is available through anymate_generate.
                """.trimIndent()
            )
        }
    }

    private fun callTool(params: JsonObject): JsonObject {
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("tools/call requires params.name")
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val result = runBlocking {
            withContext(Dispatchers.Main) {
                registry.execute(name, args)
            }
        }
        val content = buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", result.text)
            })
            result.images.forEach { image ->
                add(buildJsonObject {
                    put("type", "image")
                    put("data", Base64.encodeToString(image.data, Base64.NO_WRAP))
                    put("mimeType", image.mimeType)
                })
            }
        }
        return buildJsonObject {
            put("content", content)
            put("isError", result.isError)
        }
    }

    private fun resourcesList(): JsonObject {
        val resources = buildJsonArray {
            add(buildJsonObject {
                put("uri", "anymate://preview/current")
                put("name", "Current canvas preview")
                put("description", "Live PNG snapshot of the frame on the artist's screen.")
                put("mimeType", "image/png")
            })
            add(buildJsonObject {
                put("uri", "anymate://project/state")
                put("name", "Project state")
                put("description", "JSON project, frame and layer metadata.")
                put("mimeType", "application/json")
            })
        }
        return buildJsonObject { put("resources", resources) }
    }

    private fun readResource(params: JsonObject): JsonObject {
        val uri = params["uri"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("resources/read requires params.uri")
        return when (uri) {
            "anymate://preview/current" -> {
                val bytes = renderSnapshotBytes(engine, 1080)
                buildJsonObject {
                    put("contents", buildJsonArray {
                        add(buildJsonObject {
                            put("uri", uri)
                            put("mimeType", "image/png")
                            put("blob", Base64.encodeToString(bytes, Base64.NO_WRAP))
                        })
                    })
                }
            }
            "anymate://project/state" -> {
                val state = runBlocking {
                    withContext(Dispatchers.Main) {
                        buildJsonObject {
                            put("project", engine.projectName)
                            put("width", engine.canvasWidth)
                            put("height", engine.canvasHeight)
                            put("fps", engine.fps)
                            put("frames", engine.frames.size)
                            put("current_frame", engine.currentIndex)
                            put("layers", JsonArray(engine.layers.map { layer ->
                                buildJsonObject {
                                    put("name", layer.name)
                                    put("visible", layer.visible)
                                    put("locked", layer.locked)
                                    put("opacity", layer.opacity)
                                }
                            }))
                        }
                    }
                }
                buildJsonObject {
                    put("contents", buildJsonArray {
                        add(buildJsonObject {
                            put("uri", uri)
                            put("mimeType", "application/json")
                            put("text", state.toString())
                        })
                    })
                }
            }
            else -> throw IllegalArgumentException("Unknown resource: $uri")
        }
    }

    private fun promptsList(): JsonObject {
        val prompts = buildJsonArray {
            add(buildJsonObject {
                put("name", "direct-an-animation")
                put("title", "Direct an animation")
                put("description", "Turn an idea into a live, looping animation using AnyMate's drawing tools.")
                put("arguments", buildJsonArray {
                    add(buildJsonObject {
                        put("name", "idea")
                        put("description", "What should move? e.g. a neon fish swimming")
                        put("required", true)
                    })
                })
            })
            add(buildJsonObject {
                put("name", "polish-the-scene")
                put("title", "Polish the current scene")
                put("description", "Inspect the current frame and improve composition, colour and motion.")
            })
        }
        return buildJsonObject { put("prompts", prompts) }
    }

    private fun promptGet(params: JsonObject): JsonObject {
        val name = params["name"]?.jsonPrimitive?.contentOrNull
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val idea = args["idea"]?.jsonPrimitive?.contentOrNull ?: "a glowing paper plane looping through clouds"
        val text = when (name) {
            "direct-an-animation" -> """
                Create a live 2D animation in AnyMate from this idea: "$idea".
                First call anymate_get_state and anymate_snapshot. Then choose 24 or 30 fps.
                Build a seamless loop of 24-48 frames with anymate_batch. Keep each frame to a few clean shapes/strokes,
                use layers for background / subject / FX, and add onion-skin-friendly key poses. Finish with anymate_play.
                Tell me what you made and the frame range.
            """.trimIndent()
            "polish-the-scene" -> """
                Inspect the AnyMate canvas with anymate_snapshot. Improve it while preserving the artist's intent:
                refine spacing, contrast, colour harmony and timing. Use undo-safe batches. Then play the loop.
            """.trimIndent()
            else -> throw IllegalArgumentException("Unknown prompt: $name")
        }
        return buildJsonObject {
            put("description", "AnyMate directing prompt")
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    })
                })
            })
        }
    }

    // ---- Helpers ----------------------------------------------------------
    private fun renderSnapshotBytes(engine: AnimationEngine, maxSize: Int): ByteArray {
        val snapshot = runBlocking { withContext(Dispatchers.Main) { exporter.snapshotOnMain(engine) } }
        val frame = snapshot.frames.getOrNull(snapshot.currentIndex) ?: snapshot.frames.first()
        val scale = maxSize.toFloat() / max(snapshot.width, snapshot.height)
        val w = (snapshot.width * scale).roundToInt().coerceAtLeast(2)
        val h = (snapshot.height * scale).roundToInt().coerceAtLeast(2)
        val bitmap = runBlocking(Dispatchers.Default) {
            Rasterizer.renderFrame(
                frame, snapshot.layers, snapshot.width, snapshot.height,
                snapshot.background, w, h, includeBackground = true
            )
        }
        val png = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, png)
        bitmap.recycle()
        return png.toByteArray()
    }

    private fun readBody(session: NanoHTTPD.IHTTPSession, length: Long): String {
        return try {
            val buffer = ByteArray(8192)
            val out = java.io.ByteArrayOutputStream()
            var remaining = length
            while (remaining > 0) {
                val read = session.inputStream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
            out.toString("UTF-8")
        } catch (_: Throwable) {
            ""
        }
    }

    private fun authorized(session: NanoHTTPD.IHTTPSession): Boolean {
        if (token.isBlank()) return true
        val header = session.headers["authorization"] ?: session.headers["x-api-key"]
        val query = session.parameters["token"]?.firstOrNull()
        val provided = when {
            header?.startsWith("Bearer ", ignoreCase = true) == true ->
                header.substringAfter(' ', "").trim()
            !header.isNullOrBlank() -> header.trim()
            else -> query
        }
        return provided == token
    }

    private fun unauthorized(): NanoHTTPD.Response {
        val response = newFixedLengthResponse(
            NanoHTTPD.Response.Status.UNAUTHORIZED, "application/json",
            """{"error":"Unauthorized. Send Authorization: Bearer <token from AnyMate settings>."}"""
        )
        response.addHeader("WWW-Authenticate", "Bearer realm=\"anymate\"")
        return response
    }

    private fun withCors(response: NanoHTTPD.Response): NanoHTTPD.Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        response.addHeader(
            "Access-Control-Allow-Headers",
            "Authorization, Content-Type, Mcp-Session-Id, MCP-Protocol-Version, X-API-Key"
        )
        response.addHeader("Access-Control-Expose-Headers", "Mcp-Session-Id")
        response.addHeader("Mcp-Session-Id", sessionId)
        return response
    }

    private fun jsonResponse(payload: String): NanoHTTPD.Response {
        val response = newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", payload)
        response.addHeader("MCP-Protocol-Version", "2025-06-18")
        return response
    }

    private fun sseResponse(payload: String): NanoHTTPD.Response {
        val response = newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "text/event-stream",
            "event: message\ndata: $payload\n\n"
        )
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")
        response.addHeader("MCP-Protocol-Version", "2025-06-18")
        return response
    }

    private fun accepted(): NanoHTTPD.Response =
        newFixedLengthResponse(NanoHTTPD.Response.Status.ACCEPTED, "text/plain", "")

    private fun wantsSseOnly(accept: String): Boolean {
        val lower = accept.lowercase()
        return lower.contains("text/event-stream") && !lower.contains("application/json") &&
            !lower.contains("*/*")
    }

    private fun jsonRpcHttpError(code: Int, message: String, id: JsonElement): NanoHTTPD.Response {
        val response = newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "application/json",
            errorResponse(id, code, message).toString()
        )
        return response
    }

    private fun errorResponse(id: JsonElement?, code: Int, message: String): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
            })
        }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
