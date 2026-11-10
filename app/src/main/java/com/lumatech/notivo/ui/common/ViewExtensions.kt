package com.lumatech.notivo.ui.common

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StrikethroughSpan
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lumatech.notivo.NotivoApplication
import com.lumatech.notivo.R
import com.lumatech.notivo.di.ServiceLocator
import com.lumatech.notivo.domain.model.TaskPriority
import com.lumatech.notivo.util.BulletListHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Provides access to the application-level [ServiceLocator] from any Fragment.
 */
val Fragment.serviceLocator: ServiceLocator
    get() = (requireActivity().application as NotivoApplication).serviceLocator

/**
 * Lifecycle-aware Flow collection active only while the Fragment's view is STARTED.
 */
inline fun <T> Fragment.collectWithLifecycle(
    flow: Flow<T>,
    crossinline onCollect: suspend (T) -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { value -> onCollect(value) }
        }
    }
}

/**
 * Navigates only if the Fragment is currently attached and at a different destination,
 * preventing duplicate-tap IllegalArgumentException crashes in NavController.
 */
fun Fragment.navigateSafely(@IdRes destinationId: Int, args: Bundle? = null) {
    if (!isAdded) return
    val navController = runCatching { findNavController() }.getOrNull() ?: return
    if (navController.currentDestination?.id != destinationId) {
        runCatching { navController.navigate(destinationId, args) }
    }
}

fun View.performHapticSafe(feedbackConstant: Int = HapticFeedbackConstants.KEYBOARD_TAP) {
    performHapticFeedback(feedbackConstant)
}

fun View.performConfirmHaptic() {
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.KEYBOARD_TAP
    }
    performHapticFeedback(constant)
}

fun View.performRejectHaptic() {
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.REJECT
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constant)
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
}

fun Context.areAnimationsEnabled(): Boolean {
    val scale = Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    return scale > 0f
}

/**
 * Formats bullet lists (`• `) and legacy checklist markers (`- [ ]` / `- [x]`) into styled
 * note card previews.
 */
object NoteContentFormatter {
    fun formatPreview(rawContent: String, @ColorInt completedColor: Int): CharSequence {
        val lines = rawContent.lines()
        val builder = SpannableStringBuilder()
        lines.forEachIndexed { index, line ->
            val trimmed = line.trimStart()
            val start = builder.length
            val parsedBullet = BulletListHelper.parseLine(line)
            when {
                parsedBullet.hasBullet -> {
                    val indentLevel = (parsedBullet.indentSpaces / BulletListHelper.INDENT_UNIT.length)
                        .coerceAtMost(3)
                    val firstMargin = indentLevel * 18
                    val restMargin = firstMargin + 16
                    builder.append("${BulletListHelper.BULLET_PREFIX}${parsedBullet.body}")
                    builder.setSpan(
                        LeadingMarginSpan.Standard(firstMargin, restMargin),
                        start,
                        builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                trimmed.startsWith("- [x] ", ignoreCase = true) -> {
                    val itemText = trimmed.drop(6)
                    builder.append("☑ ").append(itemText)
                    builder.setSpan(
                        StrikethroughSpan(),
                        start + 2,
                        builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        ForegroundColorSpan(completedColor),
                        start,
                        builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                trimmed.startsWith("- [ ] ") -> {
                    val itemText = trimmed.drop(6)
                    builder.append("☐ ").append(itemText)
                }
                else -> builder.append(line)
            }
            if (index < lines.lastIndex) {
                builder.append('\n')
            }
        }
        return builder
    }
}

fun TextView.setStrikethrough(enabled: Boolean) {
    paintFlags = if (enabled) {
        paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
    } else {
        paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
    }
}

@ColorRes
fun TaskPriority.colorRes(): Int = when (this) {
    TaskPriority.HIGH -> R.color.priority_high
    TaskPriority.MEDIUM -> R.color.priority_medium
    TaskPriority.LOW -> R.color.priority_low
}

/**
 * Configures a flat circular color swatch with a subtle hairline border so light swatches remain
 * clearly separated against light surfaces without drop shadows.
 */
fun ImageView.styleColorSwatch(
    @ColorInt fillColor: Int,
    @ColorInt strokeColor: Int = ContextCompat.getColor(context, R.color.notivo_outline)
) {
    val density = resources.displayMetrics.density
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fillColor)
        setStroke((1.25f * density).toInt().coerceAtLeast(1), strokeColor)
    }
    elevation = 0f
}
