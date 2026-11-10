package com.lumatech.notivo.domain.usecase.note

import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.repository.NoteRepository

/**
 * Loads a single note, optionally decrypting its content when access has been authorized.
 */
class GetNoteByIdUseCase(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(noteId: Long, decrypt: Boolean = false): Result<Note?> {
        return noteRepository.getNoteById(noteId, decrypt = decrypt)
    }
}
