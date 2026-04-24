package com.grokchat

data class Message(
    val role: String,
    val content: String,
    val imagePath: String? = null,
    val imageMimeType: String? = null
)
