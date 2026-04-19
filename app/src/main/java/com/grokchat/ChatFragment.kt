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
        chatAdapter.submitList(vm.messages.toList())
        b.btnSend.setOnClickListener { send() }
        b.btnNewChat.setOnClickListener { clearChat() }
        b.btnAttach.setOnClickListener { showAttachMenu(it) }
        b.btnRemoveAttachment.setOnClickListener { clearAttachment() }
        setupRoleSelector()
    }

    override fun onResume() {
        super.onResume()
        setupRoleSelector()
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
                    clearChat()
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
        b.ivPreviewThumb.setImageBitmap(null)
    }

    // ── Send ─────────────────────────────────────────────────────────────────

    private fun send() {
        val ctx = requireContext()
        val text = b.etInput.text.toString().trim()
        val hasAttachment = pendingImageBase64 != null || pendingFileContent != null
        if (text.isEmpty() && !hasAttachment) return

        val apiKey = Prefs.getApiKey(ctx)
        if (apiKey.isEmpty()) {
            Toast.makeText(ctx, getString(R.string.please_set_api_key), Toast.LENGTH_LONG).show()
            return
        }

        // Build message with optional attachment
        val messageText = when {
            pendingFileContent != null -> {
                val content = "📄 $pendingFileName\n\n$pendingFileContent"
                if (text.isNotEmpty()) "$text\n\n$content" else content
            }
            else -> text
        }
        val userMsg = Message("user", messageText, pendingImageBase64, pendingImageMime.takeIf { pendingImageBase64 != null })

        b.etInput.setText("")
        clearAttachment()
        vm.messages.add(userMsg)
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
                Toast.makeText(ctx, "错误：${e.message}", Toast.LENGTH_LONG).show()
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
