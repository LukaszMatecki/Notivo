package com.lumatech.notivo.ui.security

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.FragmentPinInputBinding
import com.lumatech.notivo.ui.MainActivity
import com.lumatech.notivo.ui.common.BindingFragment
import com.lumatech.notivo.ui.common.NotivoSnackbarHelper
import com.lumatech.notivo.ui.common.NotivoViewModelFactory
import com.lumatech.notivo.ui.common.collectWithLifecycle
import com.lumatech.notivo.ui.common.performConfirmHaptic
import com.lumatech.notivo.ui.common.serviceLocator

/**
 * Guides the user through setting and confirming a 4-digit security PIN.
 */
class PinSetupFragment : BindingFragment<FragmentPinInputBinding>(
    FragmentPinInputBinding::inflate
) {
    private val viewModel: SecurityViewModel by viewModels {
        NotivoViewModelFactory(serviceLocator)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnPinBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnUseBiometric.visibility = View.GONE

        PinUiBinder.bindKeypad(
            binding = binding,
            onDigit = { digit -> viewModel.onDigitPressed(digit, isSetupMode = true) },
            onBackspace = { viewModel.onBackspacePressed() }
        )

        collectWithLifecycle(viewModel.uiState) { state ->
            when (state.setupStep) {
                PinSetupStep.ENTER_NEW_PIN -> {
                    binding.tvPinTitle.setText(R.string.pin_setup_title)
                    binding.tvPinSubtitle.setText(R.string.pin_setup_subtitle)
                }
                PinSetupStep.CONFIRM_PIN -> {
                    binding.tvPinTitle.setText(R.string.pin_confirm_title)
                    binding.tvPinSubtitle.setText(R.string.pin_confirm_subtitle)
                }
            }

            binding.tvPinStatus.text = if (state.errorKind == PinErrorKind.MISMATCH) {
                getString(R.string.pin_mismatch_error)
            } else {
                ""
            }
            PinUiBinder.renderDots(binding, state.enteredDigitsCount, state.isError)
        }

        collectWithLifecycle(viewModel.events) { event ->
            when (event) {
                SecurityEvent.SetupCompleted -> {
                    binding.root.performConfirmHaptic()
                    NotivoSnackbarHelper.show(
                        view = requireView(),
                        messageRes = R.string.pin_setup_success,
                        iconRes = R.drawable.ic_lock
                    )
                    deliverSuccessAndClose()
                }
                SecurityEvent.TriggerErrorFeedback -> PinUiBinder.playErrorShake(binding)
                SecurityEvent.Verified -> Unit
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setScreenSecurity(true)
    }

    private fun deliverSuccessAndClose() {
        val requestKey = arguments?.getString("requestKey").orEmpty()
        val payload = arguments?.getBundle(AuthGateHelper.EXTRA_PAYLOAD) ?: Bundle.EMPTY
        if (requestKey.isNotEmpty()) {
            setFragmentResult(
                requestKey,
                Bundle().apply {
                    putBoolean(AuthGateHelper.EXTRA_AUTHORIZED, true)
                    putBundle(AuthGateHelper.EXTRA_PAYLOAD, payload)
                }
            )
        }
        findNavController().popBackStack()
    }
}
