package com.anymate.studio

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anymate.studio.agent.AiAgent
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.engine.Director
import com.anymate.studio.engine.ExportManager
import com.anymate.studio.engine.ProjectStore
import com.anymate.studio.mcp.McpServer
import com.anymate.studio.mcp.ToolRegistry
import com.anymate.studio.model.CanvasPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ChatRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val at: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

enum class ExportKind { GIF, PNG, ZIP }

data class ExportUiState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val label: String = "",
    val file: File? = null,
    val mime: String = "",
    val kind: ExportKind = ExportKind.GIF,
    val error: String? = null
)

data class Notice(val id: Long, val text: String)

class StudioViewModel(app: Application) : AndroidViewModel(app) {

    val engine = AnimationEngine()
    val prefs = Prefs(app)
    val exporter = ExportManager(app)
    val registry = ToolRegistry(engine, exporter)
    val server = McpServer(app, engine, registry, exporter, prefs)
    val projectStore = ProjectStore(app, engine)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var chatId = 0L
    private var agentJob: Job? = null
    private var exportJob: Job? = null

    val chat = mutableStateListOf<ChatMessage>()
    var thinking by mutableStateOf(false)
        private set
    var agentStatus by mutableStateOf("")
        private set
    var exportState by mutableStateOf<ExportUiState?>(null)
        private set
    var notice by mutableStateOf<Notice?>(null)
        private set
    var recentColors by mutableStateOf(prefs.recentColors)
        private set

    var showWelcome by mutableStateOf(!prefs.seenWelcome)
        private set

    var mcpEnabled by mutableStateOf(prefs.mcpEnabled)
        private set
    var agentEndpoint by mutableStateOf(prefs.agentEndpoint)
        private set
    var agentModel by mutableStateOf(prefs.agentModel)
        private set
    var agentKey by mutableStateOf(prefs.agentKey)
        private set
    var agentOffline by mutableStateOf(prefs.agentOffline)
        private set
    var perfHud by mutableStateOf(prefs.perfHud)
        private set

    init {
        engine.updateLoop(prefs.loopPlayback)
        engine.updateOnionSkin(prefs.onionSkin)
        if (prefs.mcpEnabled) {
            val started = server.startServer()
            if (!started) {
                notice = Notice(System.nanoTime(), server.lastError ?: "MCP server could not start")
            }
        }
        val restored = projectStore.readAutosave()
        if (restored) {
            notice = Notice(System.nanoTime(), "Restored \"${engine.projectName}\" from your last session")
        }
        startAutosaveLoop()        chat.add(
            ChatMessage(
                id = ++chatId,
                role = ChatRole.ASSISTANT,
                text = "I'm your AnyMate director. I can use every brush, layer and frame in this studio — and you'll watch each move happen live.\n\nTry “bouncing ball”, “walk cycle at 60 fps”, or “kinetic type HELLO”."
            )
        )
    }

    /** Debounced autosave: the document is written to internal storage. */
    private fun startAutosaveLoop() {
        viewModelScope.launch {
            while (true) {
                delay(1200)
                val revision = engine.revision
                delay(400)
                if (engine.revision == revision) {
                    // Capture on the main thread, then write off it.
                    val snapshot = projectStore.capture()
                    withContext(Dispatchers.IO) { projectStore.saveAutosave(snapshot) }
                }
            }
        }
    }

    // ---- Chat / agent -----------------------------------------------------
    fun sendPrompt(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || thinking) return
        chat.add(ChatMessage(++chatId, ChatRole.USER, prompt))
        trimChat()
        thinking = true
        agentStatus = if (agentOffline) "Local Director" else "Thinking"
        engine.setAgentRunning(true, agentStatus)
        agentJob = viewModelScope.launch {
            try {
                if (agentOffline) runLocalDirector(prompt) else runModelAgent(prompt)
            } catch (cancel: CancellationException) {
                chat.add(
                    ChatMessage(
                        ++chatId, ChatRole.SYSTEM,
                        "Stopped. Any partial frames stay on the timeline — tap Undo to remove them."
                    )
                )
            } catch (t: Throwable) {
                chat.add(
                    ChatMessage(
                        ++chatId, ChatRole.ASSISTANT,
                        "I couldn't finish that. ${t.message ?: "Unknown error"}\n\nCheck the model endpoint, key and internet connection in Settings → AI Agent.",
                        isError = true
                    )
                )
            } finally {
                thinking = false
                agentStatus = ""
                engine.setAgentRunning(false)
                trimChat()
            }
        }
    }

    private suspend fun runLocalDirector(prompt: String) {
        engine.setAgentRunning(true, "Local Director")
        engine.beginUndoGroup("AI · local director")
        try {
            val result = Director.animate(prompt, engine) { done, total ->
                engine.updateAgentProgress(done.toFloat() / total, "Directing frame $done/$total")
                agentStatus = "Directing frame $done/$total"
            }
            engine.logTool(
                "anymate_generate",
                "Local Director · ${result.label} · ${result.frameCount} frames @ ${result.fps} fps",
                true
            )
            chat.add(
                ChatMessage(
                    ++chatId, ChatRole.ASSISTANT,
                    "Made **${result.label}** — ${result.frameCount} frames at ${result.fps} fps, appended at frame ${result.startFrame + 1}. It's looping on the canvas now.\n\nIt's one undo step. Ask for a polish pass, or switch to a connected model in Settings for fully bespoke motion."
                )
            )
        } finally {
            engine.endUndoGroup()
            engine.setAgentRunning(false)
        }
    }

    private suspend fun runModelAgent(prompt: String) {
        engine.setAgentRunning(true, "Thinking with ${agentModel.ifBlank { "your model" }}")
        agentStatus = "Thinking with $agentModel"
        val agent = AiAgent(prefs, registry)
        val result = agent.run(prompt) { status ->
            mainHandler.post {
                engine.setAgentRunning(true, status)
                agentStatus = status
            }
        }
        val suffix = if (result.toolCalls > 0) {
            "\n\n_${result.toolCalls} tool calls · ${result.steps} model turns · all changes are live on the canvas._"
        } else ""
        chat.add(ChatMessage(++chatId, ChatRole.ASSISTANT, result.text + suffix))
        engine.setAgentRunning(false)
    }

    fun stopAgent() {
        agentJob?.cancel()
    }

    fun clearChat() {
        if (thinking) stopAgent()
        chat.clear()
        chatId = 0
        chat.add(
            ChatMessage(
                id = ++chatId,
                role = ChatRole.ASSISTANT,
                text = "Fresh page. What should we make move?"
            )
        )
    }

    private fun trimChat() {
        while (chat.size > 80) chat.removeAt(0)
    }

    // ---- Settings ---------------------------------------------------------
    fun updateMcpEnabled(enabled: Boolean) {
        mcpEnabled = enabled
        prefs.mcpEnabled = enabled
        if (enabled) {
            val ok = server.startServer()
            if (!ok) notice = Notice(System.nanoTime(), server.lastError ?: "Could not start MCP")
            else notice = Notice(System.nanoTime(), "MCP server live at ${server.mcpUrl}")
        } else {
            server.stopServer()
            notice = Notice(System.nanoTime(), "MCP server stopped")
        }
    }

    fun regenerateMcpToken() {
        val token = prefs.regenerateToken()
        server.updateToken(token)
        notice = Notice(System.nanoTime(), "New MCP token generated")
    }

    fun updateAgentEndpoint(value: String) {
        agentEndpoint = value
        prefs.agentEndpoint = value
    }

    fun updateAgentModel(value: String) {
        agentModel = value
        prefs.agentModel = value
    }

    fun updateAgentKey(value: String) {
        agentKey = value
        prefs.agentKey = value
    }

    fun updateAgentOffline(value: Boolean) {
        agentOffline = value
        prefs.agentOffline = value
    }

    fun updatePerfHud(value: Boolean) {
        perfHud = value
        prefs.perfHud = value
    }

    fun updateLoop(value: Boolean) {
        engine.updateLoop(value)
        prefs.loopPlayback = value
    }

    fun updateOnion(value: Boolean) {
        engine.updateOnionSkin(value)
        prefs.onionSkin = value
    }

    fun dismissWelcome() {
        showWelcome = false
        prefs.seenWelcome = true
    }

    fun rememberColor(color: Long) {
        prefs.rememberColor(color)
        recentColors = prefs.recentColors
    }

    // ---- Project ----------------------------------------------------------
    fun createProject(name: String, preset: CanvasPreset, fps: Int) {
        engine.newProject(name = name, preset = preset, fpsValue = fps)
        notice = Notice(System.nanoTime(), "New ${preset.width}×${preset.height} project at $fps fps")
    }

    // ---- Export -----------------------------------------------------------
    fun startExport(kind: ExportKind, maxDimension: Int = 720) {
        if (exportJob?.isActive == true) return
        val mime = when (kind) {
            ExportKind.GIF -> "image/gif"
            ExportKind.PNG -> "image/png"
            ExportKind.ZIP -> "application/zip"
        }
        exportState = ExportUiState(
            running = true, progress = 0f, label = "Starting export…",
            kind = kind, mime = mime
        )
        exportJob = viewModelScope.launch {
            try {
                val progress: suspend (Float, String) -> Unit = { p, label ->
                    withContext(Dispatchers.Main) {
                        exportState = exportState?.copy(progress = p, label = label)
                    }
                }
                val file = when (kind) {
                    ExportKind.GIF -> exporter.exportGif(engine, maxDimension, progress)
                    ExportKind.PNG -> exporter.exportPng(engine, progress)
                    ExportKind.ZIP -> exporter.exportZip(engine, progress)
                }
                exportState = exportState?.copy(
                    running = false, progress = 1f, label = "Ready · ${file.name}", file = file
                )
                notice = Notice(System.nanoTime(), "Export ready: ${file.name}")
            } catch (cancel: CancellationException) {
                exportState = exportState?.copy(running = false, label = "Export cancelled", error = "Cancelled")
            } catch (t: Throwable) {
                exportState = exportState?.copy(
                    running = false,
                    label = "Export failed",
                    error = t.message ?: "Unknown error"
                )
            }
        }
    }

    fun dismissExport() {
        if (exportState?.running == true) return
        exportState = null
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportState = exportState?.copy(running = false, label = "Export cancelled")
    }

    fun shareExport() {
        val file = exportState?.file ?: return
        try {
            exporter.share(file, exportState?.mime ?: "application/octet-stream")
        } catch (t: Throwable) {
            notice = Notice(System.nanoTime(), "Could not share: ${t.message}")
        }
    }

    /** Writes the editable document to a shareable .anymate file. */
    fun shareProject() {
        viewModelScope.launch {
            val snapshot = projectStore.capture()
            val file = withContext(Dispatchers.IO) {
                val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val target = java.io.File(exporter.exportsDir, "anymate_project_$stamp.anymate")
                if (projectStore.writeTo(target, snapshot)) target else null
            }
            if (file == null) {
                notify("Could not write the project file")
            } else {
                try {
                    exporter.share(file, "application/octet-stream")
                    notify("Project file ready")
                } catch (t: Throwable) {
                    notify("Could not share: ${t.message}")
                }
            }
        }
    }

    fun consumeNotice() {
        notice = null
    }

    fun notify(message: String) {
        notice = Notice(System.nanoTime(), message)
    }

    override fun onCleared() {
        super.onCleared()
        agentJob?.cancel()
        exportJob?.cancel()
        server.stopServer()
    }
}
