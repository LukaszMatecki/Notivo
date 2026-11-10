package com.lumatech.notivo.domain.usecase.note

import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.repository.NoteRepository

/**
 * Encrypts a note using hardware-backed AES-256-GCM and marks it as locked.
 */
class LockNoteUseCase(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(noteId: Long): Result<Unit> = noteRepository.lockNote(noteId)
}

/**
 * Permanently removes the lock from a note and stores its decrypted content.
 */
class UnlockNoteUseCase(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(noteId: Long): Result<Note> =
        noteRepository.unlockNotePermanently(noteId)
}

/**
 * Toggles the pinned status of a note.
 */
class TogglePinNoteUseCase(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(noteId: Long, isPinned: Boolean): Result<Unit> =
        noteRepository.togglePin(noteId, isPinned)
}
