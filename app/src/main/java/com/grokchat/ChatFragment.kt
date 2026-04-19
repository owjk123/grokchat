package com.grokchat

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.grokchat.databinding.FragmentChatBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ChatViewModel : ViewModel() {
    val messages = mutableListOf<Message>()
    var conversationId: String? = null
}

class ChatFragment : Fragment() {

    private var _b: FragmentChatBinding? = null
    private val b get() = _b!!
    private val vm: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private var roles: MutableList<Role> = mutableListOf()

    private var pendingImageBase64: String? = null
    private var pendingImageMime: String = "image/jpeg"
    private var pendingFileContent: String? = null
    private var pendingFileName: String? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { processImage(it) }
        }

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { processFile(it) }
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
        }
        
        // Load existing conversation or create new one
        loadOrCreateConversation()
        
        chatAdapter.submitList(vm.messages.toList())
        b.btnSend.setOnClickListener { send() }
        b.btnNewChat.setOnClickListener { startNewConversation() }
        b.btnAttach.setOnClickListener { showAttachMenu(it) }
        b.btnRemoveAttachment.setOnClickListener { clearAttachment() }
        setupRoleSelector()
    }

    override fun onPause() {
        super.onPause()
        saveCurrentConversation()
    }

    override fun onResume() {
        super.onResume()
        setupRoleSelector()
    }

    private fun loadOrCreateConversation() {
        val ctx = requireContext()
        val currentConvId = Prefs.getCurrentConversationId(ctx)
        
        if (currentConvId != null) {
            // Load existing conversation
            val conversation = ConversationManager.getConversation(ctx, currentConvId)
            if (conversation != null) {
                vm.conversationId = conversation.id
                vm.messages.clear()
                vm.messages.addAll(conversation.messages)
                chatAdapter.submitList(vm.messages.toList())
                return
            }
        }
        
        // Create new conversation
        startNewConversation()
    }

    private fun startNewConversation() {
        saveCurrentConversation()
        
        val ctx = requireContext()
        val activeRoleId = Prefs.getActiveRoleId(ctx) ?: roles.firstOrNull()?.id ?: ""
        
        val conversation = ConversationManager.createNewConversation(ctx, activeRoleId)
        vm.conversationId = conversation.id
        Prefs.setCurrentConversationId(ctx, conversation.id)
        
        vm.messages.clear()
        chatAdapter.submitList(emptyList())
    }

    private fun saveCurrentConversation() {
        val convId = vm.conversationId ?: return
        if (vm.messages.isEmpty()) {
            // Don't save empty conversations
            ConversationManager.deleteConversation(requireContext(), convId)
            return
        }
        
        // Generate title from first message if empty
        val firstUserMessage = vm.messages.firstOrNull { it.role == "user" }
        val title = if (firstUserMessage != null) {
            ConversationManager.generateTitle(firstUserMessage.content)
        } else {
            ""
        }
        
        val conversation = Conversation(
            id = convId,
            title = title,
            roleId = Prefs.getActiveRoleId(requireContext()) ?: "",
            messages = vm.messages.toList()
        )
        ConversationManager.saveConversation(requireContext(), conversation)
    }

    private fun setupRoleSelector() {
        val ctx = requireContext()
        roles = Prefs.getRoles(ctx)
        val activeId = Prefs.getActiveRoleId(ctx) ?: roles.firstOrNull()?.id
        val activeRole = roles.find { it.id == activeId } ?: roles.firstOrNull()
        b.tvCurrentRole.text = activeRole?.name ?: "助手"

        b.tvCurrentRole.setOnClickListener { anchor ->
            val popup = PopupMenu(ctx, anchor)
            roles.forEachIndexed { idx, role -> popup.menu.add(0, idx, idx, role.name) }
            popup.setOnMenuItemClickListener { item ->
                val selected = roles[item.itemId]
                val prev = Prefs.getActiveRoleId(ctx)
                if (prev != selected.id) {
                    Prefs.setActiveRoleId(ctx, selected.id)
                    b.tvCurrentRole.text = selected.name
                    startNewConversation()
                }
                true
            }
            popup.show()
        }
    }

    private fun clearChat() {
        vm.messages.clear()
        chatAdapter.submitList(emptyList())
    }

    // ── Attachment ──────────────────────────────────────────────────────────

    private fun showAttachMenu(anchor: View) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.attach))
            .setItems(arrayOf(
                getString(R.string.attach_image),
                getString(R.string.attach_file)
            )) { _, which ->
                if (which == 0) pickImageLauncher.launch("image/*")
                else pickFileLauncher.launch("text/*")
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun processImage(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { encodeImage(uri) }
            if (result != null) {
                pendingImageBase64 = result.first
                pendingImageMime = result.second
                pendingFileContent = null
                pendingFileName = getFileName(uri) ?: "image"
                showAttachPreview(isImage = true)
            } else {
                Toast.makeText(requireContext(), getString(R.string.unsupported_file), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processFile(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { readTextFile(uri) }
            if (result != null) {
                pendingFileContent = result
                pendingImageBase64 = null
                pendingFileName = getFileName(uri) ?: "file"
                showAttachPreview(isImage = false)
            } else {
                Toast.makeText(requireContext(), getString(R.string.attachment_too_large), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun encodeImage(uri: Uri): Pair<String, String>? = try {
        val ctx = requireContext()
        val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
        ctx.contentResolver.openInputStream(uri)?.use { stream ->
            val raw = stream.readBytes()
            val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
            // Scale down to max 800px on longest side
            val scale = minOf(800f / bmp.width, 800f / bmp.height, 1f)
            val scaled = if (scale < 1f)
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            else bmp
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            Pair(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), "image/jpeg")
        }
    } catch (e: Exception) { null }

    private fun readTextFile(uri: Uri): String? = try {
        val ctx = requireContext()
        ctx.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > 50_000) null // 50KB limit
            else bytes.toString(Charsets.UTF_8)
        }
    } catch (e: Exception) { null }

    private fun getFileName(uri: Uri): String? {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (idx >= 0) it.getString(idx) else null
        }
    }

    private fun showAttachPreview(isImage: Boolean) {
        b.attachmentPreview.visibility = View.VISIBLE
        b.tvPreviewName.text = pendingFileName
        if (isImage && pendingImageBase64 != null) {
            try {
                val bytes = Base64.decode(pendingImageBase64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                b.ivPreviewThumb.setImageBitmap(bmp)
                b.ivPreviewThumb.visibility = View.VISIBLE
            } catch (e: Exception) { b.ivPreviewThumb.visibility = View.GONE }
        } else {
            b.ivPreviewThumb.visibility = View.GONE
        }
    }

    private fun clearAttachment() {
        pendingImageBase64 = null
        pendingFileContent = null
        pendingFileName = null
        b.attachmentPreview.visibility = View.GONE
    }

    // ── Send ───────────────────────────────────────────────────────────────

    private fun send() {
        val input = b.etMessage.text?.toString()?.trim() ?: ""
        if (input.isEmpty() && pendingImageBase64 == null && pendingFileContent == null) return

        val apiKey = Prefs.getApiKey(requireContext())
        if (apiKey.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.please_set_api_key), Toast.LENGTH_SHORT).show()
            return
        }

        // Build user message
        val userContent = buildString {
            if (input.isNotEmpty()) append(input)
            if (pendingFileContent != null) {
                if (isNotEmpty()) append("\n\n")
                append("[文件内容]\n").append(pendingFileContent)
            }
        }

        val userMsg = Message(
            role = "user",
            content = userContent,
            imageBase64 = pendingImageBase64,
            imageMimeType = pendingImageMime
        )

        vm.messages.add(userMsg)
        chatAdapter.submitList(vm.messages.toList())
        scrollToBottom()

        clearAttachment()
        b.etMessage.text?.clear()

        // Save conversation after user sends message
        saveCurrentConversation()

        // Build assistant message placeholder
        val assistantMsg = Message(role = "assistant", content = "")
        vm.messages.add(assistantMsg)
        chatAdapter.submitList(vm.messages.toList())
        scrollToBottom()

        b.progressBar.visibility = View.VISIBLE
        b.btnSend.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ctx = requireContext()
                val roleId = Prefs.getActiveRoleId(ctx) ?: ""
                val role = roles.find { it.id == roleId }
                val systemPrompt = role?.systemPrompt ?: ""

                val reply = withContext(Dispatchers.IO) {
                    GrokApiClient.chat(
                        apiKey = apiKey,
                        messages = vm.messages.dropLast(1).toList(), // Exclude placeholder
                        systemPrompt = systemPrompt,
                        endpoint = Prefs.getEndpoint(ctx),
                        model = Prefs.getModel(ctx)
                    )
                }

                if (reply != null) {
                    val lastIdx = vm.messages.lastIndex
                    vm.messages[lastIdx] = assistantMsg.copy(content = reply)
                    chatAdapter.submitList(vm.messages.toList())
                    scrollToBottom()
                    
                    // Save conversation after assistant responds
                    saveCurrentConversation()
                } else {
                    vm.messages.removeAt(lastIdx)
                    chatAdapter.submitList(vm.messages.toList())
                }
            } catch (e: Exception) {
                val lastIdx = vm.messages.lastIndex
                vm.messages.removeAt(lastIdx)
                chatAdapter.submitList(vm.messages.toList())
                Toast.makeText(requireContext(), e.message ?: "Error", Toast.LENGTH_SHORT).show()
            } finally {
                b.progressBar.visibility = View.GONE
                b.btnSend.isEnabled = true
            }
        }
    }

    private fun scrollToBottom() {
        b.recyclerView.post {
            if (vm.messages.isNotEmpty()) {
                b.recyclerView.smoothScrollToPosition(vm.messages.size - 1)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
