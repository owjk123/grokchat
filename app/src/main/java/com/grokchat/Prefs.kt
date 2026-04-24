package com.grokchat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val PREF_FILE = "grokchat_prefs"
    private const val KEY_SCHEMA_VERSION = "schema_version"
    const val SCHEMA_VERSION = 2

    val ENDPOINTS = listOf(
        "http://vip.apiyi.com:16888"    to "美国优化 (US)",
        "http://api-cf.apiyi.com:16888" to "CF-CDN",
        "http://api.apiyi.com:16888"    to "大陆优化1 (China 1)",
        "http://b.apiyi.com:16888"      to "大陆优化2 (China 2)"
    )

    val MODELS = listOf("grok-4.20-beta", "grok-3", "grok-2-latest")

    // Fixed IDs so active-role preference survives restarts
    val DEFAULT_ROLES = listOf(
        Role(id = "builtin-assistant",  name = "助手",     systemPrompt = "",                                                          avatar = "🤖"),
        Role(id = "builtin-coder",      name = "代码专家", systemPrompt = "你是一位经验丰富的软件工程师，请简洁、精准地协助解决编程问题。", avatar = "💻"),
        Role(id = "builtin-translator", name = "翻译专家", systemPrompt = "你是一位专业翻译，请保持原文语气，准确翻译为目标语言。",       avatar = "🌐")
    )

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    /**
     * Run any needed migrations. Called once from MainActivity.onCreate
     * before fragments touch the data layer.
     */
    fun ensureMigrated(ctx: Context) {
        val p = prefs(ctx)
        val current = p.getInt(KEY_SCHEMA_VERSION, 0)
        if (current >= SCHEMA_VERSION) return

        // v0 / v1 → v2: ensure every role has a non-empty avatar field.
        // getRoles already injects the "🎭" default on load; re-saving persists it.
        val roles = getRoles(ctx)
        val migrated = roles.map {
            if (it.avatar.isBlank()) it.copy(avatar = defaultAvatarFor(it.id)) else it
        }
        saveRoles(ctx, migrated)

        p.edit().putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION).apply()
    }

    private fun defaultAvatarFor(id: String): String = when (id) {
        "builtin-assistant"  -> "🤖"
        "builtin-coder"      -> "💻"
        "builtin-translator" -> "🌐"
        else                 -> "🎭"
    }

    fun getApiKey(ctx: Context): String = prefs(ctx).getString("api_key", "") ?: ""
    fun setApiKey(ctx: Context, v: String) = prefs(ctx).edit().putString("api_key", v).apply()

    fun getEndpoint(ctx: Context): String =
        prefs(ctx).getString("endpoint", ENDPOINTS[0].first) ?: ENDPOINTS[0].first
    fun setEndpoint(ctx: Context, v: String) = prefs(ctx).edit().putString("endpoint", v).apply()

    fun getModel(ctx: Context): String =
        prefs(ctx).getString("model", MODELS[0]) ?: MODELS[0]
    fun setModel(ctx: Context, v: String) = prefs(ctx).edit().putString("model", v).apply()

    fun getRoles(ctx: Context): MutableList<Role> {
        val json = prefs(ctx).getString("roles", null)
            ?: return DEFAULT_ROLES.toMutableList().also { saveRoles(ctx, it) }
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                val id = o.getString("id")
                Role(
                    id = id,
                    name = o.getString("name"),
                    systemPrompt = o.getString("systemPrompt"),
                    avatar = if (o.has("avatar") && !o.isNull("avatar") && o.getString("avatar").isNotBlank())
                        o.getString("avatar") else defaultAvatarFor(id)
                )
            }.toMutableList()
        } catch (e: Exception) {
            DEFAULT_ROLES.toMutableList().also { saveRoles(ctx, it) }
        }
    }

    fun saveRoles(ctx: Context, roles: List<Role>) {
        val arr = JSONArray()
        roles.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("systemPrompt", it.systemPrompt)
                put("avatar", it.avatar)
            })
        }
        prefs(ctx).edit().putString("roles", arr.toString()).apply()
    }

    fun getActiveRoleId(ctx: Context): String? = prefs(ctx).getString("active_role_id", null)
    fun setActiveRoleId(ctx: Context, id: String) =
        prefs(ctx).edit().putString("active_role_id", id).apply()
    fun clearActiveRoleId(ctx: Context) =
        prefs(ctx).edit().remove("active_role_id").apply()
}
