package com.grokchat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GrokApiClient {

    suspend fun chat(
        endpoint: String,
        apiKey: String,
        model: String,
        messages: List<Message>
    ): String = withContext(Dispatchers.IO) {
        val conn = URL("$endpoint/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.doOutput = true

            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    messages.forEach { msg -> put(buildMessageObject(msg)) }
                })
            }.toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader(Charsets.UTF_8).readText()

            if (code !in 200..299) throw RuntimeException("HTTP $code: $text")

            JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } finally {
            conn.disconnect()
        }
    }

    private fun buildMessageObject(msg: Message): JSONObject = JSONObject().apply {
        put("role", msg.role)
        // Use multimodal array content when image is attached
        if (msg.imageBase64 != null) {
            put("content", JSONArray().apply {
                if (msg.content.isNotEmpty()) {
                    put(JSONObject().apply { put("type", "text"); put("text", msg.content) })
                }
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:${msg.imageMimeType ?: "image/jpeg"};base64,${msg.imageBase64}")
                    })
                })
            })
        } else {
            put("content", msg.content)
        }
    }
}
