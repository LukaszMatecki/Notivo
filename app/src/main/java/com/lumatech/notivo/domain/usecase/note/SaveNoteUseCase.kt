package com.lumatech.notivo.domain.usecase.note

import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.model.NotivoError
import com.lumatech.notivo.domain.repository.NoteRepository

/**
 * Validates and persists a note. Locked notes are transparently encrypted by the repository.
 */
class SaveNoteUseCase(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(note: Note): Result<Long> {
        if (note.title.isBlank() && note.content.isBlank() && note.imagePaths.isEmpty()) {
            return Result.failure(NotivoError.ValidationError("Note cannot be completely empty."))
        }
        val normalizedNote = note.copy(
            title = note.title.trim(),
            updatedAt = System.currentTimeMillis()
        )
        return noteRepository.saveNote(normalizedNote)
    }
}
