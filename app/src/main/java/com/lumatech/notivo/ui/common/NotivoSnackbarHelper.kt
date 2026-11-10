package com.lumatech.notivo.ui.common

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.snackbar.Snackbar
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.ViewCustomSnackbarBinding
import java.lang.ref.WeakReference

/**
 * Displays rounded Notivo pill notifications with fluid center-circle collapse exit animation.
 */
object NotivoSnackbarHelper {

    private var activeContainerRef: WeakReference<View>? = null
    private var activeDismissRunnable: Runnable? = null
    private var activeExitAnimator: AnimatorSet? = null

    fun show(
        view: View,
        @StringRes messageRes: Int,
        @DrawableRes iconRes: Int = R.drawable.ic_check,
        isError: Boolean = false,
        duration: Int = Snackbar.LENGTH_SHORT,
        anchorView: View? = null,
        slideUpOnExit: Boolean = false,
        @StringRes actionLabelRes: Int? = null,
        onAction: (() -> Unit)? = null
    ) {
        val actionLabel = actionLabelRes?.let { view.context.getString(it) }
        show(
            view = view,
            message = view.context.getString(messageRes),
            iconRes = iconRes,
            isError = isError,
            duration = duration,
            anchorView = anchorView,
            slideUpOnExit = slideUpOnExit,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }

    fun show(
        view: View,
        message: String,
        @DrawableRes iconRes: Int = R.drawable.ic_check,
        isError: Boolean = false,
        duration: Int = Snackbar.LENGTH_SHORT,
        anchorView: View? = null,
        slideUpOnExit: Boolean = false,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        val rootContent = view.rootView.findViewById<ViewGroup>(android.R.id.content) ?: return
        removeActiveImmediately(rootContent)

        val itemBinding = ViewCustomSnackbarBinding.inflate(
            LayoutInflater.from(view.context),
            rootContent,
            false
        )
        itemBinding.tvSnackbarMessage.text = message
        itemBinding.snackbarCard.contentDescription = message

        val effectiveIcon = if (isError && iconRes == R.drawable.ic_check) R.drawable.ic_info else iconRes
        val iconTint = ContextCompat.getColor(
            view.context,
            if (isError) R.color.notivo_error else R.color.notivo_snackbar_action
        )
        itemBinding.ivSnackbarIcon.setImageResource(effectiveIcon)
        itemBinding.ivSnackbarIcon.imageTintList = ColorStateList.valueOf(iconTint)

        if (!actionLabel.isNullOrBlank() && onAction != null) {
            itemBinding.btnSnackbarAction.isVisible = true
            itemBinding.btnSnackbarAction.text = actionLabel
            itemBinding.btnSnackbarAction.setOnClickListener {
                onAction()
                dismissAnimated(rootContent, itemBinding, slideUpOnExit)
            }
        } else {
            itemBinding.btnSnackbarAction.isVisible = false
        }

        val resolvedAnchor = anchorView?.takeIf { it.isShown }
            ?: view.rootView.findViewById<View>(R.id.bottomNavigation)?.takeIf { it.isVisible }

        val density = view.resources.displayMetrics.density
        val bottomMarginPx = computeBottomMargin(rootContent, resolvedAnchor, density)

        val rootParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = bottomMarginPx
        }
        rootContent.addView(itemBinding.root, rootParams)
        activeContainerRef = WeakReference(itemBinding.root)

        itemBinding.snackbarCard.setOnClickListener {
            dismissAnimated(rootContent, itemBinding, slideUpOnExit)
        }

        if (view.context.areAnimationsEnabled()) {
            itemBinding.snackbarCard.alpha = 0f
            itemBinding.snackbarCard.translationY = 28f * density
            itemBinding.snackbarCard.scaleX = 0.92f
            itemBinding.snackbarCard.scaleY = 0.92f
            itemBinding.snackbarCard.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        val timeoutMs = when (duration) {
            Snackbar.LENGTH_LONG -> 3400L
            Snackbar.LENGTH_INDEFINITE -> 4500L
            else -> 2100L
        }
        val dismissRunnable = Runnable {
            dismissAnimated(rootContent, itemBinding, slideUpOnExit)
        }
        activeDismissRunnable = dismissRunnable
        itemBinding.root.postDelayed(dismissRunnable, timeoutMs)
    }

    private fun computeBottomMargin(rootContent: ViewGroup, anchor: View?, density: Float): Int {
        if (anchor != null) {
            val rootLoc = IntArray(2)
            val anchorLoc = IntArray(2)
            rootContent.getLocationOnScreen(rootLoc)
            anchor.getLocationOnScreen(anchorLoc)
            val rootBottom = rootLoc[1] + rootContent.height
            val distanceAboveAnchor = (rootBottom - anchorLoc[1]).coerceAtLeast(0)
            return distanceAboveAnchor + (4 * density).toInt()
        }
        val insets = ViewCompat.getRootWindowInsets(rootContent)
        val systemBottom = insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
        val imeBottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        return maxOf(systemBottom, imeBottom) + (8 * density).toInt()
    }

    private fun dismissAnimated(
        rootContent: ViewGroup,
        itemBinding: ViewCustomSnackbarBinding,
        slideUpOnExit: Boolean
    ) {
        if (activeContainerRef?.get() !== itemBinding.root) return
        activeDismissRunnable?.let { itemBinding.root.removeCallbacks(it) }
        activeDismissRunnable = null

        val context = itemBinding.root.context
        if (!context.areAnimationsEnabled() || itemBinding.snackbarCard.width <= 0) {
            removeActiveImmediately(rootContent)
            return
        }

        val card = itemBinding.snackbarCard
        val contentRow = itemBinding.llSnackbarContent
        val startWidth = card.width
        val startHeight = card.height.coerceAtLeast(1)
        val targetCircleSize = minOf(startWidth, startHeight)
        val density = context.resources.displayMetrics.density

        // Lock content width so text doesn't reflow while pill shrinks around center
        contentRow.layoutParams = contentRow.layoutParams.apply { width = startWidth }
        card.radius = startHeight / 2f

        val fadeOutContent = ObjectAnimator.ofFloat(contentRow, View.ALPHA, 1f, 0f).apply {
            duration = 120L
        }

        val collapseToCircle = ValueAnimator.ofInt(startWidth, targetCircleSize).apply {
            duration = 230L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                val currentWidth = anim.animatedValue as Int
                card.layoutParams = card.layoutParams.apply { width = currentWidth }
                card.requestLayout()
            }
        }

        val targetOffset = if (slideUpOnExit) -36f * density else 44f * density
        val glideOut = AnimatorSet().apply {
            startDelay = 150L
            duration = 180L
            interpolator = AccelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, 0f, targetOffset),
                ObjectAnimator.ofFloat(card, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(card, View.SCALE_X, 1f, 0.72f),
                ObjectAnimator.ofFloat(card, View.SCALE_Y, 1f, 0.72f)
            )
        }

        activeExitAnimator?.cancel()
        activeExitAnimator = AnimatorSet().apply {
            playTogether(fadeOutContent, collapseToCircle, glideOut)
            doOnEnd {
                activeExitAnimator = null
                if (activeContainerRef?.get() === itemBinding.root) {
                    rootContent.removeView(itemBinding.root)
                    activeContainerRef = null
                }
            }
            start()
        }
    }

    private fun removeActiveImmediately(rootContent: ViewGroup) {
        activeExitAnimator?.cancel()
        activeExitAnimator = null
        val current = activeContainerRef?.get()
        activeContainerRef = null
        if (current != null) {
            activeDismissRunnable?.let { current.removeCallbacks(it) }
            (current.parent as? ViewGroup)?.removeView(current) ?: rootContent.removeView(current)
        }
        activeDismissRunnable = null
    }
}
