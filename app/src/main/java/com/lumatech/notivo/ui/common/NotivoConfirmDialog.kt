package com.lumatech.notivo.ui.common

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.DialogNotivoConfirmBinding

/** Rounded borderless confirmation and informational modal dialog. */
object NotivoConfirmDialog {

    fun show(
        context: Context,
        title: String,
        message: String,
        positiveText: String,
        negativeText: String? = null,
        @DrawableRes iconRes: Int? = null,
        isDestructive: Boolean = false,
        onPositive: () -> Unit = {},
        onNegative: () -> Unit = {}
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogNotivoConfirmBinding.inflate(LayoutInflater.from(context))

        binding.tvDialogTitle.text = title
        binding.tvDialogMessage.text = message
        binding.btnDialogPositive.text = positiveText

        if (negativeText != null) {
            binding.btnDialogNegative.isVisible = true
            binding.btnDialogNegative.text = negativeText
        } else {
            binding.btnDialogNegative.isVisible = false
        }

        if (iconRes != null) {
            binding.cardDialogIcon.isVisible = true
            binding.ivDialogIcon.setImageResource(iconRes)
            if (isDestructive) {
                val errorColor = ContextCompat.getColor(context, R.color.notivo_error)
                binding.cardDialogIcon.setCardBackgroundColor(ColorUtils.setAlphaComponent(errorColor, 36))
                binding.ivDialogIcon.imageTintList = ColorStateList.valueOf(errorColor)
            } else {
                binding.cardDialogIcon.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.notivo_primary_container)
                )
                binding.ivDialogIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.notivo_on_primary_container)
                )
            }
        } else {
            binding.cardDialogIcon.isVisible = false
        }

        if (isDestructive) {
            val errorColor = ContextCompat.getColor(context, R.color.notivo_error)
            binding.btnDialogPositive.backgroundTintList = ColorStateList.valueOf(errorColor)
            binding.btnDialogPositive.setTextColor(ContextCompat.getColor(context, R.color.notivo_on_primary))
        }

        binding.btnDialogNegative.setOnClickListener {
            dialog.dismiss()
            onNegative()
        }
        binding.btnDialogPositive.setOnClickListener {
            dialog.dismiss()
            onPositive()
        }

        dialog.setContentView(binding.root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }
}
