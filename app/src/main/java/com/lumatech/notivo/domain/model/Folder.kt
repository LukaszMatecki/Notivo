package com.lumatech.notivo.domain.model

/**
 * Pure domain model representing a note folder/category with optional privacy protection.
 */
data class Folder(
    val id: Long = 0L,
    val name: String,
    val colorHex: String,
    val isPrivate: Boolean = false,
    val noteCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
