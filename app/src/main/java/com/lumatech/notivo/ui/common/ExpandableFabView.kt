package com.lumatech.notivo.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lumatech.notivo.databinding.ItemFabActionBinding
import com.lumatech.notivo.databinding.ViewExpandableFabBinding

/**
 * Expandable floating action button supporting context-specific mini-actions (e.g., New Note / New Folder).
 */
class ExpandableFabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    data class FabAction(
        @param:StringRes val labelRes: Int,
        @param:DrawableRes val iconRes: Int,
        val onClick: () -> Unit
    )

    private val binding: ViewExpandableFabBinding =
        ViewExpandableFabBinding.inflate(LayoutInflater.from(context), this)

    private var actions: List<FabAction> = emptyList()
    private val actionRows = mutableListOf<View>()
    var isExpanded: Boolean = false
        private set

    init {
        binding.fabPrimary.outlineProvider = null
        binding.fabPrimary.elevation = 0f
        binding.fabPrimary.compatElevation = 0f
        binding.fabPrimary.stateListAnimator = null
        binding.fabPrimary.setOnClickListener {
            if (actions.size <= 1) {
                it.performHapticSafe(HapticFeedbackConstants.CONTEXT_CLICK)
                actions.firstOrNull()?.onClick?.invoke()
            } else {
                toggle()
            }
        }
        binding.fabScrim.setOnClickListener {
            collapse()
        }
    }

    fun setActions(newActions: List<FabAction>) {
        if (isExpanded) collapse(immediate = true)
        actions = newActions
        actionRows.clear()
        binding.fabActionsList.removeAllViews()

        val inflater = LayoutInflater.from(context)
        newActions.forEach { action ->
            val rowBinding = ItemFabActionBinding.inflate(inflater, binding.fabActionsList, false)
            rowBinding.miniFabLabel.setText(action.labelRes)
            rowBinding.miniFabButton.setImageResource(action.iconRes)
            rowBinding.miniFabButton.contentDescription = context.getString(action.labelRes)
            rowBinding.miniFabButton.outlineProvider = null
            rowBinding.miniFabButton.elevation = 0f
            rowBinding.miniFabButton.compatElevation = 0f
            rowBinding.miniFabButton.stateListAnimator = null

            val clickHandler = OnClickListener { v ->
                v.performHapticSafe(HapticFeedbackConstants.CONTEXT_CLICK)
                collapse()
                action.onClick()
            }
            rowBinding.miniFabButton.setOnClickListener(clickHandler)
            rowBinding.miniFabLabelCard.setOnClickListener(clickHandler)

            binding.fabActionsList.addView(rowBinding.root)
            actionRows.add(rowBinding.root)
        }
    }

    fun setBottomMargin(bottomPx: Int) {
        val params = binding.fabMenuContainer.layoutParams as? LayoutParams ?: return
        if (params.bottomMargin != bottomPx) {
            params.bottomMargin = bottomPx
            binding.fabMenuContainer.layoutParams = params
        }
    }

    fun toggle() {
        if (isExpanded) collapse() else expand()
    }

    fun expand() {
        if (isExpanded || actions.size <= 1) return
        isExpanded = true
        binding.fabPrimary.performHapticSafe(HapticFeedbackConstants.CONTEXT_CLICK)

        val animated = context.areAnimationsEnabled()
        binding.fabScrim.visibility = View.VISIBLE
        binding.fabActionsList.visibility = View.VISIBLE

        if (!animated) {
            binding.fabScrim.alpha = 1f
            binding.fabPrimary.rotation = 45f
            actionRows.forEach { row ->
                row.alpha = 1f
                row.translationY = 0f
                row.scaleX = 1f
                row.scaleY = 1f
            }
            return
        }

        binding.fabScrim.animate()
            .alpha(1f)
            .setDuration(200L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        binding.fabPrimary.animate()
            .rotation(45f)
            .setDuration(150L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        actionRows.reversed().forEachIndexed { index, row ->
            row.alpha = 0f
            row.translationY = 24f
            row.scaleX = 0.85f
            row.scaleY = 0.85f
            row.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 45L)
                .setDuration(220L)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }
    }

    fun collapse(immediate: Boolean = false) {
        if (!isExpanded) return
        isExpanded = false

        if (immediate || !context.areAnimationsEnabled()) {
            binding.fabScrim.alpha = 0f
            binding.fabScrim.visibility = View.GONE
            binding.fabActionsList.visibility = View.GONE
            binding.fabPrimary.rotation = 0f
            return
        }

        binding.fabPrimary.animate()
            .rotation(0f)
            .setDuration(150L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        binding.fabScrim.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                if (!isExpanded) {
                    binding.fabScrim.visibility = View.GONE
                }
            }
            .start()

        actionRows.forEach { row ->
            row.animate()
                .alpha(0f)
                .translationY(16f)
                .setDuration(140L)
                .withEndAction {
                    if (!isExpanded) {
                        binding.fabActionsList.visibility = View.GONE
                    }
                }
                .start()
        }
    }
}
