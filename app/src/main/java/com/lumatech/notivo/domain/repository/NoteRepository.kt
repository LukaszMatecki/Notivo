package com.lumatech.notivo.domain.repository

import com.lumatech.notivo.domain.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for note persistence, search, and encryption.
 */
interface NoteRepository {
    fun observeActiveNotes(): Flow<List<Note>>
    fun observeDeletedNotes(): Flow<List<Note>>
    fun searchActiveNotes(query: String): Flow<List<Note>>
    fun observeActiveNoteCount(): Flow<Int>
    fun observeLockedNoteCount(): Flow<Int>

    suspend fun getNoteById(id: Long, decrypt: Boolean = false): Result<Note?>
    suspend fun saveNote(note: Note): Result<Long>
    suspend fun lockNote(noteId: Long): Result<Unit>
    suspend fun unlockNotePermanently(noteId: Long): Result<Note>
    suspend fun togglePin(noteId: Long, isPinned: Boolean): Result<Unit>
    suspend fun softDeleteNote(noteId: Long): Result<Unit>
    suspend fun restoreNote(noteId: Long): Result<Unit>
    suspend fun deleteNotePermanently(noteId: Long): Result<Unit>
    suspend fun emptyTrash(): Result<Unit>
    suspend fun getAllActiveNotesSnapshot(): Result<List<Note>>
}
