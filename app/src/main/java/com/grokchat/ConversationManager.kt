package com.grokchat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 对话历史管理器
 * 使用JSON文件存储对话历史，便于管理多个对话
 */
object ConversationManager {

    private const val CONVERSATIONS_FILE = "conversations.json"

    /**
     * 获取所有对话列表
     */
    fun getAllConversations(ctx: Context): List<Conversation> {
        return try {
            val file = File(ctx.filesDir, CONVERSATIONS_FILE)
            if (!file.exists()) return emptyList()
            
            val json = file.readText()
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { parseConversation(arr.getJSONObject(it)) }
                .sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 根据ID获取单个对话
     */
    fun getConversation(ctx: Context, id: String): Conversation? {
        return getAllConversations(ctx).find { it.id == id }
    }

    /**
     * 保存对话（新增或更新）
     */
    fun saveConversation(ctx: Context, conversation: Conversation) {
        val conversations = getAllConversations(ctx).toMutableList()
        val existingIndex = conversations.indexOfFirst { it.id == conversation.id }
        
        if (existingIndex >= 0) {
            conversations[existingIndex] = conversation.copy(updatedAt = System.currentTimeMillis())
        } else {
            conversations.add(conversation)
        }
        
        saveAllConversations(ctx, conversations)
    }

    /**
     * 删除对话
     */
    fun deleteConversation(ctx: Context, id: String) {
        val conversations = getAllConversations(ctx).filter { it.id != id }
        saveAllConversations(ctx, conversations)
    }

    /**
     * 更新对话消息
     */
    fun updateConversationMessages(ctx: Context, conversationId: String, messages: List<Message>) {
        val conversation = getConversation(ctx, conversationId) ?: return
        val updatedConversation = conversation.copy(
            messages = messages,
            updatedAt = System.currentTimeMillis()
        )
        saveConversation(ctx, updatedConversation)
    }

    /**
     * 创建新对话
     */
    fun createNewConversation(ctx: Context, roleId: String): Conversation {
        val conversation = Conversation(
            roleId = roleId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        saveConversation(ctx, conversation)
        return conversation
    }

    /**
     * 生成对话标题（取第一条消息的前20个字符）
     */
    fun generateTitle(firstMessage: String): String {
        return if (firstMessage.length > 20) {
            firstMessage.take(20) + "..."
        } else {
            firstMessage
        }
    }

    private fun saveAllConversations(ctx: Context, conversations: List<Conversation>) {
        try {
            val arr = JSONArray()
            conversations.forEach { conv ->
                arr.put(conversationToJson(conv))
            }
            val file = File(ctx.filesDir, CONVERSATIONS_FILE)
            file.writeText(arr.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseConversation(json: JSONObject): Conversation? {
        return try {
            val messagesArray = json.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until messagesArray.length()).mapNotNull { i ->
                try {
                    val msgObj = messagesArray.getJSONObject(i)
                    Message(
                        role = msgObj.getString("role"),
                        content = msgObj.getString("content"),
                        imageBase64 = msgObj.optString("imageBase64").takeIf { it.isNotEmpty() && it != "null" },
                        imageMimeType = msgObj.optString("imageMimeType").takeIf { it.isNotEmpty() && it != "null" }
                    )
                } catch (e: Exception) {
                    null
                }
            }
            
            Conversation(
                id = json.getString("id"),
                title = json.optString("title", ""),
                roleId = json.optString("roleId", ""),
                messages = messages,
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun conversationToJson(conv: Conversation): JSONObject {
        return JSONObject().apply {
            put("id", conv.id)
            put("title", conv.title)
            put("roleId", conv.roleId)
            put("createdAt", conv.createdAt)
            put("updatedAt", conv.updatedAt)
            put("messages", JSONArray().apply {
                conv.messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                        msg.imageBase64?.let { put("imageBase64", it) }
                        msg.imageMimeType?.let { put("imageMimeType", it) }
                    })
                }
            })
        }
    }
}
