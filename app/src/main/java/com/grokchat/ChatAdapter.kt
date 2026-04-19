package com.grokchat

import android.view.LayoutInflater
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
        val msg = getItem(position)
        when (holder) {
            is UserVH -> holder.binding.tvContent.text = msg.content
            is AssistantVH -> holder.binding.tvContent.text = msg.content
        }
    }

    class UserVH(val binding: ItemMessageUserBinding) : RecyclerView.ViewHolder(binding.root)
    class AssistantVH(val binding: ItemMessageAssistantBinding) : RecyclerView.ViewHolder(binding.root)
}
