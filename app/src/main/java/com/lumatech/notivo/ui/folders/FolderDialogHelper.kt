package com.lumatech.notivo.ui.folders

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.DialogFolderEditorBinding
import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.ui.common.styleColorSwatch
import com.lumatech.notivo.util.Constants

/**
 * Shared rounded bottom-sheet helper for creating or editing folders across screens.
 */
object FolderDialogHelper {

    fun show(
        context: Context,
        existingFolder: Folder? = null,
        onSave: (name: String, colorHex: String, isPrivate: Boolean) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val sheetBinding = DialogFolderEditorBinding.inflate(LayoutInflater.from(context))
        val initialColor = existingFolder?.colorHex ?: Constants.FOLDER_COLORS.first().hex
        var selectedColorHex = initialColor

        val titleRes = if (existingFolder == null) R.string.folder_new else R.string.folder_edit
        sheetBinding.tvFolderSheetTitle.setText(titleRes)

        if (existingFolder != null) {
            sheetBinding.etFolderName.setText(existingFolder.name)
            sheetBinding.switchFolderPrivate.isChecked = existingFolder.isPrivate
        }

        val swatchViews = mutableListOf<Pair<String, ImageView>>()
        val density = context.resources.displayMetrics.density
        val sizePx = (40 * density).toInt()
        val marginPx = (8 * density).toInt()

        Constants.FOLDER_COLORS.forEach { option ->
            val swatch = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    marginEnd = marginPx
                }
                styleColorSwatch(Color.parseColor(option.hex))
                setImageResource(R.drawable.ic_check)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = option.name
                val padding = (9 * density).toInt()
                setPadding(padding, padding, padding, padding)
            }
            swatchViews.add(option.hex to swatch)
            sheetBinding.folderColorsContainer.addView(swatch)
        }

        fun refreshSwatches() {
            swatchViews.forEach { (hex, view) ->
                view.setImageResource(
                    if (hex.equals(selectedColorHex, ignoreCase = true)) R.drawable.ic_check else 0
                )
            }
        }

        swatchViews.forEach { (hex, view) ->
            view.setOnClickListener {
                selectedColorHex = hex
                refreshSwatches()
            }
        }
        refreshSwatches()

        sheetBinding.btnCancelFolder.setOnClickListener { dialog.dismiss() }
        sheetBinding.btnSaveFolder.setOnClickListener {
            val name = sheetBinding.etFolderName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                sheetBinding.etFolderName.error = context.getString(R.string.folder_name_hint)
                sheetBinding.etFolderName.requestFocus()
                return@setOnClickListener
            }
            dialog.dismiss()
            onSave(name, selectedColorHex, sheetBinding.switchFolderPrivate.isChecked)
        }

        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }
}
