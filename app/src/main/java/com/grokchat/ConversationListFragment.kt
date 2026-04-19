package com.grokchat

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.grokchat.databinding.FragmentConversationListBinding
import com.grokchat.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * 对话列表Fragment
 */
class ConversationListFragment : Fragment() {

    private var _b: FragmentConversationListBinding? = null
    private val b get() = _b!!
    
    private lateinit var adapter: ConversationListAdapter
    private var onConversationSelected: ((Conversation) -> Unit)? = null
    private var onNewConversation: (() -> Unit)? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentConversationListBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ConversationListAdapter(
            onItemClick = { conv ->
                onConversationSelected?.invoke(conv)
            },
            onDeleteClick = { conv ->
                showDeleteDialog(conv)
            }
        )
        
        b.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerView.adapter = adapter
        
        b.btnNewChat.setOnClickListener {
            onNewConversation?.invoke()
        }
        
        loadConversations()
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    private fun loadConversations() {
        val conversations = ConversationManager.getAllConversations(requireContext())
        adapter.submitList(conversations)
        
        if (conversations.isEmpty()) {
            b.tvEmptyHint.visibility = View.VISIBLE
            b.recyclerView.visibility = View.GONE
        } else {
            b.tvEmptyHint.visibility = View.GONE
            b.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showDeleteDialog(conv: Conversation) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_conversation_title)
            .setMessage(R.string.delete_conversation_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                ConversationManager.deleteConversation(requireContext(), conv.id)
                loadConversations()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun setOnConversationSelectedListener(listener: (Conversation) -> Unit) {
        onConversationSelected = listener
    }

    fun setOnNewConversationListener(listener: () -> Unit) {
        onNewConversation = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

/**
 * 对话列表适配器
 */
class ConversationListAdapter(
    private val onItemClick: (Conversation) -> Unit,
    private val onDeleteClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationListAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Conversation>() {
            override fun areItemsTheSame(a: Conversation, b: Conversation) = a.id == b.id
            override fun areContentsTheSame(a: Conversation, b: Conversation) = a == b
        }
    }

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemConversationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(conv: Conversation) {
            b.tvTitle.text = conv.title.ifEmpty { b.root.context.getString(R.string.new_conversation) }
            b.tvTime.text = dateFormat.format(Date(conv.updatedAt))
            b.tvMessageCount.text = b.root.context.getString(
                R.string.message_count, conv.messages.size
            )
            
            // 根据角色ID获取角色名称
            val role = Prefs.getRoles(b.root.context).find { it.id == conv.roleId }
            b.tvRole.text = role?.name ?: b.root.context.getString(R.string.assistant)

            b.root.setOnClickListener { onItemClick(conv) }
            b.btnDelete.setOnClickListener { onDeleteClick(conv) }
        }
    }
}
