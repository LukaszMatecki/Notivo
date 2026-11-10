package com.lumatech.notivo.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.lumatech.notivo.domain.model.AppFontFamily
import com.lumatech.notivo.domain.model.AppPreferences
import com.lumatech.notivo.domain.model.FontSizeOption
import com.lumatech.notivo.domain.model.NoteLayoutMode
import com.lumatech.notivo.domain.model.SortOrder
import com.lumatech.notivo.domain.model.ThemeMode
import com.lumatech.notivo.domain.repository.PreferencesRepository
import com.lumatech.notivo.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences-backed repository exposing reactive user personalization preferences.
 */
class PreferencesManager(context: Context) : PreferencesRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(readFromPrefs())

    override fun observePreferences(): Flow<AppPreferences> = _preferences.asStateFlow()

    override fun getCurrentPreferences(): AppPreferences = _preferences.value

    override fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        val updated = transform(_preferences.value)
        writeToPrefs(updated)
        _preferences.value = updated
    }

    private fun readFromPrefs(): AppPreferences {
        return AppPreferences(
            themeMode = parseEnum(prefs.getString(KEY_THEME_MODE, null), ThemeMode.SYSTEM),
            noteLayoutMode = parseEnum(prefs.getString(KEY_LAYOUT_MODE, null), NoteLayoutMode.GRID),
            sortOrder = parseEnum(prefs.getString(KEY_SORT_ORDER, null), SortOrder.UPDATED_DESC),
            fontSizeOption = parseEnum(prefs.getString(KEY_FONT_SIZE, null), FontSizeOption.MEDIUM),
            fontFamily = parseEnum(prefs.getString(KEY_FONT_FAMILY, null), AppFontFamily.OUTFIT),
            defaultNoteColorHex = prefs.getString(KEY_DEFAULT_NOTE_COLOR, null),
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true),
            highPriorityNotifications = prefs.getBoolean(KEY_HIGH_PRIORITY_NOTIFS, false)
        )
    }

    private fun writeToPrefs(value: AppPreferences) {
        prefs.edit()
            .putString(KEY_THEME_MODE, value.themeMode.name)
            .putString(KEY_LAYOUT_MODE, value.noteLayoutMode.name)
            .putString(KEY_SORT_ORDER, value.sortOrder.name)
            .putString(KEY_FONT_SIZE, value.fontSizeOption.name)
            .putString(KEY_FONT_FAMILY, value.fontFamily.name)
            .putString(KEY_DEFAULT_NOTE_COLOR, value.defaultNoteColorHex)
            .putBoolean(KEY_BIOMETRIC_ENABLED, value.biometricEnabled)
            .putBoolean(KEY_HIGH_PRIORITY_NOTIFS, value.highPriorityNotifications)
            .apply()
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    private companion object {
        const val KEY_THEME_MODE = "pref_theme_mode"
        const val KEY_LAYOUT_MODE = "pref_layout_mode"
        const val KEY_SORT_ORDER = "pref_sort_order"
        const val KEY_FONT_SIZE = "pref_font_size"
        const val KEY_FONT_FAMILY = "pref_font_family"
        const val KEY_DEFAULT_NOTE_COLOR = "pref_default_note_color"
        const val KEY_BIOMETRIC_ENABLED = "pref_biometric_enabled"
        const val KEY_HIGH_PRIORITY_NOTIFS = "pref_high_priority_notifs"
    }
}
