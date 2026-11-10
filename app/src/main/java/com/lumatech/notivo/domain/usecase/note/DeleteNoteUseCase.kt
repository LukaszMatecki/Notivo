package com.lumatech.notivo.domain.usecase.note

import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow

/**
 * Handles soft-deleting notes to trash, restoring them, and permanently erasing them.
 */
class DeleteNoteUseCase(
    private val noteRepository: NoteRepository
) {
    suspend fun softDelete(noteId: Long): Result<Unit> = noteRepository.softDeleteNote(noteId)

    suspend fun restore(noteId: Long): Result<Unit> = noteRepository.restoreNote(noteId)

    suspend fun deletePermanently(noteId: Long): Result<Unit> =
        noteRepository.deleteNotePermanently(noteId)

    suspend fun emptyTrash(): Result<Unit> = noteRepository.emptyTrash()
}

/**
 * Observes notes currently residing in the trash.
 */
class GetDeletedNotesUseCase(
    private val noteRepository: NoteRepository
) {
    operator fun invoke(): Flow<List<Note>> = noteRepository.observeDeletedNotes()
}
