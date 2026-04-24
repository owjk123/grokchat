package com.grokchat

import java.util.UUID

data class Role(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val systemPrompt: String,
    val avatar: String = "🎭"
)
