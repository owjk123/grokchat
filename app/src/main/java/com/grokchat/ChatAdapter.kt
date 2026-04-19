package com.grokchat

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.grokchat.databinding.ItemMessageAssistantBinding
import com.grokchat.databinding.ItemMessageUserBinding

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
        when (holder) {
            is UserVH -> holder.bind(getItem(position))
            is AssistantVH -> holder.binding.tvContent.text = getItem(position).content
        }
    }

    class UserVH(val binding: ItemMessageUserBinding) : RecyclerView.ViewHolder(binding.root) {
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
        }
    }

    class AssistantVH(val binding: ItemMessageAssistantBinding) : RecyclerView.ViewHolder(binding.root)
}
