package com.grokchat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val PREF_FILE = "grokchat_prefs"

    val ENDPOINTS = listOf(
        "http://vip.apiyi.com:16888"    to "美国优化 (US)",
        "http://api-cf.apiyi.com:16888" to "CF-CDN",
        "http://api.apiyi.com:16888"    to "大陆优化1 (China 1)",
        "http://b.apiyi.com:16888"      to "大陆优化2 (China 2)"
    )

    val MODELS = listOf("grok-4.20-beta", "grok-3", "grok-2-latest")

    val DEFAULT_ROLES = listOf(
        Role(name = "Assistant",     systemPrompt = ""),
        Role(name = "Coding Helper", systemPrompt = "You are an expert software engineer. Help with coding tasks concisely."),
        Role(name = "Translator",    systemPrompt = "You are a professional translator. Translate text as requested, preserving tone.")
    )

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    fun getApiKey(ctx: Context): String = prefs(ctx).getString("api_key", "") ?: ""
    fun setApiKey(ctx: Context, v: String) = prefs(ctx).edit().putString("api_key", v).apply()

    fun getEndpoint(ctx: Context): String =
        prefs(ctx).getString("endpoint", ENDPOINTS[0].first) ?: ENDPOINTS[0].first
    fun setEndpoint(ctx: Context, v: String) = prefs(ctx).edit().putString("endpoint", v).apply()

    fun getModel(ctx: Context): String =
        prefs(ctx).getString("model", MODELS[0]) ?: MODELS[0]
    fun setModel(ctx: Context, v: String) = prefs(ctx).edit().putString("model", v).apply()

    fun getRoles(ctx: Context): MutableList<Role> {
        val json = prefs(ctx).getString("roles", null) ?: return DEFAULT_ROLES.toMutableList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Role(o.getString("id"), o.getString("name"), o.getString("systemPrompt"))
            }.toMutableList()
        } catch (e: Exception) {
            DEFAULT_ROLES.toMutableList()
        }
    }

    fun saveRoles(ctx: Context, roles: List<Role>) {
        val arr = JSONArray()
        roles.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("systemPrompt", it.systemPrompt)
            })
        }
        prefs(ctx).edit().putString("roles", arr.toString()).apply()
    }

    fun getActiveRoleId(ctx: Context): String? = prefs(ctx).getString("active_role_id", null)
    fun setActiveRoleId(ctx: Context, id: String) =
        prefs(ctx).edit().putString("active_role_id", id).apply()
}
