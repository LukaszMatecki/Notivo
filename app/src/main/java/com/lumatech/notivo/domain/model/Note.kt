package com.lumatech.notivo.domain.model

/**
 * Pure domain model representing a user note.
 *
 * When [isLocked] is true in listing mode, [content] is intentionally blank to avoid leaking
 * sensitive information before biometric or PIN verification.
 */
data class Note(
    val id: Long = 0L,
    val title: String,
    val content: String,
    val colorHex: String? = null,
    val isLocked: Boolean = false,
    val isPinned: Boolean = false,
    val folderId: Long? = null,
    val imagePaths: List<String> = emptyList(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
