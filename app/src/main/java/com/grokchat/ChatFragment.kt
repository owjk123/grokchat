package com.grokchat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.grokchat.databinding.FragmentChatBinding
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    val messages = mutableListOf<Message>()
}

class ChatFragment : Fragment() {

    private var _b: FragmentChatBinding? = null
    private val b get() = _b!!
    private val vm: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private var roles: MutableList<Role> = mutableListOf()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentChatBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        chatAdapter = ChatAdapter()
        b.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).also { it.stackFromEnd = true }
            adapter = chatAdapter
        }
        chatAdapter.submitList(vm.messages.toList())
        b.btnSend.setOnClickListener { send() }
        b.btnNewChat.setOnClickListener { clearChat() }
        setupRoleSpinner()
    }

    override fun onResume() {
        super.onResume()
        setupRoleSpinner()
    }

    private fun setupRoleSpinner() {
        val ctx = requireContext()
        roles = Prefs.getRoles(ctx)
        val names = roles.map { it.name }
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerRole.adapter = adapter

        val savedId = Prefs.getActiveRoleId(ctx)
        val idx = roles.indexOfFirst { it.id == savedId }.coerceAtLeast(0)
        b.spinnerRole.setSelection(idx, false)

        b.spinnerRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val selected = roles[pos]
                val prev = Prefs.getActiveRoleId(ctx)
                if (prev != selected.id) {
                    Prefs.setActiveRoleId(ctx, selected.id)
                    clearChat()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun clearChat() {
        vm.messages.clear()
        chatAdapter.submitList(emptyList())
    }

    private fun send() {
        val ctx = requireContext()
        val text = b.etInput.text.toString().trim().takeIf { it.isNotEmpty() } ?: return
        val apiKey = Prefs.getApiKey(ctx)
        if (apiKey.isEmpty()) {
            Toast.makeText(ctx, "Please set your API key in Settings", Toast.LENGTH_LONG).show()
            return
        }

        b.etInput.setText("")
        vm.messages.add(Message("user", text))
        refresh()

        b.btnSend.isEnabled = false
        b.progressBar.visibility = View.VISIBLE

        val endpoint = Prefs.getEndpoint(ctx)
        val model = Prefs.getModel(ctx)
        val activeId = Prefs.getActiveRoleId(ctx) ?: roles.firstOrNull()?.id
        val activeRole = roles.find { it.id == activeId } ?: roles.firstOrNull()

        val payload = buildList {
            if (!activeRole?.systemPrompt.isNullOrEmpty())
                add(Message("system", activeRole!!.systemPrompt))
            addAll(vm.messages)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val reply = GrokApiClient.chat(endpoint, apiKey, model, payload)
                vm.messages.add(Message("assistant", reply))
                refresh()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                b.btnSend.isEnabled = true
                b.progressBar.visibility = View.GONE
            }
        }
    }

    private fun refresh() {
        chatAdapter.submitList(vm.messages.toList())
        if (chatAdapter.itemCount > 0)
            b.recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
