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
import com.lumatech.notivo.ui.common.NotivoViewModelFactory
import com.lumatech.notivo.ui.common.collectWithLifecycle
import com.lumatech.notivo.ui.common.performConfirmHaptic
import com.lumatech.notivo.ui.common.serviceLocator

/**
 * Verifies the user's 4-digit PIN with attempt limits and optional biometric shortcut.
 */
class PinVerifyFragment : BindingFragment<FragmentPinInputBinding>(
    FragmentPinInputBinding::inflate
) {
    private val viewModel: SecurityViewModel by viewModels {
        NotivoViewModelFactory(serviceLocator)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvPinTitle.setText(R.string.pin_verify_title)
        binding.tvPinSubtitle.setText(R.string.pin_verify_subtitle)
        binding.btnPinBack.setOnClickListener { findNavController().popBackStack() }

        val prefs = serviceLocator.preferencesRepository.getCurrentPreferences()
        val canUseBio = prefs.biometricEnabled &&
            serviceLocator.biometricHelper.isBiometricAvailable(requireContext())
        binding.btnUseBiometric.visibility = if (canUseBio) View.VISIBLE else View.GONE
        binding.btnUseBiometric.setOnClickListener { launchBiometricPrompt() }

        PinUiBinder.bindKeypad(
            binding = binding,
            onDigit = { digit -> viewModel.onDigitPressed(digit, isSetupMode = false) },
            onBackspace = { viewModel.onBackspacePressed() }
        )

        viewModel.refreshLockoutState()

        collectWithLifecycle(viewModel.uiState) { state ->
            val isLockedOut = state.lockoutSecondsRemaining > 0L
            PinUiBinder.setKeypadEnabled(binding, !isLockedOut)
            PinUiBinder.renderDots(binding, state.enteredDigitsCount, state.isError)

            binding.tvPinStatus.text = when (state.errorKind) {
                PinErrorKind.INVALID_PIN -> getString(
                    R.string.pin_invalid_attempts,
                    state.remainingAttempts ?: 0
                )
                PinErrorKind.LOCKED_OUT -> getString(
                    R.string.pin_locked_out,
                    state.lockoutSecondsRemaining
                )
                PinErrorKind.MISMATCH, null -> ""
            }
        }

        collectWithLifecycle(viewModel.events) { event ->
            when (event) {
                SecurityEvent.Verified -> {
                    binding.root.performConfirmHaptic()
                    deliverSuccessAndClose()
                }
                SecurityEvent.TriggerErrorFeedback -> PinUiBinder.playErrorShake(binding)
                SecurityEvent.SetupCompleted -> Unit
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setScreenSecurity(true)
    }

    private fun launchBiometricPrompt() {
        serviceLocator.biometricHelper.authenticate(
            fragment = this,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.pin_verify_subtitle),
            negativeButtonText = getString(R.string.biometric_prompt_fallback),
            onSuccess = {
                if (isAdded) {
                    binding.root.performConfirmHaptic()
                    deliverSuccessAndClose()
                }
            },
            onFallbackToPin = { /* Stay on PIN screen */ },
            onError = { /* Stay on PIN screen */ }
        )
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
