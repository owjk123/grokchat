package com.grokchat.pro

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.grokchat.databinding.FragmentChatBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class ChatViewModel : ViewModel() {
    val messages = mutableListOf<Message>()
}

class ChatFragment : Fragment() {

    private var _b: FragmentChatBinding? = null
    private val b get() = _b!!
    private val vm: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private var roles: MutableList<Role> = mutableListOf()
    private var pendingImageBase64: String? = null
    private var userScrolledToBottom = true
    private var newMessageHintJob: Job? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedFile(uri)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            openFilePicker()
        } else {
            Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentChatBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        chatAdapter = ChatAdapter()
        b.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).also { it.stackFromEnd = true }
            adapter = chatAdapter
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    val total = layoutManager.itemCount
                    userScrolledToBottom = lastVisible >= total - 2
                    if (userScrolledToBottom) {
                        b.tvNewMessage.visibility = View.GONE
                    }
                }
            })
        }

        b.btnSend.setOnClickListener { send() }
        b.btnNewChat.setOnClickListener { clearChat() }
        b.btnAttach.setOnClickListener { checkAndOpenFilePicker() }

        setupRoleSpinner()
        observeMessages()
        loadMessages()
    }

    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MessageStore.messages.collect { messages ->
                    val roleId = Prefs.getActiveRoleId(requireContext())
                    val role = roles.find { it.id == roleId }
                    chatAdapter.roleAvatar = role?.avatar ?: "🤖"
                    
                    val oldSize = vm.messages.size
                    vm.messages.clear()
                    vm.messages.addAll(messages)
                    
                    if (oldSize != messages.size) {
                        chatAdapter.submitList(vm.messages.toList())
                        smartScroll()
                    }
                }
            }
        }
    }

    private fun loadMessages() {
        val roleId = Prefs.getActiveRoleId(requireContext()) ?: roles.firstOrNull()?.id
        viewLifecycleOwner.lifecycleScope.launch {
            MessageStore.loadMessages(requireContext(), roleId ?: "")
        }
    }

    private fun smartScroll() {
        if (userScrolledToBottom || chatAdapter.itemCount == 0) {
            b.recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
        } else {
            // Show "new message" hint
            b.tvNewMessage.visibility = View.VISIBLE
            newMessageHintJob?.cancel()
            newMessageHintJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(5000)
                b.tvNewMessage.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupRoleSpinner()
    }

    private fun setupRoleSpinner() {
        val ctx = requireContext()
        roles = Prefs.getRoles(ctx)
        val names = roles.map { "${it.avatar} ${it.name}" }
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
                    chatAdapter.roleAvatar = selected.avatar
                    clearChat()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun clearChat() {
        viewLifecycleOwner.lifecycleScope.launch {
            MessageStore.clearAndSave(requireContext())
            vm.messages.clear()
            chatAdapter.submitList(emptyList())
        }
    }

    private fun checkAndOpenFilePicker() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            openFilePicker()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "text/*"))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }

    private fun handleSelectedFile(uri: Uri) {
        try {
            val mimeType = requireContext().contentResolver.getType(uri)
            when {
                mimeType?.startsWith("image/") == true -> {
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    
                    // Compress and encode
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
                    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
                    
                    pendingImageBase64 = base64
                    Toast.makeText(requireContext(), "Image attached", Toast.LENGTH_SHORT).show()
                }
                mimeType?.startsWith("text/") == true -> {
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    val text = inputStream?.bufferedReader()?.readText() ?: ""
                    inputStream?.close()
                    b.etInput.setText(text)
                }
                else -> {
                    Toast.makeText(requireContext(), "Unsupported file type", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
        val imageBase64 = pendingImageBase64
        pendingImageBase64 = null

        val userMessage = Message("user", text, imageBase64)
        MessageStore.addMessage(userMessage)
        vm.messages.add(userMessage)
        chatAdapter.submitList(vm.messages.toList())
        smartScroll()

        b.btnSend.isEnabled = false
        b.progressBar.visibility = View.VISIBLE

        // Start foreground service for background keep-alive
        ChatForegroundService.start(requireContext())

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
                val assistantMessage = Message("assistant", reply, null)
                MessageStore.addMessage(assistantMessage)
                vm.messages.add(assistantMessage)
                chatAdapter.submitList(vm.messages.toList())
                MessageStore.saveMessages(ctx)
                smartScroll()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                b.btnSend.isEnabled = true
                b.progressBar.visibility = View.GONE
                // Stop foreground service after message sent
                if (!ChatForegroundService.isRunning) {
                    ChatForegroundService.stop(requireContext())
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
