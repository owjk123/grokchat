package com.grokchat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.grokchat.databinding.FragmentRolesBinding

class RolesFragment : Fragment() {

    private var _b: FragmentRolesBinding? = null
    private val b get() = _b!!
    private lateinit var roleAdapter: RoleAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentRolesBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        roleAdapter = RoleAdapter(
            onEdit = { role -> openEditRole(role.id) },
            onDelete = { role -> confirmDelete(role) }
        )
        b.recyclerRoles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = roleAdapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
        b.fabAddRole.setOnClickListener { openEditRole(null) }
        loadRoles()
    }

    override fun onResume() {
        super.onResume()
        loadRoles()
    }

    private fun loadRoles() {
        roleAdapter.submitList(Prefs.getRoles(requireContext()).toList())
    }

    private fun openEditRole(roleId: String?) {
        parentFragmentManager.commit {
            replace(R.id.fragmentContainer, EditRoleFragment.newInstance(roleId))
            addToBackStack(null)
        }
    }

    private fun confirmDelete(role: Role) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_role_title))
            .setMessage(getString(R.string.delete_role_message, role.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                val ctx = requireContext()
                val roles = Prefs.getRoles(ctx).also { it.removeAll { r -> r.id == role.id } }
                Prefs.saveRoles(ctx, roles)
                // Clear orphan active-role pref if the deleted role was active
                if (Prefs.getActiveRoleId(ctx) == role.id) Prefs.clearActiveRoleId(ctx)
                loadRoles()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
