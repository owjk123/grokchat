package com.grokchat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.grokchat.databinding.ItemMessageAssistantBinding
import com.grokchat.databinding.ItemMessageUserBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(a: Message, b: Message) = a === b
            override fun areContentsTheSame(a: Message, b: Message) = a == b
        }
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position).role == "user") 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0)
            UserVH(ItemMessageUserBinding.inflate(inflater, parent, false))
        else
            AssistantVH(ItemMessageAssistantBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is UserVH -> holder.bind(msg)
            is AssistantVH -> holder.bind(msg)
        }
    }

    class UserVH(val binding: ItemMessageUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: Message) {
            if (msg.imagePath != null) {
                binding.ivAttachment.visibility = View.VISIBLE
                binding.ivAttachment.setImageBitmap(null)
                val path = msg.imagePath
                binding.ivAttachment.tag = path  // stale-load guard
                val owner = itemView.findViewTreeLifecycleOwner()
                if (owner != null) {
                    owner.lifecycleScope.launch {
                        val bmp = withContext(Dispatchers.IO) { decodeSampled(path, 1080) }
                        // Guard: view still bound to the same image path
                        if (bmp != null && binding.ivAttachment.tag == path) {
                            binding.ivAttachment.setImageBitmap(bmp)
                        }
                    }
                }
            } else {
                binding.ivAttachment.visibility = View.GONE
            }

            if (msg.content.isNotEmpty()) {
                binding.tvContent.text = msg.content
                binding.tvContent.visibility = View.VISIBLE
            } else {
                binding.tvContent.visibility = View.GONE
            }

            binding.tvContent.setOnLongClickListener { v ->
                copyToClipboard(v.context, msg.content)
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                true
            }
        }
    }

    class AssistantVH(val binding: ItemMessageAssistantBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: Message) {
            binding.tvContent.text = msg.content
            binding.tvContent.setOnLongClickListener { v ->
                copyToClipboard(v.context, msg.content)
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                true
            }
        }
    }
}

private fun copyToClipboard(ctx: Context, text: String) {
    if (text.isEmpty()) return
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("message", text))
    Toast.makeText(ctx, ctx.getString(R.string.msg_copied), Toast.LENGTH_SHORT).show()
}

/**
 * Decode a bitmap from a file, sampled down so the longest edge <= maxEdge.
 * Keeps memory use sane even with large JPEGs.
 */
private fun decodeSampled(path: String, maxEdge: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxEdge) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)
    } catch (e: Exception) { null }
}
