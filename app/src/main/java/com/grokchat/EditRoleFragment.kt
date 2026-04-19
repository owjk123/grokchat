package com.grokchat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.grokchat.databinding.FragmentEditRoleBinding

class EditRoleFragment : Fragment() {

    companion object {
        private const val ARG_ROLE_ID = "role_id"

        fun newInstance(roleId: String?) = EditRoleFragment().apply {
            arguments = Bundle().apply { putString(ARG_ROLE_ID, roleId) }
        }
    }

    private var _b: FragmentEditRoleBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentEditRoleBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val roleId = arguments?.getString(ARG_ROLE_ID)

        if (roleId != null) {
            val existing = Prefs.getRoles(ctx).find { it.id == roleId }
            if (existing != null) {
                b.etRoleName.setText(existing.name)
                b.etSystemPrompt.setText(existing.systemPrompt)
            }
        }

        b.btnSaveRole.setOnClickListener {
            val name = b.etRoleName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(ctx, "Role name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val systemPrompt = b.etSystemPrompt.text.toString().trim()
            val roles = Prefs.getRoles(ctx)

            if (roleId != null) {
                val idx = roles.indexOfFirst { it.id == roleId }
                if (idx >= 0) roles[idx] = Role(roleId, name, systemPrompt)
            } else {
                roles.add(Role(name = name, systemPrompt = systemPrompt))
            }
            Prefs.saveRoles(ctx, roles)
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
