package com.grokchat.pro

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object MessageStore {
    private const val MESSAGES_FILE = "messages.json"

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private var currentRoleId: String? = null

    suspend fun loadMessages(ctx: Context, roleId: String) = withContext(Dispatchers.IO) {
        if (currentRoleId != roleId) {
            currentRoleId = roleId
            val file = File(ctx.filesDir, "${MESSAGES_FILE}_$roleId")
            if (file.exists()) {
                try {
                    val json = file.readText()
                    val arr = JSONArray(json)
                    val list = mutableListOf<Message>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(Message(
                            role = obj.getString("role"),
                            content = obj.getString("content"),
                            imageBase64 = obj.optString("imageBase64", null).takeIf { it.isNotEmpty() }
                        ))
                    }
                    _messages.value = list
                } catch (e: Exception) {
                    _messages.value = emptyList()
                }
            } else {
                _messages.value = emptyList()
            }
        }
    }

    suspend fun saveMessages(ctx: Context) = withContext(Dispatchers.IO) {
        val roleId = currentRoleId ?: return@withContext
        val file = File(ctx.filesDir, "${MESSAGES_FILE}_$roleId")
        val arr = JSONArray()
        _messages.value.forEach { msg ->
            arr.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
                msg.imageBase64?.let { put("imageBase64", it) }
            })
        }
        file.writeText(arr.toString())
    }

    fun addMessage(message: Message) {
        _messages.value = _messages.value + message
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    suspend fun clearAndSave(ctx: Context) {
        clearMessages()
        saveMessages(ctx)
    }
}
