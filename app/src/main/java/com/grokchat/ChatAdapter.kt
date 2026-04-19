package com.grokchat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.grokchat.databinding.ItemMessageAssistantBinding
import com.grokchat.databinding.ItemMessageUserBinding

class ChatAdapter(
    private val onMessageLongClick: ((Message) -> Unit)? = null
) : ListAdapter<Message, RecyclerView.ViewHolder>(DIFF) {

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
            UserVH(ItemMessageUserBinding.inflate(inflater, parent, false), onMessageLongClick)
        else
            AssistantVH(ItemMessageAssistantBinding.inflate(inflater, parent, false), onMessageLongClick)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserVH -> holder.bind(getItem(position))
            is AssistantVH -> holder.bind(getItem(position))
        }
    }

    class UserVH(
        val binding: ItemMessageUserBinding,
        private val onLongClick: ((Message) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: Message) {
            if (msg.imageBase64 != null) {
                try {
                    val bytes = Base64.decode(msg.imageBase64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    binding.ivAttachment.setImageBitmap(bmp)
                    binding.ivAttachment.visibility = View.VISIBLE
                } catch (e: Exception) {
                    binding.ivAttachment.visibility = View.GONE
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

            // Setup long click to copy message content
            binding.root.setOnLongClickListener {
                if (msg.content.isNotEmpty()) {
                    copyToClipboard(binding.root.context, msg.content)
                }
                onLongClick?.invoke(msg)
                true
            }
        }

        private fun copyToClipboard(context: Context, text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("GrokChat Message", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
    }

    class AssistantVH(
        val binding: ItemMessageAssistantBinding,
        private val onLongClick: ((Message) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: Message) {
            binding.tvContent.text = msg.content
            
            // Load custom avatar if set
            try {
                val avatarBase64 = Prefs.getGrokAvatar(binding.root.context)
                if (avatarBase64 != null) {
                    val bytes = Base64.decode(avatarBase64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    binding.ivAvatar.setImageBitmap(bmp)
                } else {
                    binding.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
                }
            } catch (e: Exception) {
                binding.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
            }
            
            // Setup long click to copy message content
            binding.root.setOnLongClickListener {
                if (msg.content.isNotEmpty()) {
                    copyToClipboard(binding.root.context, msg.content)
                }
                onLongClick?.invoke(msg)
                true
            }
        }

        private fun copyToClipboard(context: Context, text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("GrokChat Message", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
    }
}
