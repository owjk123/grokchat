package com.grokchat.pro

import java.util.UUID

data class Role(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatar: String = "🤖",
    val systemPrompt: String
)
