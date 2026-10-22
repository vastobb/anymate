<div align="center">

<img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="96" alt="AnyMate logo" />

# AnyMate

**A 2D animation studio for Android that a human and an AI agent can drive at the same time.**

One canvas. Real drawing tools. A built-in MCP server. And a companion controller that brings the AI.

[![Build AnyMate APK](https://github.com/vastobb/anymate/actions/workflows/build.yml/badge.svg)](https://github.com/vastobb/anymate/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/vastobb/anymate.svg)](https://github.com/vastobb/anymate/releases/latest)

</div>

---

## What AnyMate is

AnyMate is a **landscape-first, touch-first frame-by-frame animation app** for Android, built
around a single idea: **the artist and the agent share one document**.

- **Human side** — pencil, pressure-aware ink pen, soft brush, eraser, flood fill, shapes, type,
  colour picker, layers, onion skin, timeline, playback at 12 / 24 / 30 / 60 fps, GIF + PNG export.
- **Agent side** — a real **Model Context Protocol** server runs inside the app. Any MCP client
  (Claude, Cursor, VS Code, [anymate-controller](https://github.com/vastobb/anymate-controller))
  can draw, animate, play, inspect and export through ~55 tools, and it can *see* the canvas
  through PNG snapshots and sprite sheets.
- **Together** — [anymate-controller](https://github.com/vastobb/anymate-controller) is the brain
  box: an AI agent, ffmpeg, Python, a terminal and a full animation design system, all local, all
  driving this canvas.

> The app itself contains **no assistant**. The phone stays fast, offline and focused; the agent
> lives in the controller.

## What's new in 1.1

| | |
|---|---|
| **Landscape studio** | The app runs in landscape with a floating chrome layout: tool rail left, inspector right, slim timeline at the bottom — the canvas keeps the rest of the screen. Pinch-zoom, two-finger pan, one-finger draw that never fights the UI. |
| **Three art-directed themes** | **Graphite** (cool neutral), **Warm Neutral** (sand and clay) and **Midnight Slate** (deep blue). Switching a theme restyles the whole studio, artboard included. |
| **Compact layout** | One switch for a denser studio: smaller tools, shorter timeline, tighter sheets — more canvas. |
| **Animation-grade MCP** | New tools: `anymate_draw_curve`, `anymate_motion_path`, `anymate_offset_frame`, `anymate_reorder_frame`, `anymate_merge_layers`, `anymate_get_frame`, `anymate_replace_layer`, `anymate_delete_element`, `anymate_set_grid`, `anymate_set_canvas`, `anymate_set_playback_range`, `anymate_get_render_info`, `anymate_render_sprite_sheet`. |
| **anymate-controller** | A separate app: AI agent + ffmpeg + Python + terminal + animation design tools, exposed over MCP. |

## Highlights

| Area | Detail |
|------|--------|
| Drawing | Pressure-aware strokes, soft brush, tapered ink, eraser, flood fill with tolerance, line / rect / ellipse / arrow, text with custom typeface |
| Timeline | Frame add / duplicate / delete / reorder / clear, onion skin (prev + next), looping playback, thumbnails that never block the draw thread |
| Performance | Vector scene graph, vsync-bounded rendering, batched agent ingest, lazy thumbnails — built to absorb **60+ frame writes per second** without stutter |
| Colour | Full HSV + alpha picker, curated studio palette, recents, eyedropper, hex entry, drawing grid |
| Layout | Landscape-first floating chrome, three themes, compact density mode |
| Layers | Add / rename / reorder / lock / hide / opacity, per-layer isolation so erasers behave correctly |
| MCP | Streamable HTTP transport, bearer-token auth, ~55 tools, resources, prompts, live PNG snapshots, sprite sheets, export downloads |
| Export | Animated GIF (popularity-quantised palette), full-resolution PNG, numbered PNG sequence ZIP, share sheet |
| Persistence | Debounced autosave to internal storage, automatic restore on launch, shareable `.anymate` project files |

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
`anymate_seek`, `anymate_set_loop`, `anymate_set_onion_skin`, `anymate_set_playback_range`

**Animation** — `anymate_draw_curve` (smooth Catmull-Rom), `anymate_motion_path`
(animate a shape along a path), `anymate_offset_frame`, `anymate_reorder_frame`,
`anymate_merge_layers`, `anymate_get_frame`, `anymate_replace_layer`,
`anymate_delete_element`, `anymate_set_grid`, `anymate_set_canvas`, `anymate_get_render_info`

**Preview & export** — `anymate_snapshot` (returns a PNG image),
`anymate_render_sprite_sheet` (every frame in one contact sheet),
`anymate_export_gif`, `anymate_export_png`, `anymate_export_frames`

**Directing** — `anymate_generate` (offline motion presets), `anymate_batch` (many calls in one
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

## The controller — where the AI lives

**[anymate-controller](https://github.com/vastobb/anymate-controller)** is a second Android app that
pairs with this one over MCP:

- **AnyMate Controller APK** (landscape, installable) — the AI director, an on-device **Python**
  runtime, a **terminal** with pipes and redirection, **GIF/MP4 encoding**, animation design tools
  (palettes, easing, timing plans, storyboards, sprite sheets) and a live preview of the phone's
  canvas. Download it from the
  [controller releases](https://github.com/vastobb/anymate-controller/releases).
- **Optional desktop runner** (`anymate-controller/desktop`) — the same toolbox for a workstation,
  where real ffmpeg and a real Python live. It speaks MCP too, so one client can drive the phone
  *and* the desktop.

```bash
# on your computer, with the phone app running
anymate-controller config --set anymate_url=http://192.168.1.20:8420/mcp
anymate-controller config --set anymate_token=am_xxxxxxxx
anymate-controller serve        # web console with a live canvas preview
anymate-controller chat "make a 60 fps walk cycle on the phone and play it"
anymate-controller mcp          # expose the whole toolbox over MCP (stdio)
```

## Sessions and project files

Work is autosaved to the device (debounced, a second after you stop) and restored the next time
AnyMate opens. **Export → AnyMate project** writes the whole editable document — frames, layers,
strokes, fills and type — into a single `.anymate` file you can share, back up or move to another
phone.

## Architecture

```
app/src/main/java/com/anymate/studio/
├── engine/          AnimationEngine (document + history), Rasterizer (single source of truth
│                    for editor/thumbnails/GIF/snapshots), Director (offline motion presets),
│                    GifEncoder, ExportManager, ProjectStore (autosave)
├── mcp/             ToolRegistry (one execution surface for MCP *and* the controller),
│                    McpServer (NanoHTTPD, JSON-RPC / Streamable HTTP), AnimationTools
├── model/           Document model: frames, layers, elements (stroke / shape / polygon /
│                    text / raster), project file format
└── ui/              Compose studio: landscape canvas, tool rail, inspector, timeline,
                     colour lab, layers, MCP panel, settings, export
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
