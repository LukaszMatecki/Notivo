package com.lumatech.notivo.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.BottomSheetOptionsBinding
import com.lumatech.notivo.databinding.ItemOptionSheetBinding

/** Reusable rounded bottom sheet picker for settings and contextual menus. */
object NotivoOptionSheet {

    data class OptionItem(
        val id: Int,
        val title: String,
        val subtitle: String? = null,
        @param:DrawableRes val iconRes: Int? = null,
        val isSelected: Boolean = false,
        val isDestructive: Boolean = false,
        @param:FontRes val previewFontRes: Int? = null
    )

    fun show(
        context: Context,
        title: String,
        subtitle: String? = null,
        options: List<OptionItem>,
        onSelected: (OptionItem) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val sheetBinding = BottomSheetOptionsBinding.inflate(LayoutInflater.from(context))
        sheetBinding.tvSheetTitle.text = title
        sheetBinding.tvSheetSubtitle.text = subtitle
        sheetBinding.tvSheetSubtitle.isVisible = !subtitle.isNullOrBlank()

        val inflater = LayoutInflater.from(context)
        val surfaceColor = ContextCompat.getColor(context, R.color.notivo_surface)
        val selectedContainer = ContextCompat.getColor(context, R.color.notivo_primary_container)
        val selectedContent = ContextCompat.getColor(context, R.color.notivo_on_primary_container)
        val normalTitleColor = ContextCompat.getColor(context, R.color.notivo_on_surface)
        val normalVariantColor = ContextCompat.getColor(context, R.color.notivo_on_surface_variant)
        val errorColor = ContextCompat.getColor(context, R.color.notivo_error)

        options.forEach { item ->
            val row = ItemOptionSheetBinding.inflate(inflater, sheetBinding.llOptionsContainer, false)
            row.tvOptionTitle.text = item.title
            row.tvOptionSubtitle.text = item.subtitle
            row.tvOptionSubtitle.isVisible = !item.subtitle.isNullOrBlank()

            applyFontPreview(context, row, item.previewFontRes)

            val contentColor = when {
                item.isDestructive -> errorColor
                item.isSelected -> selectedContent
                else -> normalTitleColor
            }
            row.cardOptionItem.setCardBackgroundColor(
                if (item.isSelected) selectedContainer else surfaceColor
            )
            row.tvOptionTitle.setTextColor(contentColor)
            row.tvOptionSubtitle.setTextColor(
                if (item.isSelected) selectedContent else normalVariantColor
            )

            if (item.iconRes != null) {
                row.ivOptionIcon.isVisible = true
                row.ivOptionIcon.setImageResource(item.iconRes)
                row.ivOptionIcon.imageTintList = ColorStateList.valueOf(
                    if (item.isDestructive || item.isSelected) contentColor else normalVariantColor
                )
            } else {
                row.ivOptionIcon.isVisible = false
            }

            row.ivOptionCheck.isVisible = item.isSelected
            row.ivOptionCheck.imageTintList = ColorStateList.valueOf(selectedContent)

            row.cardOptionItem.setOnClickListener {
                dialog.dismiss()
                onSelected(item)
            }
            sheetBinding.llOptionsContainer.addView(row.root)
        }

        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    private fun applyFontPreview(context: Context, row: ItemOptionSheetBinding, previewFontRes: Int?) {
        if (previewFontRes == null) return
        if (previewFontRes == 0) {
            row.tvOptionTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            row.tvOptionSubtitle.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
            return
        }
        val typeface = runCatching { ResourcesCompat.getFont(context, previewFontRes) }.getOrNull() ?: return
        row.tvOptionTitle.setTypeface(typeface, Typeface.NORMAL)
        row.tvOptionSubtitle.setTypeface(typeface, Typeface.NORMAL)
    }
}
