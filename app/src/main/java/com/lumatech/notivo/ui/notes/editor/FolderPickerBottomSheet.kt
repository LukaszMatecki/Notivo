package com.lumatech.notivo.ui.notes.editor

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.BottomSheetFolderPickerBinding
import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.ui.common.serviceLocator
import kotlinx.coroutines.launch

/**
 * Bottom sheet allowing the user to assign a note to an existing folder or create a new one.
 */
class FolderPickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFolderPickerBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFolderPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentFolderId = arguments?.getLong(ARG_CURRENT_FOLDER_ID, -1L) ?: -1L

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                serviceLocator.provideGetFoldersUseCase()().collect { folders ->
                    renderFolderList(folders, currentFolderId)
                }
            }
        }

        binding.btnCreateFolderFromSheet.setOnClickListener {
            setFragmentResult(REQ_NEW_FOLDER_FROM_PICKER, Bundle.EMPTY)
            dismiss()
        }
    }

    private fun renderFolderList(folders: List<Folder>, currentFolderId: Long) {
        binding.folderPickerList.removeAllViews()
        binding.folderPickerList.addView(
            buildRow(
                label = getString(R.string.folder_none),
                colorHex = null,
                isPrivate = false,
                isSelected = currentFolderId <= 0L
            ) {
                deliverSelection(-1L)
            }
        )

        folders.forEach { folder ->
            binding.folderPickerList.addView(
                buildRow(
                    label = folder.name,
                    colorHex = folder.colorHex,
                    isPrivate = folder.isPrivate,
                    isSelected = folder.id == currentFolderId
                ) {
                    deliverSelection(folder.id)
                }
            )
        }
    }

    private fun buildRow(
        label: String,
        colorHex: String?,
        isPrivate: Boolean,
        isSelected: Boolean,
        onClick: () -> Unit
    ): View {
        val context = requireContext()
        val density = resources.displayMetrics.density
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)

        val row = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (52 * density).toInt()
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener { onClick() }
        }

        val iconView = ImageView(context).apply {
            val size = (24 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (14 * density).toInt()
            }
            setImageResource(R.drawable.ic_folder)
            val tintColor = colorHex?.let {
                runCatching { Color.parseColor(it) }.getOrNull()
            } ?: MaterialColors.getColor(
                requireView(),
                com.google.android.material.R.attr.colorOnSurfaceVariant
            )
            imageTintList = ColorStateList.valueOf(tintColor)
        }
        row.addView(iconView)

        val titleView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = label
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        }
        row.addView(titleView)

        if (isPrivate) {
            val lockIcon = ImageView(context).apply {
                val size = (16 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (10 * density).toInt()
                }
                setImageResource(R.drawable.ic_lock)
                imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(
                        requireView(),
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
            }
            row.addView(lockIcon)
        }

        if (isSelected) {
            val checkIcon = ImageView(context).apply {
                val size = (20 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
                setImageResource(R.drawable.ic_check)
                imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(
                        requireView(),
                        androidx.appcompat.R.attr.colorPrimary
                    )
                )
            }
            row.addView(checkIcon)
        }

        return row
    }

    private fun deliverSelection(folderId: Long) {
        setFragmentResult(
            REQ_FOLDER_PICKED,
            Bundle().apply { putLong(EXTRA_FOLDER_ID, folderId) }
        )
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FolderPickerBottomSheet"
        const val REQ_FOLDER_PICKED = "req_folder_picked"
        const val REQ_NEW_FOLDER_FROM_PICKER = "req_new_folder_from_picker"
        const val EXTRA_FOLDER_ID = "extra_folder_id"
        private const val ARG_CURRENT_FOLDER_ID = "arg_current_folder_id"

        fun newInstance(currentFolderId: Long?): FolderPickerBottomSheet =
            FolderPickerBottomSheet().apply {
                arguments = Bundle().apply { putLong(ARG_CURRENT_FOLDER_ID, currentFolderId ?: -1L) }
            }
    }
}
