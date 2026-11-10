package com.lumatech.notivo.data.repository

import com.lumatech.notivo.data.db.dao.NoteDao
import com.lumatech.notivo.data.db.entity.NoteEntity
import com.lumatech.notivo.data.mapper.DataMappers
import com.lumatech.notivo.data.security.CryptoEngine
import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.model.NotivoError
import com.lumatech.notivo.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room + hardware Keystore implementation of [NoteRepository].
 */
class NoteRepositoryImpl(
    private val noteDao: NoteDao,
    private val cryptoEngine: CryptoEngine,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : NoteRepository {

    override fun observeActiveNotes(): Flow<List<Note>> =
        noteDao.observeActiveNotes().map { entities ->
            entities.map { DataMappers.noteEntityToDomain(it) }
        }

    override fun observeDeletedNotes(): Flow<List<Note>> =
        noteDao.observeDeletedNotes().map { entities ->
            entities.map { DataMappers.noteEntityToDomain(it) }
        }

    override fun searchActiveNotes(query: String): Flow<List<Note>> =
        noteDao.searchActiveNotes(query).map { entities ->
            entities.map { DataMappers.noteEntityToDomain(it) }
        }

    override fun observeActiveNoteCount(): Flow<Int> = noteDao.observeActiveNoteCount()

    override fun observeLockedNoteCount(): Flow<Int> = noteDao.observeLockedNoteCount()

    override suspend fun getNoteById(id: Long, decrypt: Boolean): Result<Note?> = runCatching {
        val entity = withContext(ioDispatcher) { noteDao.getById(id) } ?: return@runCatching null
        if (!entity.isLocked || !decrypt) {
            return@runCatching DataMappers.noteEntityToDomain(entity)
        }
        val encryptedBytes = entity.encryptedContent
        val iv = entity.iv
        if (encryptedBytes == null || iv == null) {
            return@runCatching DataMappers.noteEntityToDomain(entity, decryptedContent = entity.content)
        }
        val plainContent = try {
            withContext(cpuDispatcher) { cryptoEngine.decrypt(encryptedBytes, iv) }
        } catch (error: Exception) {
            throw NotivoError.DecryptionFailed(error)
        }
        DataMappers.noteEntityToDomain(entity, decryptedContent = plainContent)
    }

    override suspend fun saveNote(note: Note): Result<Long> = runCatching {
        val entity = if (note.isLocked) {
            val payload = try {
                withContext(cpuDispatcher) { cryptoEngine.encrypt(note.content) }
            } catch (error: Exception) {
                throw NotivoError.EncryptionFailed(error)
            }
            NoteEntity(
                id = note.id,
                title = note.title,
                content = "",
                colorHex = note.colorHex,
                isLocked = true,
                encryptedContent = payload.cipherText,
                iv = payload.iv,
                isPinned = note.isPinned,
                folderId = note.folderId,
                imagePaths = DataMappers.serializeImagePaths(note.imagePaths),
                isDeleted = note.isDeleted,
                deletedAt = note.deletedAt,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt
            )
        } else {
            DataMappers.noteDomainToPlainEntity(note)
        }
        withContext(ioDispatcher) { noteDao.insertOrUpdate(entity) }
    }

    override suspend fun lockNote(noteId: Long): Result<Unit> = runCatching {
        val existing = withContext(ioDispatcher) { noteDao.getById(noteId) }
            ?: throw NotivoError.NotFoundError("Note $noteId not found.")
        if (existing.isLocked) return@runCatching

        val payload = try {
            withContext(cpuDispatcher) { cryptoEngine.encrypt(existing.content) }
        } catch (error: Exception) {
            throw NotivoError.EncryptionFailed(error)
        }

        val updated = existing.copy(
            content = "",
            isLocked = true,
            encryptedContent = payload.cipherText,
            iv = payload.iv,
            updatedAt = System.currentTimeMillis()
        )
        withContext(ioDispatcher) { noteDao.insertOrUpdate(updated) }
    }

    override suspend fun unlockNotePermanently(noteId: Long): Result<Note> = runCatching {
        val existing = withContext(ioDispatcher) { noteDao.getById(noteId) }
            ?: throw NotivoError.NotFoundError("Note $noteId not found.")
        if (!existing.isLocked) {
            return@runCatching DataMappers.noteEntityToDomain(existing)
        }

        val plainContent = if (existing.encryptedContent != null && existing.iv != null) {
            try {
                withContext(cpuDispatcher) {
                    cryptoEngine.decrypt(existing.encryptedContent, existing.iv)
                }
            } catch (error: Exception) {
                throw NotivoError.DecryptionFailed(error)
            }
        } else {
            existing.content
        }

        val updated = existing.copy(
            content = plainContent,
            isLocked = false,
            encryptedContent = null,
            iv = null,
            updatedAt = System.currentTimeMillis()
        )
        withContext(ioDispatcher) { noteDao.insertOrUpdate(updated) }
        DataMappers.noteEntityToDomain(updated, decryptedContent = plainContent)
    }

    override suspend fun togglePin(noteId: Long, isPinned: Boolean): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            noteDao.updatePin(noteId, isPinned, System.currentTimeMillis())
        }
    }

    override suspend fun softDeleteNote(noteId: Long): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            noteDao.softDelete(noteId, System.currentTimeMillis())
        }
    }

    override suspend fun restoreNote(noteId: Long): Result<Unit> = runCatching {
        withContext(ioDispatcher) { noteDao.restore(noteId) }
    }

    override suspend fun deleteNotePermanently(noteId: Long): Result<Unit> = runCatching {
        withContext(ioDispatcher) { noteDao.deletePermanently(noteId) }
    }

    override suspend fun emptyTrash(): Result<Unit> = runCatching {
        withContext(ioDispatcher) { noteDao.emptyTrash() }
    }

    override suspend fun getAllActiveNotesSnapshot(): Result<List<Note>> = runCatching {
        withContext(ioDispatcher) {
            noteDao.getAllActiveNotesOnce().map { DataMappers.noteEntityToDomain(it) }
        }
    }
}
