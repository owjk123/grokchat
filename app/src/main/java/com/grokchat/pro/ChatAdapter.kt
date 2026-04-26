package com.grokchat.pro

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.grokchat.pro.databinding.ItemMessageAssistantBinding
import com.grokchat.pro.databinding.ItemMessageUserBinding

class ChatAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(a: Message, b: Message) = a === b
            override fun areContentsTheSame(a: Message, b: Message) = a == b
        }
    }

    var roleAvatar: String = "🤖"
        set(value) {
            field = value
            notifyDataSetChanged()
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
            is UserVH -> {
                holder.binding.tvContent.text = msg.content
                // User doesn't need avatar display in bubble
                if (msg.imageBase64 != null) {
                    holder.binding.ivImage.visibility = View.VISIBLE
                    try {
                        val bytes = Base64.decode(msg.imageBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        holder.binding.ivImage.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        holder.binding.ivImage.visibility = View.GONE
                    }
                } else {
                    holder.binding.ivImage.visibility = View.GONE
                }
                // Set bubble width to wrap content
                (holder.binding.tvContent.layoutParams as? ConstraintLayout.LayoutParams)?.let {
                    it.width = ConstraintLayout.LayoutParams.WRAP_CONTENT
                }
            }
            is AssistantVH -> {
                holder.binding.tvContent.text = msg.content
                holder.binding.tvAvatar.text = roleAvatar
                if (msg.imageBase64 != null) {
                    holder.binding.ivImage.visibility = View.VISIBLE
                    try {
                        val bytes = Base64.decode(msg.imageBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        holder.binding.ivImage.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        holder.binding.ivImage.visibility = View.GONE
                    }
                } else {
                    holder.binding.ivImage.visibility = View.GONE
                }
                // Set bubble width to wrap content
                (holder.binding.tvContent.layoutParams as? ConstraintLayout.LayoutParams)?.let {
                    it.width = ConstraintLayout.LayoutParams.WRAP_CONTENT
                }
            }
        }
    }

    class UserVH(val binding: ItemMessageUserBinding) : RecyclerView.ViewHolder(binding.root)
    class AssistantVH(val binding: ItemMessageAssistantBinding) : RecyclerView.ViewHolder(binding.root)
}
