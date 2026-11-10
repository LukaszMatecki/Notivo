package com.lumatech.notivo.ui.security

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.lumatech.notivo.R
import com.lumatech.notivo.ui.common.navigateSafely
import com.lumatech.notivo.ui.common.serviceLocator

/**
 * Coordinates biometric and PIN authentication flows across Fragments using Fragment Results.
 */
object AuthGateHelper {
    const val EXTRA_AUTHORIZED = "extra_authorized"
    const val EXTRA_PAYLOAD = "extra_payload"

    fun requestUnlock(
        fragment: Fragment,
        requestKey: String,
        subtitle: String,
        payload: Bundle = Bundle.EMPTY,
        onBiometricSuccess: () -> Unit
    ) {
        if (!fragment.isAdded) return
        val locator = fragment.serviceLocator
        val prefs = locator.preferencesRepository.getCurrentPreferences()
        val context = fragment.requireContext()

        if (prefs.biometricEnabled && locator.biometricHelper.isBiometricAvailable(context)) {
            locator.biometricHelper.authenticate(
                fragment = fragment,
                title = fragment.getString(R.string.biometric_prompt_title),
                subtitle = subtitle,
                negativeButtonText = fragment.getString(R.string.biometric_prompt_fallback),
                onSuccess = {
                    if (fragment.isAdded) {
                        onBiometricSuccess()
                    }
                },
                onFallbackToPin = {
                    if (fragment.isAdded) {
                        navigateToPinGate(fragment, requestKey, payload)
                    }
                },
                onError = {
                    if (fragment.isAdded) {
                        navigateToPinGate(fragment, requestKey, payload)
                    }
                }
            )
        } else {
            navigateToPinGate(fragment, requestKey, payload)
        }
    }

    fun ensurePinConfiguredThenProceed(
        fragment: Fragment,
        requestKey: String,
        payload: Bundle = Bundle.EMPTY,
        onAlreadyConfigured: () -> Unit
    ) {
        if (!fragment.isAdded) return
        val locator = fragment.serviceLocator
        if (locator.pinManager.isPinSet()) {
            onAlreadyConfigured()
        } else {
            navigateToPinSetup(fragment, requestKey, payload)
        }
    }

    fun navigateToPinSetup(
        fragment: Fragment,
        requestKey: String,
        payload: Bundle = Bundle.EMPTY
    ) {
        fragment.navigateSafely(
            R.id.pinSetupFragment,
            Bundle().apply {
                putString("requestKey", requestKey)
                putBundle(EXTRA_PAYLOAD, payload)
            }
        )
    }

    private fun navigateToPinGate(
        fragment: Fragment,
        requestKey: String,
        payload: Bundle
    ) {
        val locator = fragment.serviceLocator
        val destination = if (locator.pinManager.isPinSet()) {
            R.id.pinVerifyFragment
        } else {
            R.id.pinSetupFragment
        }
        fragment.navigateSafely(
            destination,
            Bundle().apply {
                putString("requestKey", requestKey)
                putBundle(EXTRA_PAYLOAD, payload)
            }
        )
    }
}
