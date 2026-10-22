package com.anymate.studio

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/**
 * Lightweight, on-device settings. The MCP token and model key never leave the
 * handset except when you call your own configured model endpoint.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("anymate.settings", Context.MODE_PRIVATE)

    var mcpEnabled: Boolean
        get() = sp.getBoolean("mcp.enabled", true)
        set(value) = sp.edit().putBoolean("mcp.enabled", value).apply()

    var mcpPort: Int
        get() = sp.getInt("mcp.port", 8420)
        set(value) = sp.edit().putInt("mcp.port", value.coerceIn(1024, 65535)).apply()

    var mcpToken: String
        get() {
            val existing = sp.getString("mcp.token", null)
            if (!existing.isNullOrBlank()) return existing
            val fresh = generateToken()
            sp.edit().putString("mcp.token", fresh).apply()
            return fresh
        }
        set(value) = sp.edit().putString("mcp.token", value.trim()).apply()

    var agentEndpoint: String
        get() = sp.getString("agent.endpoint", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
        set(value) = sp.edit().putString("agent.endpoint", value.trim()).apply()

    var agentModel: String
        get() = sp.getString("agent.model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) = sp.edit().putString("agent.model", value.trim()).apply()

    var agentKey: String
        get() = sp.getString("agent.key", "") ?: ""
        set(value) = sp.edit().putString("agent.key", value.trim()).apply()

    var agentOffline: Boolean
        get() = sp.getBoolean("agent.offline", true)
        set(value) = sp.edit().putBoolean("agent.offline", value).apply()

    var perfHud: Boolean
        get() = sp.getBoolean("studio.perfHud", false)
        set(value) = sp.edit().putBoolean("studio.perfHud", value).apply()

    var loopPlayback: Boolean
        get() = sp.getBoolean("studio.loop", true)
        set(value) = sp.edit().putBoolean("studio.loop", value).apply()

    var onionSkin: Boolean
        get() = sp.getBoolean("studio.onion", false)
        set(value) = sp.edit().putBoolean("studio.onion", value).apply()

    var seenWelcome: Boolean
        get() = sp.getBoolean("studio.seenWelcome", false)
        set(value) = sp.edit().putBoolean("studio.seenWelcome", value).apply()

    var recentColors: List<Long>
        get() = (sp.getString("studio.recentColors", "") ?: "")
            .split(",")
            .mapNotNull { it.toLongOrNull() }
        set(value) = sp.edit().putString("studio.recentColors", value.take(16).joinToString(",")).apply()

    /** Issues a fresh MCP access token and stores it. */
    fun regenerateToken(): String {
        val fresh = generateToken()
        mcpToken = fresh
        return fresh
    }

    fun rememberColor(color: Long) {
        val next = (listOf(color) + recentColors.filter { it != color }).take(16)
        recentColors = next
    }

    companion object {
        private fun generateToken(): String {
            val bytes = ByteArray(18)
            SecureRandom().nextBytes(bytes)
            return "am_" + bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
