package com.grokchat

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object MessageStore {
    private const val TAG = "MessageStore"
    private const val MESSAGES_FILE = "messages.json"
    private const val IMAGES_DIR = "images"
    const val MAX_MESSAGES = 200

    private fun messagesFile(ctx: Context) = File(ctx.filesDir, MESSAGES_FILE)

    private fun imagesDir(ctx: Context): File =
        File(ctx.filesDir, IMAGES_DIR).apply { if (!exists()) mkdirs() }

    suspend fun load(ctx: Context): MutableList<Message> = withContext(Dispatchers.IO) {
        val file = messagesFile(ctx)
        if (!file.exists()) return@withContext mutableListOf()
        try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Message(
                    role = o.getString("role"),
                    content = o.getString("content"),
                    imagePath = if (o.has("imagePath") && !o.isNull("imagePath"))
                        o.getString("imagePath") else null,
                    imageMimeType = if (o.has("imageMimeType") && !o.isNull("imageMimeType"))
                        o.getString("imageMimeType") else null
                )
            }.toMutableList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load messages; starting empty", e)
            mutableListOf()
        }
    }

    suspend fun save(ctx: Context, messages: List<Message>) = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            messages.forEach { m ->
                arr.put(JSONObject().apply {
                    put("role", m.role)
                    put("content", m.content)
                    if (m.imagePath != null) put("imagePath", m.imagePath)
                    if (m.imageMimeType != null) put("imageMimeType", m.imageMimeType)
                })
            }
            messagesFile(ctx).writeText(arr.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save messages", e)
        }
    }

    /**
     * Save a bitmap to files dir and return the absolute path.
     */
    suspend fun saveImage(ctx: Context, bmp: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(imagesDir(ctx), "${UUID.randomUUID()}.jpg")
            file.outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            null
        }
    }

    /**
     * Delete all chat data (messages.json + images dir).
     */
    suspend fun clearAll(ctx: Context) = withContext(Dispatchers.IO) {
        try {
            messagesFile(ctx).delete()
            imagesDir(ctx).listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear messages", e)
        }
    }

    /**
     * Delete orphaned image files not referenced in the given messages list.
     */
    suspend fun pruneOrphanImages(ctx: Context, messages: List<Message>) = withContext(Dispatchers.IO) {
        try {
            val referenced = messages.mapNotNull { it.imagePath }.toSet()
            imagesDir(ctx).listFiles()?.forEach { f ->
                if (f.absolutePath !in referenced) f.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prune orphan images", e)
        }
    }
}
