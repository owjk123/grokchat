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
                    messages.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
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
}
