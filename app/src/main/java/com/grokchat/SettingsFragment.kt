package com.grokchat

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.grokchat.databinding.FragmentSettingsBinding
import java.io.ByteArrayOutputStream

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    private val pickAvatarLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { updateAvatar(it) }
        }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()

        b.etApiKey.setText(Prefs.getApiKey(ctx))

        val endpointLabels = Prefs.ENDPOINTS.map { it.second }
        b.spinnerEndpoint.adapter = ArrayAdapter(ctx, R.layout.item_spinner, endpointLabels)
            .also { it.setDropDownViewResource(R.layout.item_spinner) }
        val epIdx = Prefs.ENDPOINTS.indexOfFirst { it.first == Prefs.getEndpoint(ctx) }.coerceAtLeast(0)
        b.spinnerEndpoint.setSelection(epIdx)

        b.spinnerModel.adapter = ArrayAdapter(ctx, R.layout.item_spinner, Prefs.MODELS)
            .also { it.setDropDownViewResource(R.layout.item_spinner) }
        val mdIdx = Prefs.MODELS.indexOf(Prefs.getModel(ctx)).coerceAtLeast(0)
        b.spinnerModel.setSelection(mdIdx)

        // Setup avatar
        setupAvatar(ctx)

        b.btnSave.setOnClickListener {
            Prefs.setApiKey(ctx, b.etApiKey.text.toString().trim())
            Prefs.setEndpoint(ctx, Prefs.ENDPOINTS[b.spinnerEndpoint.selectedItemPosition].first)
            Prefs.setModel(ctx, Prefs.MODELS[b.spinnerModel.selectedItemPosition])
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupAvatar(ctx: android.content.Context) {
        // Show current avatar
        val avatarBase64 = Prefs.getGrokAvatar(ctx)
        if (avatarBase64 != null) {
            try {
                val bytes = Base64.decode(avatarBase64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                b.ivAvatar.setImageBitmap(bmp)
            } catch (e: Exception) {
                b.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
            }
        } else {
            b.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
        }

        // Avatar click to change
        b.ivAvatar.setOnClickListener {
            showAvatarOptions()
        }

        b.btnChangeAvatar.setOnClickListener {
            showAvatarOptions()
        }
    }

    private fun showAvatarOptions() {
        val ctx = requireContext()
        val hasCustomAvatar = Prefs.getGrokAvatar(ctx) != null
        
        val options = if (hasCustomAvatar) {
            arrayOf(
                getString(R.string.change_avatar),
                getString(R.string.avatar_removed)
            )
        } else {
            arrayOf(getString(R.string.change_avatar))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.change_avatar)
            .setItems(options) { _, which ->
                if (which == 0) {
                    pickAvatarLauncher.launch("image/*")
                } else if (hasCustomAvatar && which == 1) {
                    // Remove custom avatar
                    Prefs.setGrokAvatar(requireContext(), null)
                    b.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
                    Toast.makeText(ctx, R.string.avatar_removed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateAvatar(uri: Uri) {
        try {
            val ctx = requireContext()
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                val raw = stream.readBytes()
                val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return
                
                // Scale down to max 200px
                val scale = minOf(200f / bmp.width, 200f / bmp.height, 1f)
                val scaled = if (scale < 1f)
                    Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                else bmp
                
                // Compress and encode
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                
                Prefs.setGrokAvatar(ctx, base64)
                b.ivAvatar.setImageBitmap(scaled)
                Toast.makeText(ctx, R.string.avatar_updated, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.unsupported_file, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
