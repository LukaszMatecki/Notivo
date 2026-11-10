package com.lumatech.notivo.ui.security

import android.animation.ObjectAnimator
import android.view.HapticFeedbackConstants
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.FragmentPinInputBinding
import com.lumatech.notivo.ui.common.areAnimationsEnabled
import com.lumatech.notivo.ui.common.performHapticSafe
import com.lumatech.notivo.ui.common.performRejectHaptic

/**
 * Shared UI binder for the PIN keypad, dot indicators, shake feedback, and haptics.
 */
object PinUiBinder {

    fun bindKeypad(
        binding: FragmentPinInputBinding,
        onDigit: (Int) -> Unit,
        onBackspace: () -> Unit
    ) {
        val digitButtons = listOf(
            binding.key0 to 0,
            binding.key1 to 1,
            binding.key2 to 2,
            binding.key3 to 3,
            binding.key4 to 4,
            binding.key5 to 5,
            binding.key6 to 6,
            binding.key7 to 7,
            binding.key8 to 8,
            binding.key9 to 9
        )

        digitButtons.forEach { (button, digit) ->
            button.contentDescription = button.context.getString(R.string.pin_digit_desc, digit.toString())
            button.setOnClickListener { view ->
                view.performHapticSafe(HapticFeedbackConstants.KEYBOARD_TAP)
                onDigit(digit)
            }
        }

        binding.keyBackspace.setOnClickListener { view ->
            view.performHapticSafe(HapticFeedbackConstants.KEYBOARD_TAP)
            onBackspace()
        }
    }

    fun renderDots(
        binding: FragmentPinInputBinding,
        filledCount: Int,
        isError: Boolean
    ) {
        val dots: List<ImageView> = listOf(
            binding.pinDot1,
            binding.pinDot2,
            binding.pinDot3,
            binding.pinDot4
        )
        val animated = binding.root.context.areAnimationsEnabled()

        dots.forEachIndexed { index, dot ->
            val wasFilled = dot.tag as? Boolean ?: false
            val isNowFilled = index < filledCount
            dot.tag = isNowFilled

            val drawableRes = when {
                isError -> R.drawable.bg_pin_dot_error
                isNowFilled -> R.drawable.bg_pin_dot_filled
                else -> R.drawable.bg_pin_dot_empty
            }
            dot.setImageResource(drawableRes)

            if (!wasFilled && isNowFilled && animated) {
                dot.scaleX = 0.6f
                dot.scaleY = 0.6f
                dot.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120L)
                    .setInterpolator(OvershootInterpolator(1.6f))
                    .start()
            }
        }
    }

    fun playErrorShake(binding: FragmentPinInputBinding) {
        binding.root.performRejectHaptic()
        if (!binding.root.context.areAnimationsEnabled()) return
        val density = binding.root.resources.displayMetrics.density
        val delta = 10f * density
        ObjectAnimator.ofFloat(
            binding.pinDotsContainer,
            "translationX",
            0f, -delta, delta, -delta, delta, -delta / 2f, delta / 2f, 0f
        ).apply {
            duration = 320L
            start()
        }
    }

    fun setKeypadEnabled(binding: FragmentPinInputBinding, enabled: Boolean) {
        val buttons = listOf(
            binding.key0, binding.key1, binding.key2, binding.key3, binding.key4,
            binding.key5, binding.key6, binding.key7, binding.key8, binding.key9,
            binding.keyBackspace
        )
        buttons.forEach { it.isEnabled = enabled }
    }
}
