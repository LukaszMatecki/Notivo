package com.lumatech.notivo.ui.notes.list

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.ItemNoteCardBinding
import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.domain.model.FontSizeOption
import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.ui.common.NoteContentFormatter
import com.lumatech.notivo.ui.common.NoteImageStorage
import com.lumatech.notivo.util.DateUtils
import com.lumatech.notivo.util.NoteColorPalette

/**
 * RecyclerView adapter rendering notes in grid, list, or compact horizontal dashboard mode.
 */
class NoteAdapter(
    private val compactHorizontal: Boolean = false,
    private val onNoteClick: (Note) -> Unit,
    private val onNoteLongClick: (Note, View) -> Unit = { _, _ -> }
) : ListAdapter<Note, NoteAdapter.NoteViewHolder>(DiffCallback) {

    private var folderMap: Map<Long, Folder> = emptyMap()
    private var fontSizeOption: FontSizeOption = FontSizeOption.MEDIUM

    fun updateMetadata(folders: List<Folder>, fontSize: FontSizeOption) {
        val nextMap = folders.associateBy { it.id }
        val changed = nextMap != folderMap || fontSize != fontSizeOption
        folderMap = nextMap
        fontSizeOption = fontSize
        if (changed && itemCount > 0) {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        if (compactHorizontal) {
            val density = parent.resources.displayMetrics.density
            val widthPx = (182 * density).toInt()
            binding.root.layoutParams = RecyclerView.LayoutParams(
                widthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val marginPx = (5 * density).toInt()
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
        }
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(
        private val binding: ItemNoteCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            val context = binding.root.context
            val scale = fontSizeOption.scaleFactor

            val displayTitle = note.title.ifBlank { context.getString(R.string.note_untitled) }
            binding.tvCardTitle.text = displayTitle
            binding.tvCardTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f * scale)
            binding.tvCardPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * scale)
            binding.tvCardDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f * scale)

            binding.ivCardPin.visibility = if (note.isPinned) View.VISIBLE else View.GONE
            binding.tvCardDate.text = DateUtils.formatCardTimestamp(note.updatedAt)

            val folder = note.folderId?.let { folderMap[it] }

            val defaultSurfaceColor = MaterialColors.getColor(
                binding.root,
                com.google.android.material.R.attr.colorSurfaceContainer
            )

            if (note.isLocked) {
                binding.cardImageContainer.visibility = View.GONE
                binding.ivCardImage.setImageDrawable(null)
                binding.tvCardPreview.visibility = View.GONE
                binding.lockedPlaceholder.visibility = View.VISIBLE
                binding.noteCardRoot.setCardBackgroundColor(defaultSurfaceColor)
            } else {
                binding.lockedPlaceholder.visibility = View.GONE

                if (note.imagePaths.isNotEmpty()) {
                    binding.cardImageContainer.visibility = View.VISIBLE
                    NoteImageStorage.loadInto(binding.ivCardImage, note.imagePaths.first(), 540, 360)
                    val extraCount = note.imagePaths.size - 1
                    if (extraCount > 0) {
                        binding.tvCardImageCountBadge.visibility = View.VISIBLE
                        binding.tvCardImageCountBadge.text =
                            context.getString(R.string.card_extra_images_badge, extraCount)
                    } else {
                        binding.tvCardImageCountBadge.visibility = View.GONE
                    }
                } else {
                    binding.cardImageContainer.visibility = View.GONE
                    binding.ivCardImage.setImageDrawable(null)
                }

                when {
                    note.content.isBlank() && note.imagePaths.isNotEmpty() -> {
                        binding.tvCardPreview.visibility = View.GONE
                    }
                    note.content.isBlank() -> {
                        binding.tvCardPreview.visibility = View.VISIBLE
                        binding.tvCardPreview.setText(R.string.note_empty_preview)
                    }
                    else -> {
                        binding.tvCardPreview.visibility = View.VISIBLE
                        val mutedColor = MaterialColors.getColor(
                            binding.root,
                            com.google.android.material.R.attr.colorOnSurfaceVariant
                        )
                        binding.tvCardPreview.text = NoteContentFormatter.formatPreview(note.content, mutedColor)
                    }
                }

                val customBg = NoteColorPalette.resolveBackgroundColor(context, note.colorHex)
                binding.noteCardRoot.setCardBackgroundColor(customBg ?: defaultSurfaceColor)
            }

            binding.root.contentDescription = buildAccessibilitySummary(displayTitle, note, folder)

            binding.noteCardRoot.setOnClickListener { onNoteClick(note) }
            binding.noteCardRoot.setOnLongClickListener { v ->
                onNoteLongClick(note, v)
                true
            }
        }

        private fun buildAccessibilitySummary(
            title: String,
            note: Note,
            folder: Folder?
        ): String {
            val context = binding.root.context
            val lockLabel = if (note.isLocked) {
                context.getString(R.string.note_locked_preview)
            } else {
                ""
            }
            val folderLabel = folder?.name.orEmpty()
            return listOf(title, DateUtils.formatCardTimestamp(note.updatedAt), folderLabel, lockLabel)
                .filter { it.isNotBlank() }
                .joinToString(", ")
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean =
            oldItem == newItem
    }
}
