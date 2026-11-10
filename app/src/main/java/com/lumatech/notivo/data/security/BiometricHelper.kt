package com.lumatech.notivo.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * Helper encapsulating AndroidX BiometricPrompt and hardware availability checks.
 */
class BiometricHelper(
    private val cryptoManager: CryptoManager
) {
    fun isBiometricAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val strongResult = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (strongResult == BiometricManager.BIOMETRIC_SUCCESS) return true

        val weakResult = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        return weakResult == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        fragment: Fragment,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onFallbackToPin: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = fragment.activity ?: return
        authenticateInternal(
            activity = activity,
            promptFactory = { executor, callback -> BiometricPrompt(fragment, executor, callback) },
            title = title,
            subtitle = subtitle,
            negativeButtonText = negativeButtonText,
            onSuccess = onSuccess,
            onFallbackToPin = onFallbackToPin,
            onError = onError
        )
    }

    private fun authenticateInternal(
        activity: FragmentActivity,
        promptFactory: (java.util.concurrent.Executor, BiometricPrompt.AuthenticationCallback) -> BiometricPrompt,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onFallbackToPin: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT -> onFallbackToPin()
                    else -> onError(errString.toString())
                }
            }
        }

        val manager = BiometricManager.from(activity)
        val strongAvailable =
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        val weakAvailable =
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS

        if (!strongAvailable && !weakAvailable) {
            onFallbackToPin()
            return
        }

        val prompt = promptFactory(executor, callback)
        try {
            if (strongAvailable) {
                // CryptoObject requires BIOMETRIC_STRONG exclusively (cannot be combined with BIOMETRIC_WEAK)
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText(negativeButtonText)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build()

                val cipher = cryptoManager.createBiometricBoundCipher()
                if (cipher != null) {
                    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
                } else {
                    prompt.authenticate(promptInfo)
                }
            } else {
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText(negativeButtonText)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build()
                prompt.authenticate(promptInfo)
            }
        } catch (_: Exception) {
            onFallbackToPin()
        }
    }
}
