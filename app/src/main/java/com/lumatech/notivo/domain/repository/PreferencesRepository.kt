package com.lumatech.notivo.domain.repository

import com.lumatech.notivo.domain.model.AppPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for user appearance, layout, and security preferences.
 */
interface PreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>
    fun getCurrentPreferences(): AppPreferences
    fun updatePreferences(transform: (AppPreferences) -> AppPreferences)
}
