package com.grokchat

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Typed exception so UI can map status codes to friendly messages
 * without string-parsing the `.message`.
 */
class ApiException(
    val httpCode: Int,           // -1 for IO / unknown
    val friendlyKey: Kind,
    val detail: String? = null
) : RuntimeException(detail) {
    enum class Kind { AUTH, RATE_LIMIT, SERVER, NETWORK, PARSE, UNKNOWN }
}

object GrokApiClient {
    private const val TAG = "GrokApiClient"

    suspend fun chat(
        endpoint: String,
        apiKey: String,
        model: String,
        messages: List<Message>
    ): String = withContext(Dispatchers.IO) {
        val conn = try {
            URL("$endpoint/v1/chat/completions").openConnection() as HttpURLConnection
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open connection", e)
            throw ApiException(-1, ApiException.Kind.NETWORK, e.message)
        }
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
                ?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""

            if (code !in 200..299) {
                Log.w(TAG, "HTTP $code: $text")
                val kind = when (code) {
                    401, 403 -> ApiException.Kind.AUTH
                    429      -> ApiException.Kind.RATE_LIMIT
                    in 500..599 -> ApiException.Kind.SERVER
                    else     -> ApiException.Kind.UNKNOWN
                }
                throw ApiException(code, kind, text.take(200))
            }

            return@withContext try {
                JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse response: $text", e)
                throw ApiException(code, ApiException.Kind.PARSE, e.message)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown host", e)
            throw ApiException(-1, ApiException.Kind.NETWORK, e.message)
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout", e)
            throw ApiException(-1, ApiException.Kind.NETWORK, e.message)
        } catch (e: IOException) {
            Log.e(TAG, "Network IO", e)
            throw ApiException(-1, ApiException.Kind.NETWORK, e.message)
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error", e)
            throw ApiException(-1, ApiException.Kind.UNKNOWN, e.message)
        } finally {
            conn.disconnect()
        }
    }

    private fun buildMessageObject(msg: Message): JSONObject = JSONObject().apply {
        put("role", msg.role)
        val imgBase64 = msg.imagePath?.let { encodeImageToBase64(it) }
        if (imgBase64 != null) {
            put("content", JSONArray().apply {
                if (msg.content.isNotEmpty()) {
                    put(JSONObject().apply { put("type", "text"); put("text", msg.content) })
                }
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:${msg.imageMimeType ?: "image/jpeg"};base64,$imgBase64")
                    })
                })
            })
        } else {
            put("content", msg.content)
        }
    }

    private fun encodeImageToBase64(path: String): String? = try {
        val bytes = File(path).readBytes()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to encode image $path", e)
        null
    }
}
