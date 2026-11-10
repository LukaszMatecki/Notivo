package com.lumatech.notivo.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.ItemTrashNoteBinding
import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.util.DateUtils

class TrashAdapter(
    private val onRestore: (Note) -> Unit,
    private val onDeleteForever: (Note) -> Unit
) : ListAdapter<Note, TrashAdapter.TrashViewHolder>(DiffCallback) {

    private var privateFolderIds: Set<Long> = emptySet()

    fun updatePrivateFolders(ids: Set<Long>) {
        if (privateFolderIds != ids) {
            privateFolderIds = ids
            if (itemCount > 0) {
                notifyItemRangeChanged(0, itemCount)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val binding = ItemTrashNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TrashViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TrashViewHolder(
        private val binding: ItemTrashNoteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            val context = binding.root.context
            val isPrivate = note.isLocked ||
                (note.folderId != null && note.folderId in privateFolderIds)
            binding.tvTrashTitle.text = if (isPrivate) {
                context.getString(R.string.trash_private_note_title)
            } else {
                note.title.ifBlank { context.getString(R.string.note_untitled) }
            }
            binding.tvTrashDate.text =
                DateUtils.formatCardTimestamp(note.deletedAt ?: note.updatedAt)

            binding.btnRestoreNote.setOnClickListener { onRestore(note) }
            binding.btnDeleteForever.setOnClickListener { onDeleteForever(note) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean =
            oldItem == newItem
    }
}
