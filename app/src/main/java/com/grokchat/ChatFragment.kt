package com.grokchat

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grokchat.databinding.FragmentChatBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatFragment : Fragment() {

    private var _b: FragmentChatBinding? = null
    private val b get() = _b!!
    private val vm: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private var roles: MutableList<Role> = mutableListOf()

    private var pendingImagePath: String? = null
    private var pendingImageMime: String = "image/jpeg"
    private var pendingFileContent: String? = null
    private var pendingFileName: String? = null

    // Flag that forces scroll-to-bottom on the next list update (user just sent)
    private var forceScrollOnNext: Boolean = false

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
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (isNearBottom()) hideNewMessageChip()
                }
            })
        }
        b.btnSend.setOnClickListener { send() }
        b.btnNewChat.setOnClickListener { confirmClearChat() }
        b.btnAttach.setOnClickListener { showAttachMenu() }
        b.btnRemoveAttachment.setOnClickListener { clearAttachment() }
        b.chipNewMessage.setOnClickListener {
            scrollToBottom()
            hideNewMessageChip()
        }
        setupRoleSelector()

        vm.loadIfNeeded()

        // Observe messages and update adapter with smart-scroll policy
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.messages.collect { list ->
                    val wasForced = forceScrollOnNext
                    val wasNearBottom = isNearBottom()
                    forceScrollOnNext = false
                    chatAdapter.submitList(list) {
                        if (_b == null) return@submitList
                        when {
                            wasForced -> scrollToBottom()
                            wasNearBottom -> scrollToBottom()
                            list.isNotEmpty() && list.last().role == "assistant" ->
                                showNewMessageChip()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupRoleSelector()
    }

    override fun onPause() {
        // Ensure in-flight messages are durable before process death
        vm.flushSync()
        super.onPause()
    }

    // ── Role selector ───────────────────────────────────────────────────────

    private fun setupRoleSelector() {
        val ctx = requireContext()
        roles = Prefs.getRoles(ctx)
        val activeId = Prefs.getActiveRoleId(ctx) ?: roles.firstOrNull()?.id
        val activeRole = roles.find { it.id == activeId } ?: roles.firstOrNull()
        b.tvCurrentRole.text = activeRole?.name ?: "助手"
        b.tvAvatar.text = activeRole?.avatar ?: "🎭"

        val showPopup: (View) -> Unit = { anchor ->
            val popup = PopupMenu(ctx, anchor)
            roles.forEachIndexed { idx, role ->
                popup.menu.add(0, idx, idx, "${role.avatar}  ${role.name}")
            }
            popup.setOnMenuItemClickListener { item ->
                val idx = item.itemId
                if (idx in roles.indices) {
                    val selected = roles[idx]
                    val prev = Prefs.getActiveRoleId(ctx)
                    if (prev != selected.id) {
                        Prefs.setActiveRoleId(ctx, selected.id)
                        b.tvCurrentRole.text = selected.name
                        b.tvAvatar.text = selected.avatar
                        clearChat(silent = true)
                    }
                }
                true
            }
            popup.show()
        }
        b.roleHeader.setOnClickListener(showPopup)
    }

    private fun confirmClearChat() {
        if (vm.messages.value.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.new_chat))
            .setMessage(getString(R.string.confirm_new_chat))
            .setPositiveButton(getString(R.string.new_chat)) { _, _ -> clearChat(silent = false) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun clearChat(silent: Boolean) {
        vm.clear()
        if (!silent) hideNewMessageChip()
    }

    // ── Attachment ──────────────────────────────────────────────────────────

    private fun showAttachMenu() {
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
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = encodeAndSaveImage(ctx, uri)
            if (!isAdded || _b == null) return@launch
            if (result != null) {
                pendingImagePath = result.first
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
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { readTextFileInternal(ctx, uri) }
            if (!isAdded || _b == null) return@launch
            if (result != null) {
                pendingFileContent = result
                pendingImagePath = null
                pendingFileName = getFileName(uri) ?: "file"
                showAttachPreview(isImage = false)
            } else {
                Toast.makeText(requireContext(), getString(R.string.attachment_too_large), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun encodeAndSaveImage(ctx: Context, uri: Uri): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    val raw = stream.readBytes()
                    val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                        ?: return@use null
                    val scale = minOf(800f / bmp.width, 800f / bmp.height, 1f)
                    val scaled = if (scale < 1f)
                        Bitmap.createScaledBitmap(
                            bmp,
                            (bmp.width * scale).toInt(),
                            (bmp.height * scale).toInt(),
                            true
                        )
                    else bmp
                    val path = MessageStore.saveImage(ctx, scaled) ?: return@use null
                    Pair(path, "image/jpeg")
                }
            } catch (e: Exception) { null }
        }

    private fun readTextFileInternal(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > 50_000) null
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
        if (isImage && pendingImagePath != null) {
            try {
                val bmp = BitmapFactory.decodeFile(pendingImagePath)
                b.ivPreviewThumb.setImageBitmap(bmp)
                b.ivPreviewThumb.visibility = View.VISIBLE
            } catch (e: Exception) { b.ivPreviewThumb.visibility = View.GONE }
        } else {
            b.ivPreviewThumb.visibility = View.GONE
        }
    }

    private fun clearAttachment() {
        pendingImagePath = null
        pendingFileContent = null
        pendingFileName = null
        b.attachmentPreview.visibility = View.GONE
        b.ivPreviewThumb.setImageBitmap(null)
    }

    // ── Send ─────────────────────────────────────────────────────────────────

    private fun send() {
        val ctx = requireContext()
        val text = b.etInput.text.toString().trim()
        val hasAttachment = pendingImagePath != null || pendingFileContent != null
        if (text.isEmpty() && !hasAttachment) return

        val apiKey = Prefs.getApiKey(ctx)
        if (apiKey.isEmpty()) {
            Toast.makeText(ctx, getString(R.string.please_set_api_key), Toast.LENGTH_LONG).show()
            return
        }

        if (!isNetworkAvailable(ctx)) {
            Toast.makeText(ctx, getString(R.string.error_no_network), Toast.LENGTH_LONG).show()
            return
        }

        val messageText = when {
            pendingFileContent != null -> {
                val content = "📄 $pendingFileName\n\n$pendingFileContent"
                if (text.isNotEmpty()) "$text\n\n$content" else content
            }
            else -> text
        }
        val userMsg = Message(
            role = "user",
            content = messageText,
            imagePath = pendingImagePath,
            imageMimeType = pendingImageMime.takeIf { pendingImagePath != null }
        )

        b.etInput.setText("")
        clearAttachment()
        forceScrollOnNext = true
        vm.addMessage(userMsg)

        b.btnSend.isEnabled = false
        b.progressBar.visibility = View.VISIBLE

        val endpoint = Prefs.getEndpoint(ctx)
        val model = Prefs.getModel(ctx)
        val activeId = Prefs.getActiveRoleId(ctx) ?: roles.firstOrNull()?.id
        val activeRole = roles.find { it.id == activeId } ?: roles.firstOrNull()

        val payload = buildList {
            if (!activeRole?.systemPrompt.isNullOrEmpty())
                add(Message("system", activeRole!!.systemPrompt))
            addAll(vm.messages.value)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val reply = try {
                GrokApiClient.chat(endpoint, apiKey, model, payload)
            } catch (e: ApiException) {
                if (!isAdded || _b == null) return@launch
                Toast.makeText(requireContext(), friendlyError(e), Toast.LENGTH_LONG).show()
                b.btnSend.isEnabled = true
                b.progressBar.visibility = View.GONE
                return@launch
            } catch (e: Exception) {
                if (!isAdded || _b == null) return@launch
                Toast.makeText(requireContext(), getString(R.string.error_unknown), Toast.LENGTH_LONG).show()
                b.btnSend.isEnabled = true
                b.progressBar.visibility = View.GONE
                return@launch
            }

            if (!isAdded || _b == null) return@launch
            vm.addMessage(Message("assistant", reply))
            b.btnSend.isEnabled = true
            b.progressBar.visibility = View.GONE
        }
    }

    private fun friendlyError(e: ApiException): String = when (e.friendlyKey) {
        ApiException.Kind.AUTH       -> getString(R.string.error_auth)
        ApiException.Kind.RATE_LIMIT -> getString(R.string.error_rate_limit)
        ApiException.Kind.SERVER     -> getString(R.string.error_server, e.httpCode)
        ApiException.Kind.NETWORK    -> getString(R.string.error_network)
        ApiException.Kind.PARSE      -> getString(R.string.error_parse)
        ApiException.Kind.UNKNOWN    -> getString(R.string.error_unknown)
    }

    private fun isNetworkAvailable(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true  // If we can't check, assume available
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── Scroll helpers ──────────────────────────────────────────────────────

    private fun isNearBottom(): Boolean {
        val rv = _b?.recyclerView ?: return true
        if (rv.childCount == 0) return true
        val thresholdPx = dpToPx(240)
        val remaining = rv.computeVerticalScrollRange() -
            rv.computeVerticalScrollExtent() -
            rv.computeVerticalScrollOffset()
        return remaining < thresholdPx
    }

    private fun scrollToBottom() {
        val rv = _b?.recyclerView ?: return
        val count = chatAdapter.itemCount
        if (count == 0) return
        (rv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(count - 1, 0)
        // After layout, scrollBy reaches the true bottom (handles very tall messages)
        rv.post {
            if (_b == null) return@post
            rv.scrollBy(0, Int.MAX_VALUE)
        }
    }

    private fun showNewMessageChip() {
        _b?.chipNewMessage?.visibility = View.VISIBLE
    }

    private fun hideNewMessageChip() {
        _b?.chipNewMessage?.visibility = View.GONE
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics
    ).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
