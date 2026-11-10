package com.lumatech.notivo.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd

/**
 * TextView supporting a smooth animated left-to-right strikethrough across all rendered lines.
 */
class AnimatedStrikeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var strikeProgress: Float = 0f
    private var animator: ValueAnimator? = null

    private val strikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun setStruck(struck: Boolean) {
        animator?.cancel()
        animator = null
        strikeProgress = if (struck) 1f else 0f
        invalidate()
    }

    fun animateStrike(targetStruck: Boolean, onEnd: () -> Unit = {}) {
        animator?.cancel()
        val endValue = if (targetStruck) 1f else 0f
        if (!context.areAnimationsEnabled() || strikeProgress == endValue) {
            strikeProgress = endValue
            invalidate()
            onEnd()
            return
        }

        var cancelled = false
        animator = ValueAnimator.ofFloat(strikeProgress, endValue).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                strikeProgress = anim.animatedValue as Float
                invalidate()
            }
            doOnCancel {
                cancelled = true
            }
            doOnEnd {
                animator = null
                if (!cancelled) {
                    onEnd()
                }
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val textLayout = layout ?: return
        if (strikeProgress <= 0.001f) return

        val density = resources.displayMetrics.density
        strikePaint.color = currentTextColor
        strikePaint.strokeWidth = maxOf(1.6f * density, paint.textSize * 0.085f)

        val lineCount = textLayout.lineCount
        if (lineCount == 0) return

        var totalWidth = 0f
        for (i in 0 until lineCount) {
            totalWidth += textLayout.getLineWidth(i)
        }
        if (totalWidth <= 0f) return

        var remaining = totalWidth * strikeProgress
        canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
        val offsetFromBaseline = paint.textSize * 0.30f

        for (i in 0 until lineCount) {
            if (remaining <= 0f) break
            val lineWidth = textLayout.getLineWidth(i)
            if (lineWidth <= 0f) continue

            val drawn = minOf(lineWidth, remaining)
            val startX = textLayout.getLineLeft(i)
            val y = textLayout.getLineBaseline(i) - offsetFromBaseline
            canvas.drawLine(startX, y, startX + drawn, y, strikePaint)
            remaining -= drawn
        }
        canvas.restore()
    }
}
