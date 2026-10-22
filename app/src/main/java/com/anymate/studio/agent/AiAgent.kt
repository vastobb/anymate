package com.anymate.studio.agent

import android.util.Base64
import com.anymate.studio.Prefs
import com.anymate.studio.mcp.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bring-your-own-model agent. Works with any OpenAI-compatible
 * /chat/completions endpoint (OpenAI, OpenRouter, Groq, Ollama, LM Studio...).
 * Every tool invocation flows through the same ToolRegistry as MCP, so the
 * phone canvas updates live.
 */
class AiAgent(
    private val prefs: Prefs,
    private val registry: ToolRegistry
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .build()

    data class RunResult(
        val text: String,
        val toolCalls: Int,
        val steps: Int
    )

    suspend fun run(
        prompt: String,
        onStep: (String) -> Unit = {}
    ): RunResult = withContext(Dispatchers.IO) {
        val messages = ArrayList<JsonElement>()
        messages.add(systemMessage(SYSTEM_PROMPT))
        messages.add(userMessage(prompt))
        var toolCallTotal = 0
        var imagesSent = 0
        val imageQueue = ArrayList<com.anymate.studio.mcp.ToolImage>()
        val maxSteps = 18

        for (step in 0 until maxSteps) {
            onStep(if (step == 0) "Thinking" else "Working")
            val payload = buildJsonObject {
                put("model", prefs.agentModel)
                put("messages", JsonArray(messages))
                put("tools", toolDefinitions())
                put("tool_choice", "auto")
                put("temperature", 0.72)
                put("max_tokens", 4096)
            }
            val responseText = post(payload.toString())
            val root = json.parseToJsonElement(responseText).jsonObject
            root["error"]?.let { error ->
                val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                    ?: error.toString()
                throw IOException("Model endpoint error: $message")
            }
            val choices = root["choices"]?.jsonArray
                ?: throw IOException("Model returned no choices: ${responseText.take(300)}")
            if (choices.isEmpty()) throw IOException("Model returned an empty response.")
            val message = choices[0].jsonObject["message"]?.jsonObject
                ?: throw IOException("Model returned no message.")
            val content = message["content"]?.jsonPrimitive?.contentOrNull
            val toolCalls = message["tool_calls"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonArray

            if (toolCalls.isNullOrEmpty()) {
                return@withContext RunResult(
                    text = content?.trim().orEmpty().ifBlank { "Done. The canvas is yours." },
                    toolCalls = toolCallTotal,
                    steps = step + 1
                )
            }

            // Echo the assistant's tool-call turn back to the model.
            messages.add(buildJsonObject {
                put("role", "assistant")
                put("content", content ?: "")
                put("tool_calls", toolCalls)
            })

            for ((index, callElement) in toolCalls.withIndex()) {
                val call = callElement.jsonObject
                val id = call["id"]?.jsonPrimitive?.contentOrNull ?: "call_$step$index"
                val function = call["function"]?.jsonObject
                val name = function?.get("name")?.jsonPrimitive?.contentOrNull
                if (name == null) {
                    messages.add(toolMessage(id, "Error: tool call has no function name."))
                    continue
                }
                val argumentsRaw = function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                val arguments = try {
                    json.parseToJsonElement(argumentsRaw).jsonObject
                } catch (_: Exception) {
                    JsonObject(emptyMap())
                }
                onStep("Running ${name.removePrefix("anymate_").replace('_', ' ')}")
                val result = registry.execute(name, arguments)
                toolCallTotal++
                val text = if (result.isError) "Error: ${result.text}" else result.text
                messages.add(toolMessage(id, text.take(6000)))

                if (result.images.isNotEmpty() && imagesSent < 2) {
                    imageQueue.add(result.images.first())
                }
            }
            // Tool responses must directly follow the assistant tool-call turn.
            // Visual feedback is supplied after that group is complete.
            imageQueue.forEach { image ->
                val b64 = Base64.encodeToString(image.data, Base64.NO_WRAP)
                messages.add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "Visual result from the tool. Verify composition and continue.")
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", "data:${image.mimeType};base64,$b64")
                            })
                        })
                    })
                })
                imagesSent++
            }
            imageQueue.clear()
        }
        RunResult(
            text = "I reached the step limit while directing. The work so far is on the canvas — ask me to continue or refine it.",
            toolCalls = toolCallTotal,
            steps = maxSteps
        )
    }

    private suspend fun post(body: String): String {
        val url = endpointUrl()
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
        if (prefs.agentKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${prefs.agentKey}")
        }
        val request = builder.build()
        return client.newCall(request).awaitText()
    }

    private fun endpointUrl(): String {
        val endpoint = prefs.agentEndpoint.trim().trimEnd('/')
        if (endpoint.endsWith("/chat/completions")) return endpoint
        if (endpoint.isBlank()) return "https://api.openai.com/v1/chat/completions"
        val withoutScheme = endpoint.substringAfter("://")
        val hasPath = withoutScheme.contains('/')
        return if (hasPath) "$endpoint/chat/completions" else "$endpoint/v1/chat/completions"
    }

    private fun toolDefinitions(): JsonArray = JsonArray(registry.tools.values.map { tool ->
        buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", tool.inputSchema)
            })
        }
    })

    private fun userMessage(text: String): JsonObject = buildJsonObject {
        put("role", "user")
        put("content", text)
    }

    private fun systemMessage(text: String): JsonObject = buildJsonObject {
        put("role", "system")
        put("content", text)
    }

    private fun toolMessage(id: String, text: String): JsonObject = buildJsonObject {
        put("role", "tool")
        put("tool_call_id", id)
        put("content", text)
    }

    private suspend fun Call.awaitText(): String = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        val message = try {
                            json.parseToJsonElement(text).jsonObject["error"]?.jsonObject
                                ?.get("message")?.jsonPrimitive?.contentOrNull
                        } catch (_: Exception) {
                            null
                        }
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IOException("HTTP ${it.code}: ${message ?: text.take(300)}")
                            )
                        }
                    } else {
                        if (continuation.isActive) continuation.resume(text)
                    }
                }
            }
        })
    }

    companion object {
        val SYSTEM_PROMPT = """
You are the director inside AnyMate, a live 2D animation studio running on the artist's Android phone. You and the artist share one canvas: every tool call appears immediately in front of them.

Rules
- Always use tools for changes. Never claim you drew, added frames or changed settings without a tool call.
- Start with anymate_get_state. Call anymate_snapshot to see the canvas (you will receive the PNG as visual feedback).
- Respect the existing artwork. Never call anymate_new_project, delete frames/layers or clear work unless the user explicitly asks.
- If a layer is locked, unlock it with anymate_set_layer before drawing.
- Coordinates are project pixels. (0,0) is top-left. Keep important art inside the canvas.
- Layers are indexed from 0 (bottom). Use layer 0 for background, layer 1 for the subject, and the last layer for FX when available.
- Prefer a restrained palette, consistent line weight, generous negative space, and clean silhouettes. Aim for a premium editorial motion-graphics look, not clip art.
- Animation: build seamless loops. For an N-frame loop use phase = frame / N, not frame / (N-1). Reuse shapes with smooth parametric motion (sin, cos, easing). Add squash, anticipations, overshoots and secondary motion where appropriate.
- Speed: 12/24/30/60 fps are supported. For sequences of 24+ frames use anymate_batch (one undo step, far less overhead). Keep batches under ~300 calls; each frame should normally use 2-6 elements, not hundreds.
- Frame-by-frame: add a frame, draw every element for that pose, then move on. To leave the artist's current work untouched, append after the last frame.
- Finish by calling anymate_play so the artist instantly sees the result, then summarize what you made: scene, frame range, fps, palette, and anything they might tweak.
- If the task is large, make a clear first pass that already looks composed, then offer a second polish pass.

The built-in offline director (anymate_generate) can create quick presets; use it for simple motion studies, or build your own bespoke animation for richer requests.
""".trimIndent()
    }
}
