package com.lumatech.notivo.domain.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks temporary in-memory unlock state for private biometric/PIN folders.
 * Cleared automatically when switching back to public filters, switching tabs, or backgrounding.
 */
class PrivateFolderSession {
    private val _unlockedFolderIds = MutableStateFlow<Set<Long>>(emptySet())
    val unlockedFolderIds: StateFlow<Set<Long>> = _unlockedFolderIds.asStateFlow()

    fun unlock(folderId: Long) {
        if (folderId > 0L) {
            _unlockedFolderIds.value = setOf(folderId)
        }
    }

    fun lockAll() {
        if (_unlockedFolderIds.value.isNotEmpty()) {
            _unlockedFolderIds.value = emptySet()
        }
    }

    fun isUnlocked(folderId: Long?): Boolean {
        return folderId != null && folderId in _unlockedFolderIds.value
    }
}
