package com.lumatech.notivo.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lumatech.notivo.data.db.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room projection combining folder metadata with its active note count.
 */
data class FolderWithCountTuple(
    @Embedded val folder: FolderEntity,
    @ColumnInfo(name = "note_count") val noteCount: Int
)

/**
 * Data Access Object for folder management.
 */
@Dao
interface FolderDao {
    @Query(
        """
        SELECT f.*, COUNT(n.id) AS note_count
        FROM folders f
        LEFT JOIN notes n ON f.id = n.folder_id AND n.is_deleted = 0
        GROUP BY f.id
        ORDER BY f.is_private ASC, f.created_at ASC
        """
    )
    fun observeFoldersWithCount(): Flow<List<FolderWithCountTuple>>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getById(folderId: Long): FolderEntity?

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getFolderCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(folders: List<FolderEntity>)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteById(folderId: Long)
}
