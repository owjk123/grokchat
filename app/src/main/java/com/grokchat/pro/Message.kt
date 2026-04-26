package com.grokchat.pro

data class Message(
    val role: String,
    val content: String,
    val imageBase64: String? = null
)
