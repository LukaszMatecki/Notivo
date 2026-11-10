package com.lumatech.notivo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumatech.notivo.data.security.CryptoEngine
import com.lumatech.notivo.data.security.KeySecurityLevel
import com.lumatech.notivo.data.security.PinManager
import com.lumatech.notivo.domain.model.AppFontFamily
import com.lumatech.notivo.domain.model.AppPreferences
import com.lumatech.notivo.domain.model.FontSizeOption
import com.lumatech.notivo.domain.model.NoteLayoutMode
import com.lumatech.notivo.domain.model.SortOrder
import com.lumatech.notivo.domain.model.ThemeMode
import com.lumatech.notivo.domain.repository.PreferencesRepository
import com.lumatech.notivo.domain.usecase.note.ExportNotesUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val isPinConfigured: Boolean = false,
    val securityLevel: KeySecurityLevel = KeySecurityLevel.TEE
)

sealed interface SettingsEvent {
    data class ExportJsonReady(val jsonContent: String) : SettingsEvent
    data class ThemeChanged(val mode: ThemeMode) : SettingsEvent
    data class ShowMessage(val message: String) : SettingsEvent
}

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val pinManager: PinManager,
    private val cryptoEngine: CryptoEngine,
    private val exportNotesUseCase: ExportNotesUseCase
) : ViewModel() {

    private val pinRefreshTrigger = MutableStateFlow(0)
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.observePreferences(),
        pinRefreshTrigger
    ) { prefs, _ ->
        SettingsUiState(
            preferences = prefs,
            isPinConfigured = pinManager.isPinSet(),
            securityLevel = cryptoEngine.getSecurityLevel()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            preferences = preferencesRepository.getCurrentPreferences(),
            isPinConfigured = pinManager.isPinSet()
        )
    )

    fun refreshPinStatus() {
        pinRefreshTrigger.value += 1
    }

    fun updateFontFamily(family: AppFontFamily) {
        preferencesRepository.updatePreferences { it.copy(fontFamily = family) }
    }

    fun updateFontSize(option: FontSizeOption) {
        preferencesRepository.updatePreferences { it.copy(fontSizeOption = option) }
    }

    fun updateSortOrder(order: SortOrder) {
        preferencesRepository.updatePreferences { it.copy(sortOrder = order) }
    }

    fun updateLayoutMode(mode: NoteLayoutMode) {
        preferencesRepository.updatePreferences { it.copy(noteLayoutMode = mode) }
    }

    fun updateThemeMode(mode: ThemeMode) {
        preferencesRepository.updatePreferences { it.copy(themeMode = mode) }
        viewModelScope.launch {
            _events.send(SettingsEvent.ThemeChanged(mode))
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        preferencesRepository.updatePreferences { it.copy(biometricEnabled = enabled) }
    }

    fun setHighPriorityNotifications(enabled: Boolean) {
        preferencesRepository.updatePreferences { it.copy(highPriorityNotifications = enabled) }
    }

    fun removePin() {
        pinManager.clearPin()
        refreshPinStatus()
    }

    fun prepareNotesExport() {
        viewModelScope.launch {
            exportNotesUseCase()
                .onSuccess { json -> _events.send(SettingsEvent.ExportJsonReady(json)) }
                .onFailure { _events.send(SettingsEvent.ShowMessage("Błąd podczas eksportu.")) }
        }
    }
}
