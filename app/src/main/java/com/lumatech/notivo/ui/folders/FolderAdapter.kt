package com.lumatech.notivo.ui.folders

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.ItemFolderCardBinding
import com.lumatech.notivo.domain.model.Folder

class FolderAdapter(
    private val onFolderClicked: (Folder) -> Unit,
    private val onFolderMoreClicked: (Folder, View) -> Unit
) : ListAdapter<Folder, FolderAdapter.FolderViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FolderViewHolder(
        private val binding: ItemFolderCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(folder: Folder) {
            val context = binding.root.context
            binding.tvFolderName.text = folder.name
            binding.tvFolderCount.text =
                context.getString(R.string.folder_note_count_plurals, folder.noteCount)

            val parsedColor = runCatching { Color.parseColor(folder.colorHex) }
                .getOrDefault(Color.parseColor("#4285F4"))
            binding.folderIconBadge.backgroundTintList = ColorStateList.valueOf(parsedColor)
            binding.ivFolderPrivateLock.visibility =
                if (folder.isPrivate) View.VISIBLE else View.GONE

            binding.folderCardRoot.setOnClickListener { onFolderClicked(folder) }
            binding.folderCardRoot.setOnLongClickListener {
                onFolderMoreClicked(folder, binding.btnFolderMore)
                true
            }
            binding.btnFolderMore.setOnClickListener { anchor ->
                onFolderMoreClicked(folder, anchor)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Folder>() {
        override fun areItemsTheSame(oldItem: Folder, newItem: Folder): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Folder, newItem: Folder): Boolean =
            oldItem == newItem
    }
}
