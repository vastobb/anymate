# Connecting an AI agent to AnyMate

AnyMate ships an embedded **Model Context Protocol** server (Streamable HTTP transport).
No companion daemon, no cloud, no root — the phone is the server.

```
Base URL   http://<phone-ip>:8420
MCP URL    http://<phone-ip>:8420/mcp
Auth       Authorization: Bearer <token>
```

The URL, token and request counters are visible in **AnyMate → Settings → MCP server**.

---

## 1. Turn it on

1. Open **Settings → MCP server**.
2. Keep **Enable MCP** on (it is on by default; the server starts with the app).
3. Copy the **MCP URL** and the **Bearer token**.

The server listens on all interfaces so agents on the same Wi-Fi can reach it.
Rotate the token at any time with **New token** — connected clients must re-authenticate.

## 2. Point a client at it

### HTTP-native MCP clients (Claude Code, Cursor, VS Code, Windsurf, Zed, Continue)

Add the server with a bearer header:

```json
{
  "mcpServers": {
    "anymate": {
      "type": "http",
      "url": "http://192.168.1.20:8420/mcp",
      "headers": { "Authorization": "Bearer am_xxxxxxxx" }
    }
  }
}
```

If your client cannot set headers, append the token to the URL:
`http://192.168.1.20:8420/mcp?token=am_xxxxxxxx`

### stdio-only clients (Claude Desktop)

Use the bridge in [`mcp-bridge/`](../mcp-bridge):

```json
{
  "mcpServers": {
    "anymate": {
      "command": "node",
      "args": ["/absolute/path/to/anymate/mcp-bridge/index.mjs"],
      "env": {
        "ANYMATE_URL": "http://192.168.1.20:8420/mcp",
        "ANYMATE_TOKEN": "am_xxxxxxxx"
      }
    }
  }
}
```

The bridge is dependency-free (Node 18+) and forwards newline-delimited JSON-RPC to the app.

### Over USB (no Wi-Fi)

```bash
adb reverse tcp:8420 tcp:8420
```

Then use `http://127.0.0.1:8420/mcp` (shown as **Loopback** in the app).

### From an emulator

`http://10.0.2.2:8420/mcp` reaches the host machine, not the phone — use the emulator's own
`localhost` when the app runs inside the emulator.

---

## 3. Verify the connection

```bash
curl -s http://192.168.1.20:8420/health
# {"name":"AnyMate Studio","status":"ok","version":"1.0.0","frames":12,"fps":24,...}

curl -s -H "Authorization: Bearer am_xxxxxxxx" \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}' \
     http://192.168.1.20:8420/mcp
```

A browser can open `http://<phone-ip>:8420/` for a small landing page, and
`/snapshot.png` returns the live canvas as a PNG (same auth as the MCP endpoint).

---

## 4. What an agent can do

| Group | Tools |
|-------|-------|
| Project | `anymate_get_state`, `anymate_new_project`, `anymate_rename_project`, `anymate_set_fps`, `anymate_set_background` |
| Frames | `anymate_list_frames`, `anymate_add_frame`, `anymate_duplicate_frame`, `anymate_delete_frame`, `anymate_select_frame`, `anymate_clear_frame` |
| Layers | `anymate_add_layer`, `anymate_delete_layer`, `anymate_set_layer`, `anymate_select_layer` |
| Drawing | `anymate_draw_stroke`, `anymate_draw_shape`, `anymate_draw_polygon`, `anymate_draw_text`, `anymate_fill`, `anymate_clear_layer` |
| Style | `anymate_set_tool`, `anymate_set_color`, `anymate_get_palette` |
| History & playback | `anymate_undo`, `anymate_redo`, `anymate_play`, `anymate_pause`, `anymate_seek`, `anymate_set_loop`, `anymate_set_onion_skin` |
| Preview & export | `anymate_snapshot`, `anymate_export_gif`, `anymate_export_png`, `anymate_export_frames` |
| Directing | `anymate_generate`, `anymate_batch` |

Resources: `anymate://preview/current` (PNG), `anymate://project/state` (JSON).
Prompts: `direct-an-animation`, `polish-the-scene`.

### Coordinate system

Project pixels, origin top-left, x → right, y → down. Call `anymate_get_state` to learn
the canvas size (default 900 × 900).

### Working at 60 fps

* Use **`anymate_batch`** — one request, one undo step, far less overhead than 300 round-trips.
* Keep each frame to 2–6 elements. Vector shapes are cheap; a thousand-point stroke is not.
* Use `anymate_add_frame` with `copy: true` to inherit a background layer, or draw a static
  background once and copy it forward.
* `anymate_snapshot` returns the rendered frame, so an agent can verify composition instead of
  guessing.

### Example session

```json
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
  "name":"anymate_batch",
  "arguments":{"calls":[
    {"name":"anymate_set_fps","args":{"fps":60}},
    {"name":"anymate_add_frame","args":{"copy":true}},
    {"name":"anymate_draw_shape","args":{"shape":"ellipse","x1":420,"y1":300,"x2":480,"y2":360,"fill_color":"#7C3AED"}},
    {"name":"anymate_play","args":{}}
  ]}}}
```

## 5. Security notes

* The token is the only credential — treat it like a password.
* The server is reachable by anyone on the same network who has the token.
* Turn MCP off in Settings when you do not need it.
* `anymate_new_project` wipes the document; the agent system prompt tells models not to call it
  unless the user explicitly asks.
