#!/usr/bin/env node
/**
 * AnyMate MCP bridge (stdio -> Streamable HTTP).
 *
 * Claude Desktop and other stdio-only MCP clients cannot talk to a device on
 * the network directly, so this tiny proxy forwards JSON-RPC to the AnyMate
 * app running on your phone.
 *
 *   ANYMATE_URL   http://192.168.1.20:8420/mcp   (default)
 *   ANYMATE_TOKEN your AnyMate bearer token       (optional if the app has auth off)
 *
 * Claude Desktop config (~/.config/Claude/claude_desktop_config.json):
 * {
 *   "mcpServers": {
 *     "anymate": {
 *       "command": "node",
 *       "args": ["/absolute/path/to/anymate/mcp-bridge/index.mjs"],
 *       "env": {
 *         "ANYMATE_URL": "http://192.168.1.20:8420/mcp",
 *         "ANYMATE_TOKEN": "am_..."
 *       }
 *     }
 *   }
 * }
 */

import readline from "node:readline";

const URL = process.env.ANYMATE_URL || "http://127.0.0.1:8420/mcp";
const TOKEN = process.env.ANYMATE_TOKEN || "";

const rl = readline.createInterface({ input: process.stdin, terminal: false });

function send(payload) {
  process.stdout.write(JSON.stringify(payload) + "\n");
}

async function forward(message) {
  const headers = {
    "Content-Type": "application/json",
    Accept: "application/json, text/event-stream",
  };
  if (TOKEN) headers.Authorization = `Bearer ${TOKEN}`;

  try {
    const response = await fetch(URL, {
      method: "POST",
      headers,
      body: JSON.stringify(message),
    });
    const text = await response.text();
    if (!response.ok) {
      send({
        jsonrpc: "2.0",
        id: message.id ?? null,
        error: { code: -32000, message: `AnyMate HTTP ${response.status}: ${text.slice(0, 400)}` },
      });
      return;
    }
    // Streamable HTTP may answer with SSE framing; unwrap the last data block.
    let body = text;
    if (text.includes("data: ")) {
      const chunks = text
        .split("\n")
        .filter((line) => line.startsWith("data: "))
        .map((line) => line.slice(6));
      body = chunks.length ? chunks[chunks.length - 1] : text;
    }
    let parsed;
    try {
      parsed = JSON.parse(body);
    } catch {
      send({
        jsonrpc: "2.0",
        id: message.id ?? null,
        error: { code: -32700, message: "Unparsable response from AnyMate" },
      });
      return;
    }
    if (Array.isArray(parsed)) {
      for (const item of parsed) send(item);
    } else {
      send(parsed);
    }
  } catch (error) {
    send({
      jsonrpc: "2.0",
      id: message.id ?? null,
      error: {
        code: -32000,
        message: `Cannot reach AnyMate at ${URL}: ${error?.message || error}`,
      },
    });
  }
}

rl.on("line", (line) => {
  const trimmed = line.trim();
  if (!trimmed) return;
  let message;
  try {
    message = JSON.parse(trimmed);
  } catch {
    return;
  }
  if (Array.isArray(message)) {
    message.forEach((item) => forward(item));
  } else {
    forward(message);
  }
});

rl.on("close", () => process.exit(0));
