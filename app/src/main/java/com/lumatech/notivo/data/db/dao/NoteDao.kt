package com.lumatech.notivo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lumatech.notivo.data.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for notes CRUD, search, trash, and lock state updates.
 */
@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE is_deleted = 0 ORDER BY is_pinned DESC, updated_at DESC")
    fun observeActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE is_deleted = 1 ORDER BY deleted_at DESC")
    fun observeDeletedNotes(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE is_deleted = 0
          AND (title LIKE '%' || :query || '%'
               OR (is_locked = 0 AND content LIKE '%' || :query || '%'))
        ORDER BY is_pinned DESC, updated_at DESC
        """
    )
    fun searchActiveNotes(query: String): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes WHERE is_deleted = 0")
    fun observeActiveNoteCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE is_deleted = 0 AND is_locked = 1")
    fun observeLockedNoteCount(): Flow<Int>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getById(noteId: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE is_deleted = 0 ORDER BY updated_at DESC")
    suspend fun getAllActiveNotesOnce(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(note: NoteEntity): Long

    @Query("UPDATE notes SET is_pinned = :isPinned, updated_at = :updatedAt WHERE id = :noteId")
    suspend fun updatePin(noteId: Long, isPinned: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET is_deleted = 1, deleted_at = :deletedAt WHERE id = :noteId")
    suspend fun softDelete(noteId: Long, deletedAt: Long)

    @Query("UPDATE notes SET is_deleted = 0, deleted_at = NULL WHERE id = :noteId")
    suspend fun restore(noteId: Long)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deletePermanently(noteId: Long)

    @Query("DELETE FROM notes WHERE is_deleted = 1")
    suspend fun emptyTrash()
}
