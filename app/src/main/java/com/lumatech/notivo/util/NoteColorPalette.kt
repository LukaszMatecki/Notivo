package com.lumatech.notivo.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * Provides pastel note background color themes with automatic Light/Dark mode adaptation.
 */
object NoteColorPalette {
    data class ColorOption(
        val name: String,
        val lightHex: String,
        val darkHex: String
    )

    val options: List<ColorOption> = listOf(
        ColorOption("Biel", "#FFFFFF", "#201F24"),
        ColorOption("Masłowy", "#FFF8E1", "#3E3520"),
        ColorOption("Miodowy", "#FFECB3", "#423118"),
        ColorOption("Brzoskwiniowy", "#FBE9E7", "#3E2A20"),
        ColorOption("Łososiowy", "#FFEBEE", "#3E2023"),
        ColorOption("Różany", "#FCE4EC", "#3B1E2E"),
        ColorOption("Lawendowy", "#F3E5F5", "#2E1F3E"),
        ColorOption("Indygo", "#E8EAF6", "#22253E"),
        ColorOption("Błękitny", "#E3F2FD", "#1A2E3E"),
        ColorOption("Turkusowy", "#E0F2F1", "#183534"),
        ColorOption("Miętowy", "#E8F5E9", "#1B3320"),
        ColorOption("Piaskowy", "#EFEBE9", "#2F2B2A")
    )

    @ColorInt
    fun resolveBackgroundColor(context: Context, lightHex: String?): Int? {
        if (lightHex.isNullOrBlank()) return null
        val isDark = isDarkThemeActive(context)
        val matched = options.firstOrNull { it.lightHex.equals(lightHex, ignoreCase = true) }
        val resolvedHex = when {
            matched != null && isDark -> matched.darkHex
            matched != null -> matched.lightHex
            else -> lightHex
        }
        return runCatching { Color.parseColor(resolvedHex) }.getOrNull()
    }

    fun isDarkThemeActive(context: Context): Boolean {
        val nightModeFlags =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}
