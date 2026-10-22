package com.anymate.studio

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.engine.ExportManager
import com.anymate.studio.engine.ProjectStore
import com.anymate.studio.mcp.McpServer
import com.anymate.studio.mcp.ToolRegistry
import com.anymate.studio.model.CanvasPreset
import com.anymate.studio.ui.theme.ThemeKind
import com.anymate.studio.util.toArgbLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

/**
 * AnyMate is a drawing studio first and an MCP host second: the app itself has
 * no assistant. All AI behaviour lives in **anymate-controller**, which talks
 * to this app over MCP.
 */
class StudioViewModel(app: Application) : AndroidViewModel(app) {

    val engine = AnimationEngine()
    val prefs = Prefs(app)
    val exporter = ExportManager(app)
    val registry = ToolRegistry(engine, exporter)
    val server = McpServer(app, engine, registry, exporter, prefs)
    val projectStore = ProjectStore(app, engine)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var exportJob: Job? = null

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
    var themeKind by mutableStateOf(
        runCatching { ThemeKind.valueOf(prefs.themeName) }.getOrDefault(ThemeKind.GRAPHITE)
    )
        private set
    var compactLayout by mutableStateOf(prefs.compactLayout)
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
        startAutosaveLoop()
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

    fun updateTheme(kind: ThemeKind) {
        val previousPaper = themeKind.palette.paper.toArgbLong()
        themeKind = kind
        prefs.themeName = kind.name
        // Keep the artboard in step with the theme unless the artist chose a colour.
        if (engine.background == previousPaper) {
            engine.updateBackground(kind.palette.paper.toArgbLong())
        }
    }

    fun updateCompact(value: Boolean) {
        compactLayout = value
        prefs.compactLayout = value
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
        engine.newProject(
            name = name,
            preset = preset,
            fpsValue = fps,
            backgroundColor = themeKind.palette.paper.toArgbLong()
        )
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
            } catch (cancel: kotlinx.coroutines.CancellationException) {
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
        exportJob?.cancel()
        server.stopServer()
    }
}
