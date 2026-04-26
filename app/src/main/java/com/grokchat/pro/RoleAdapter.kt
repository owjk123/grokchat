package com.grokchat.pro

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.grokchat.pro.databinding.ItemRoleBinding

class RoleAdapter(
    private val onEdit: (Role) -> Unit,
    private val onDelete: (Role) -> Unit
) : ListAdapter<Role, RoleAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Role>() {
            override fun areItemsTheSame(a: Role, b: Role) = a.id == b.id
            override fun areContentsTheSame(a: Role, b: Role) = a == b
        }
    }

    inner class VH(val binding: ItemRoleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(role: Role) {
            binding.tvRoleAvatar.text = role.avatar
            binding.tvRoleName.text = role.name
            binding.btnEdit.setOnClickListener { onEdit(role) }
            binding.btnDelete.setOnClickListener { onDelete(role) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRoleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
