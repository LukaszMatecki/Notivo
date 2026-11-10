package com.lumatech.notivo.ui.notes.editor

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.BottomSheetColorPickerBinding
import com.lumatech.notivo.ui.common.styleColorSwatch
import com.lumatech.notivo.util.NoteColorPalette

/**
 * Bottom sheet displaying expanded pastel note background themes with framed circular swatches.
 */
class ColorPickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetColorPickerBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetColorPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentHex = arguments?.getString(ARG_CURRENT_COLOR)
        val density = resources.displayMetrics.density
        val sizePx = (48 * density).toInt()
        val verticalMarginPx = (10 * density).toInt()
        val paddingPx = (12 * density).toInt()

        val iconContrastColor = MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorOnSurface
        )

        NoteColorPalette.options.forEachIndexed { index, option ->
            val resolvedColor = NoteColorPalette.resolveBackgroundColor(requireContext(), option.lightHex)
                ?: Color.WHITE
            val isSelected = (currentHex == null && index == 0) ||
                option.lightHex.equals(currentHex, ignoreCase = true)

            val cell = FrameLayout(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(0, verticalMarginPx, 0, verticalMarginPx)
                }
            }

            val swatch = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER)
                styleColorSwatch(resolvedColor)
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                contentDescription = option.name
                if (isSelected) {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(iconContrastColor)
                }
                setOnClickListener {
                    val chosenHex = if (index == 0) null else option.lightHex
                    setFragmentResult(
                        REQ_COLOR_PICKED,
                        Bundle().apply { putString(EXTRA_COLOR_HEX, chosenHex) }
                    )
                    dismiss()
                }
            }
            cell.addView(swatch)
            binding.colorSwatchGrid.addView(cell)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ColorPickerBottomSheet"
        const val REQ_COLOR_PICKED = "req_color_picked"
        const val EXTRA_COLOR_HEX = "extra_color_hex"
        private const val ARG_CURRENT_COLOR = "arg_current_color"

        fun newInstance(currentHex: String?): ColorPickerBottomSheet =
            ColorPickerBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_CURRENT_COLOR, currentHex) }
            }
    }
}
