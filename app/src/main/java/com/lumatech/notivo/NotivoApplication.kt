package com.lumatech.notivo

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.lumatech.notivo.di.ServiceLocator
import com.lumatech.notivo.domain.model.ThemeMode

/**
 * Application entry point holding the [ServiceLocator] and applying saved theme preferences.
 */
class NotivoApplication : Application() {

    lateinit var serviceLocator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        serviceLocator = ServiceLocator(this)
        val prefs = serviceLocator.preferencesRepository.getCurrentPreferences()
        applyThemeMode(prefs.themeMode)
    }

    companion object {
        fun applyThemeMode(mode: ThemeMode) {
            val delegateMode = when (mode) {
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
            AppCompatDelegate.setDefaultNightMode(delegateMode)
        }
    }
}
