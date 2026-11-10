package com.lumatech.notivo.util

/**
 * Application-wide constants for security, layout, and search behavior.
 */
object Constants {
    const val DATABASE_NAME = "notivo_database.db"
    const val PREFS_NAME = "notivo_preferences"
    const val ENCRYPTED_PREFS_NAME = "notivo_secure_prefs"

    const val PIN_LENGTH = 4
    const val MAX_PIN_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 30_000L
    const val SEARCH_DEBOUNCE_MS = 300L

    const val KEYSTORE_ALIAS = "notivo_master_aes_key"
    const val BIOMETRIC_KEYSTORE_ALIAS = "notivo_biometric_aes_key"

    val FOLDER_COLORS = listOf(
        FolderColorOption("Niebieski", "#4285F4"),
        FolderColorOption("Zielony", "#34A853"),
        FolderColorOption("Czerwony", "#EA4335"),
        FolderColorOption("Pomarańczowy", "#F5A623"),
        FolderColorOption("Fioletowy", "#9C27B0"),
        FolderColorOption("Szary", "#9E9E9E")
    )
}

data class FolderColorOption(
    val name: String,
    val hex: String
)
