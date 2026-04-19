package com.grokchat

import java.util.UUID

/**
 * 对话历史实体类
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",  // 对话标题，默认为空或第一条消息摘要
    val roleId: String = "", // 关联的角色ID
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
