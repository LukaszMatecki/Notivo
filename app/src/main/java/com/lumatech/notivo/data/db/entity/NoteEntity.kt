package com.lumatech.notivo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity for notes, supporting both plaintext and AES-GCM encrypted blobs.
 */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["folder_id"]),
        Index(value = ["is_deleted"])
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val content: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String? = null,
    @ColumnInfo(name = "is_locked")
    val isLocked: Boolean = false,
    @ColumnInfo(name = "encrypted_content", typeAffinity = ColumnInfo.BLOB)
    val encryptedContent: ByteArray? = null,
    @ColumnInfo(name = "iv", typeAffinity = ColumnInfo.BLOB)
    val iv: ByteArray? = null,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "folder_id")
    val folderId: Long? = null,
    @ColumnInfo(name = "image_paths")
    val imagePaths: String = "",
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NoteEntity
        if (id != other.id) return false
        if (title != other.title) return false
        if (content != other.content) return false
        if (colorHex != other.colorHex) return false
        if (isLocked != other.isLocked) return false
        if (!encryptedContent.contentEquals(other.encryptedContent)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (isPinned != other.isPinned) return false
        if (folderId != other.folderId) return false
        if (imagePaths != other.imagePaths) return false
        if (isDeleted != other.isDeleted) return false
        if (deletedAt != other.deletedAt) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + (colorHex?.hashCode() ?: 0)
        result = 31 * result + isLocked.hashCode()
        result = 31 * result + (encryptedContent?.contentHashCode() ?: 0)
        result = 31 * result + (iv?.contentHashCode() ?: 0)
        result = 31 * result + isPinned.hashCode()
        result = 31 * result + (folderId?.hashCode() ?: 0)
        result = 31 * result + imagePaths.hashCode()
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
