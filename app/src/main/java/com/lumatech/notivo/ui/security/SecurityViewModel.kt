package com.lumatech.notivo.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumatech.notivo.data.security.PinManager
import com.lumatech.notivo.data.security.PinVerifyResult
import com.lumatech.notivo.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PinSetupStep {
    ENTER_NEW_PIN,
    CONFIRM_PIN
}

data class PinUiState(
    val enteredDigitsCount: Int = 0,
    val setupStep: PinSetupStep = PinSetupStep.ENTER_NEW_PIN,
    val isError: Boolean = false,
    val remainingAttempts: Int? = null,
    val lockoutSecondsRemaining: Long = 0L,
    val errorKind: PinErrorKind? = null
)

enum class PinErrorKind {
    MISMATCH,
    INVALID_PIN,
    LOCKED_OUT
}

sealed interface SecurityEvent {
    data object Verified : SecurityEvent
    data object SetupCompleted : SecurityEvent
    data object TriggerErrorFeedback : SecurityEvent
}

class SecurityViewModel(
    private val pinManager: PinManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinUiState())
    val uiState: StateFlow<PinUiState> = _uiState.asStateFlow()

    private val _events = Channel<SecurityEvent>(Channel.BUFFERED)
    val events: Flow<SecurityEvent> = _events.receiveAsFlow()

    private var currentInput = StringBuilder()
    private var firstSetupPin: String? = null
    private var lockoutJob: Job? = null

    fun refreshLockoutState() {
        val remaining = pinManager.getRemainingLockoutSeconds()
        if (remaining > 0L) {
            startLockoutCountdown(remaining)
        }
    }

    fun onDigitPressed(digit: Int, isSetupMode: Boolean) {
        if (_uiState.value.lockoutSecondsRemaining > 0L) return
        if (currentInput.length >= Constants.PIN_LENGTH) return

        currentInput.append(digit)
        _uiState.update {
            it.copy(
                enteredDigitsCount = currentInput.length,
                isError = false,
                errorKind = null
            )
        }

        if (currentInput.length == Constants.PIN_LENGTH) {
            val pin = currentInput.toString()
            if (isSetupMode) {
                handleSetupInput(pin)
            } else {
                handleVerifyInput(pin)
            }
        }
    }

    fun onBackspacePressed() {
        if (currentInput.isEmpty() || _uiState.value.lockoutSecondsRemaining > 0L) return
        currentInput.deleteCharAt(currentInput.length - 1)
        _uiState.update {
            it.copy(
                enteredDigitsCount = currentInput.length,
                isError = false,
                errorKind = null
            )
        }
    }

    private fun handleSetupInput(pin: String) {
        if (_uiState.value.setupStep == PinSetupStep.ENTER_NEW_PIN) {
            firstSetupPin = pin
            currentInput.clear()
            _uiState.update {
                it.copy(
                    enteredDigitsCount = 0,
                    setupStep = PinSetupStep.CONFIRM_PIN,
                    isError = false,
                    errorKind = null
                )
            }
            return
        }

        if (pin == firstSetupPin) {
            pinManager.setPin(pin)
            currentInput.clear()
            viewModelScope.launch {
                _events.send(SecurityEvent.SetupCompleted)
            }
        } else {
            firstSetupPin = null
            currentInput.clear()
            _uiState.update {
                it.copy(
                    enteredDigitsCount = 0,
                    setupStep = PinSetupStep.ENTER_NEW_PIN,
                    isError = true,
                    errorKind = PinErrorKind.MISMATCH
                )
            }
            viewModelScope.launch {
                _events.send(SecurityEvent.TriggerErrorFeedback)
            }
        }
    }

    private fun handleVerifyInput(pin: String) {
        when (val result = pinManager.verifyPin(pin)) {
            PinVerifyResult.Success -> {
                currentInput.clear()
                viewModelScope.launch {
                    _events.send(SecurityEvent.Verified)
                }
            }
            is PinVerifyResult.InvalidPin -> {
                currentInput.clear()
                _uiState.update {
                    it.copy(
                        enteredDigitsCount = 0,
                        isError = true,
                        remainingAttempts = result.remainingAttempts,
                        errorKind = PinErrorKind.INVALID_PIN
                    )
                }
                viewModelScope.launch {
                    _events.send(SecurityEvent.TriggerErrorFeedback)
                }
            }
            is PinVerifyResult.LockedOut -> {
                currentInput.clear()
                startLockoutCountdown(result.remainingSeconds)
                viewModelScope.launch {
                    _events.send(SecurityEvent.TriggerErrorFeedback)
                }
            }
            PinVerifyResult.NotConfigured -> {
                currentInput.clear()
                viewModelScope.launch {
                    _events.send(SecurityEvent.Verified)
                }
            }
        }
    }

    private fun startLockoutCountdown(initialSeconds: Long) {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            var remaining = initialSeconds
            while (remaining > 0L) {
                _uiState.update {
                    it.copy(
                        enteredDigitsCount = 0,
                        isError = true,
                        lockoutSecondsRemaining = remaining,
                        errorKind = PinErrorKind.LOCKED_OUT
                    )
                }
                delay(1_000L)
                remaining = pinManager.getRemainingLockoutSeconds()
            }
            _uiState.update {
                it.copy(
                    isError = false,
                    lockoutSecondsRemaining = 0L,
                    errorKind = null,
                    remainingAttempts = null
                )
            }
        }
    }
}
