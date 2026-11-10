package com.lumatech.notivo.domain.model

/**
 * Available sorting strategies for notes list.
 */
enum class SortOrder {
    UPDATED_DESC,
    CREATED_DESC,
    TITLE_ASC,
    COLOR
}

/**
 * Layout modes for displaying notes.
 */
enum class NoteLayoutMode {
    GRID,
    LIST
}

/**
 * Supported application theme modes.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Font scale options for note cards and editor.
 */
enum class FontSizeOption(val scaleFactor: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.25f)
}

/**
 * Bundled typography families licensed under SIL Open Font License 1.1 or system default.
 */
enum class AppFontFamily {
    OUTFIT,
    PLUS_JAKARTA_SANS,
    POPPINS,
    LATO,
    SYSTEM
}

/**
 * Immutable snapshot of user personalization and security preferences.
 */
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val noteLayoutMode: NoteLayoutMode = NoteLayoutMode.GRID,
    val sortOrder: SortOrder = SortOrder.UPDATED_DESC,
    val fontSizeOption: FontSizeOption = FontSizeOption.MEDIUM,
    val fontFamily: AppFontFamily = AppFontFamily.OUTFIT,
    val defaultNoteColorHex: String? = null,
    val biometricEnabled: Boolean = true,
    val highPriorityNotifications: Boolean = false
)
