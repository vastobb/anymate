<div align="center">

<img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="96" alt="AnyMate logo" />

# AnyMate

**A 2D animation studio for Android that a human and an AI agent can drive at the same time.**

One canvas. Real drawing tools. A built-in MCP server. An AI director that paints every frame live.

[![Build AnyMate APK](https://github.com/vastobb/anymate/actions/workflows/build.yml/badge.svg)](https://github.com/vastobb/anymate/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/vastobb/anymate.svg)](https://github.com/vastobb/anymate/releases/latest)

</div>

---

## What AnyMate is

AnyMate is a touch-first frame-by-frame animation app for Android, built around a single
idea: **the artist and the agent share one document**.

- **Human side** — pencil, ink pen, brush, eraser, flood fill, shapes, type, colour picker,
  layers, onion skin, timeline, playback at 12 / 24 / 30 / 60 fps, GIF + PNG export.
- **Agent side** — a real **Model Context Protocol** server runs inside the app. Any MCP client
  (Claude, Cursor, VS Code, your own agent) can draw, animate, play, inspect and export through
  ~40 tools, and it can *see* the canvas through PNG snapshots.
- **Both together** — the in-app **Director** (offline templates, or any OpenAI-compatible model)
  paints frames one by one while you watch, and every action is a single undo step.

## Highlights

| Area | Detail |
|------|--------|
| Drawing | Pressure-aware strokes, soft brush, tapered ink, eraser, flood fill with tolerance, line / rect / ellipse / arrow, text with custom typeface |
| Timeline | Frame add / duplicate / delete / clear, onion skin (prev + next), looping playback, thumbnails that never block the draw thread |
| Performance | Vector scene graph, vsync-bounded rendering, batched agent ingest, lazy thumbnails — built to absorb **60+ frame writes per second** without stutter |
| Colour | Full HSV + alpha picker, curated studio palette, recents, eyedropper, hex entry |
| Layers | Add / rename / reorder / lock / hide / opacity, per-layer isolation so erasers behave correctly |
| AI | Offline Local Director (no key, no network) **or** any OpenAI-compatible endpoint (OpenAI, OpenRouter, Ollama, LM Studio, Groq…) with tool calling and visual feedback |
| MCP | Streamable HTTP transport, bearer-token auth, tools, resources, prompts, live PNG snapshots, export downloads |
| Export | Animated GIF (popularity-quantised palette), full-resolution PNG, numbered PNG sequence ZIP, share sheet |

## Install

APKs are built by **GitHub Actions** (no local Android SDK required).

1. Open the **Actions** tab → run **Build AnyMate APK** (or download the artifact of the latest run).
2. Pull the `AnyMate-*.apk` artifact / release asset onto your phone.
3. Allow *install from unknown sources* and launch **AnyMate**.

The CI release APK is signed with the CI debug key so it can be sideloaded immediately.

<details>
<summary>Connect a phone over USB instead of Wi-Fi</summary>

```bash
adb reverse tcp:8420 tcp:8420
# then the app's MCP URL becomes http://127.0.0.1:8420/mcp
```
</details>

## Connect an AI agent (MCP)

The MCP server starts automatically with the app (port **8420**). Open
**AnyMate → Settings → MCP server** to see the URL, the bearer token and to rotate the token.
Full details, curl examples and client configs live in **[docs/MCP.md](docs/MCP.md)**.

```
MCP URL     http://<phone-ip>:8420/mcp
Header      Authorization: Bearer am_xxxxxxxx
(or append  ?token=am_xxxxxxxx  for clients that cannot set headers)
```

### Clients that speak HTTP directly

Claude Code, Cursor, VS Code (Copilot MCP), Windsurf, Zed, Continue… point at the URL above and
add the `Authorization` header.

### Clients that only speak stdio (Claude Desktop)

Use the tiny bridge shipped in `mcp-bridge/`:

```json
{
  "mcpServers": {
    "anymate": {
      "command": "node",
      "args": ["/absolute/path/to/anymate/mcp-bridge/index.mjs"],
      "env": {
        "ANYMATE_URL": "http://192.168.1.20:8420/mcp",
        "ANYMATE_TOKEN": "am_..."
      }
    }
  }
}
```

### Tools exposed over MCP

<details>
<summary>Full list</summary>

**Project** — `anymate_get_state`, `anymate_new_project`, `anymate_rename_project`,
`anymate_set_fps`, `anymate_set_background`

**Frames** — `anymate_list_frames`, `anymate_add_frame`, `anymate_duplicate_frame`,
`anymate_delete_frame`, `anymate_select_frame`, `anymate_clear_frame`

**Layers** — `anymate_add_layer`, `anymate_delete_layer`, `anymate_set_layer`,
`anymate_select_layer`

**Drawing** — `anymate_draw_stroke`, `anymate_draw_shape`, `anymate_draw_polygon`,
`anymate_draw_text`, `anymate_fill`, `anymate_clear_layer`

**Tools & colour** — `anymate_set_tool`, `anymate_set_color`, `anymate_get_palette`

**History & playback** — `anymate_undo`, `anymate_redo`, `anymate_play`, `anymate_pause`,
`anymate_seek`, `anymate_set_loop`, `anymate_set_onion_skin`

**Preview & export** — `anymate_snapshot` (returns a PNG image), `anymate_export_gif`,
`anymate_export_png`, `anymate_export_frames`

**Directing** — `anymate_generate` (offline director), `anymate_batch` (many calls in one
request → one undo step)

**Resources** — `anymate://preview/current` (PNG), `anymate://project/state` (JSON)

**Prompts** — `direct-an-animation`, `polish-the-scene`

</details>

### Writing a good agent prompt

```
Call anymate_get_state, then anymate_snapshot.
Build a 48-frame loop at 30 fps: a paper plane gliding through layered clouds,
parallax background on layer 0, the plane on layer 1, trails on layer 2.
Use anymate_batch so the whole sequence is one undo step, and finish with anymate_play.
```

## The in-app Director

*Settings → AI agent* switches between two modes:

- **Local Director (offline)** — no key, no network. It interprets motion briefs
  (*bouncing ball, walk cycle, fireworks at 60 fps, kinetic type "HELLO", pulse rings,
  heart loop, solar orbit*) and paints them frame by frame, live, with the same drawing
  primitives an agent uses.
- **Connected model** — any OpenAI-compatible `/chat/completions` endpoint. The model receives
  every studio tool, calls them for real, and receives PNG snapshots of its own work as visual
  feedback.

## Architecture

```
app/src/main/java/com/anymate/studio/
├── engine/          AnimationEngine (document + history), Rasterizer (single source of truth
│                    for editor/thumbnails/GIF/snapshots), Director (offline generative
│                    engine), GifEncoder, ExportManager
├── mcp/             ToolRegistry (one execution surface for MCP *and* the AI agent),
│                    McpServer (NanoHTTPD, JSON-RPC / Streamable HTTP)
├── agent/           AiAgent — OpenAI-compatible tool-calling loop
├── model/           Document model: frames, layers, elements (stroke / shape / polygon /
│                    text / raster)
└── ui/              Compose studio: canvas, tool rail, timeline, colour lab, layers,
                     agent sheet, settings, export
```

### How 60+ fps stays smooth

1. **Vector scene graph.** Frames hold strokes/shapes/polygons/text, not pixels. The renderer
   draws paths with cached geometry, so a new frame costs a state write, not a bitmap allocation.
2. **One render surface.** The editor, the timeline thumbnails, GIF export and MCP snapshots all
   go through `Rasterizer`, so the agent's view and the artist's view can never diverge.
3. **Batched ingest.** `anymate_batch` (and the Director) apply many mutations inside a single
   undo group; the Compose state is updated per element but the frame loop stays vsync-bound.
4. **Lazy thumbnails.** Thumbnail rendering is debounced and runs off the UI thread against an
   immutable copy of the frame, so an agent writing 60 frames/second never stalls the canvas.
5. **Isolated history.** Drawing is undoable per action, while a whole agent sequence collapses
   into one undo step.

## Build from source

```bash
git clone https://github.com/vastobb/anymate.git
cd anymate
./gradlew :app:assembleDebug
```

Requires JDK 17 and the Android SDK (compileSdk 35, minSdk 26). CI does the same thing on
`ubuntu-latest` and publishes the APK.

## License

MIT — see [LICENSE](LICENSE). Bundled typefaces are Inter and JetBrains Mono (SIL Open Font
License 1.1).
