package com.lumatech.notivo

import com.lumatech.notivo.data.db.entity.NoteEntity
import com.lumatech.notivo.data.mapper.DataMappers
import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.model.Task
import com.lumatech.notivo.domain.repository.FolderRepository
import com.lumatech.notivo.domain.repository.NoteRepository
import com.lumatech.notivo.domain.repository.TaskRepository
import com.lumatech.notivo.domain.security.PrivateFolderSession
import com.lumatech.notivo.domain.usecase.dashboard.GetDashboardSummaryUseCase
import com.lumatech.notivo.domain.usecase.note.ExportNotesUseCase
import com.lumatech.notivo.domain.usecase.note.SaveNoteUseCase
import com.lumatech.notivo.util.BulletListHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BulletListAndPrivacyTest {

    @Test
    fun `toggleOrIndentAtCursor cycles plain to bullet to indented bullet and back to plain`() {
        val step1 = BulletListHelper.toggleOrIndentAtCursor("Zakupy", 6)
        assertEquals("• Zakupy", step1.text)
        assertEquals(8, step1.cursorPosition)

        val step2 = BulletListHelper.toggleOrIndentAtCursor(step1.text, step1.cursorPosition)
        assertEquals("    • Zakupy", step2.text)
        assertEquals(12, step2.cursorPosition)

        val step3 = BulletListHelper.toggleOrIndentAtCursor(step2.text, step2.cursorPosition)
        assertEquals("Zakupy", step3.text)
        assertEquals(6, step3.cursorPosition)
    }

    @Test
    fun `toggleOrIndentAtCursor works cleanly on empty line`() {
        val step1 = BulletListHelper.toggleOrIndentAtCursor("", 0)
        assertEquals("• ", step1.text)
        assertEquals(2, step1.cursorPosition)

        val step2 = BulletListHelper.toggleOrIndentAtCursor(step1.text, step1.cursorPosition)
        assertEquals("    • ", step2.text)
        assertEquals(6, step2.cursorPosition)

        val step3 = BulletListHelper.toggleOrIndentAtCursor(step2.text, step2.cursorPosition)
        assertEquals("", step3.text)
        assertEquals(0, step3.cursorPosition)
    }

    @Test
    fun `handleNewlineAfterBullet continues matching indent or outdents on empty bullet`() {
        val cont1 = BulletListHelper.handleNewlineAfterBullet("• Mleko\n", 7)
        assertEquals("• Mleko\n• ", cont1?.text)
        assertEquals(10, cont1?.cursorPosition)

        val cont2 = BulletListHelper.handleNewlineAfterBullet("    • Serek\n", 11)
        assertEquals("    • Serek\n    • ", cont2?.text)
        assertEquals(18, cont2?.cursorPosition)

        val outdent = BulletListHelper.handleNewlineAfterBullet("    • \n", 6)
        assertEquals("• ", outdent?.text)
        assertEquals(2, outdent?.cursorPosition)

        val exitList = BulletListHelper.handleNewlineAfterBullet("• \n", 2)
        assertEquals("", exitList?.text)
        assertEquals(0, exitList?.cursorPosition)

        assertNull(BulletListHelper.handleNewlineAfterBullet("Zwykły tekst\n", 12))
    }

    @Test
    fun `PrivateFolderSession keeps single active folder and locks all on clear`() {
        val session = PrivateFolderSession()
        assertFalse(session.isUnlocked(3L))

        session.unlock(3L)
        assertTrue(session.isUnlocked(3L))

        session.unlock(5L)
        assertFalse(session.isUnlocked(3L))
        assertTrue(session.isUnlocked(5L))

        session.lockAll()
        assertFalse(session.isUnlocked(5L))
    }

    @Test
    fun `ExportNotesUseCase redacts notes inside private folders`() = runTest {
        val noteRepo = object : NoteRepository {
            private val flow = MutableStateFlow(
                listOf(
                    Note(id = 1L, title = "Publiczna", content = "Jawna treść", folderId = 1L),
                    Note(id = 2L, title = "Tajny projekt", content = "Poufna treść", folderId = 2L)
                )
            )
            override fun observeActiveNotes(): Flow<List<Note>> = flow
            override fun observeDeletedNotes(): Flow<List<Note>> = flow
            override fun searchActiveNotes(query: String): Flow<List<Note>> = flow
            override fun observeActiveNoteCount(): Flow<Int> = MutableStateFlow(2)
            override fun observeLockedNoteCount(): Flow<Int> = MutableStateFlow(0)
            override suspend fun getNoteById(id: Long, decrypt: Boolean) = Result.success(null)
            override suspend fun saveNote(note: Note) = Result.success(1L)
            override suspend fun lockNote(noteId: Long) = Result.success(Unit)
            override suspend fun unlockNotePermanently(noteId: Long) = Result.failure<Note>(NotImplementedError())
            override suspend fun togglePin(noteId: Long, isPinned: Boolean) = Result.success(Unit)
            override suspend fun softDeleteNote(noteId: Long) = Result.success(Unit)
            override suspend fun restoreNote(noteId: Long) = Result.success(Unit)
            override suspend fun deleteNotePermanently(noteId: Long) = Result.success(Unit)
            override suspend fun emptyTrash() = Result.success(Unit)
            override suspend fun getAllActiveNotesSnapshot() = Result.success(flow.value)
        }
        val folderRepo = object : FolderRepository {
            override fun observeFolders(): Flow<List<Folder>> = MutableStateFlow(
                listOf(
                    Folder(id = 1L, name = "Osobiste", colorHex = "#4285F4", isPrivate = false),
                    Folder(id = 2L, name = "Prywatne", colorHex = "#EA4335", isPrivate = true)
                )
            )
            override suspend fun getFolderById(id: Long) = Result.success(null)
            override suspend fun saveFolder(folder: Folder) = Result.success(1L)
            override suspend fun deleteFolder(folderId: Long) = Result.success(Unit)
        }

        val json = ExportNotesUseCase(noteRepo, folderRepo)().getOrThrow()
        assertTrue(json.contains("Publiczna"))
        assertTrue(json.contains("Jawna treść"))
        assertFalse(json.contains("Tajny projekt"))
        assertFalse(json.contains("Poufna treść"))
        assertTrue(json.contains("[PRIVATE_FOLDER]"))
        assertTrue(json.contains("[ENCRYPTED]"))
    }

    @Test
    fun `GetDashboardSummaryUseCase excludes private folder notes before taking 8 recent notes`() = runTest {
        val privateNotes = (1L..10L).map { id ->
            Note(id = id, title = "Priv$id", content = "secret", folderId = 2L, updatedAt = 1000L + id)
        }
        val publicNote = Note(id = 99L, title = "Publiczna", content = "visible", folderId = 1L, updatedAt = 500L)
        val noteRepo = object : NoteRepository {
            private val flow = MutableStateFlow(privateNotes + publicNote)
            override fun observeActiveNotes(): Flow<List<Note>> = flow
            override fun observeDeletedNotes(): Flow<List<Note>> = MutableStateFlow(emptyList())
            override fun searchActiveNotes(query: String): Flow<List<Note>> = flow
            override fun observeActiveNoteCount(): Flow<Int> = MutableStateFlow(11)
            override fun observeLockedNoteCount(): Flow<Int> = MutableStateFlow(0)
            override suspend fun getNoteById(id: Long, decrypt: Boolean) = Result.success(null)
            override suspend fun saveNote(note: Note) = Result.success(1L)
            override suspend fun lockNote(noteId: Long) = Result.success(Unit)
            override suspend fun unlockNotePermanently(noteId: Long) = Result.failure<Note>(NotImplementedError())
            override suspend fun togglePin(noteId: Long, isPinned: Boolean) = Result.success(Unit)
            override suspend fun softDeleteNote(noteId: Long) = Result.success(Unit)
            override suspend fun restoreNote(noteId: Long) = Result.success(Unit)
            override suspend fun deleteNotePermanently(noteId: Long) = Result.success(Unit)
            override suspend fun emptyTrash() = Result.success(Unit)
            override suspend fun getAllActiveNotesSnapshot() = Result.success(flow.value)
        }
        val taskRepo = object : TaskRepository {
            override fun observeAllTasks(): Flow<List<Task>> = MutableStateFlow(emptyList())
            override fun observePendingTaskCount(): Flow<Int> = MutableStateFlow(0)
            override suspend fun getTaskById(id: Long) = Result.success(null)
            override suspend fun saveTask(task: Task) = Result.success(1L)
            override suspend fun toggleTaskCompletion(taskId: Long, isCompleted: Boolean) = Result.success(Unit)
            override suspend fun setTaskArchived(taskId: Long, isArchived: Boolean) = Result.success(Unit)
            override suspend fun deleteTask(taskId: Long) = Result.success(Unit)
        }
        val folderRepo = object : FolderRepository {
            override fun observeFolders(): Flow<List<Folder>> = MutableStateFlow(
                listOf(
                    Folder(id = 1L, name = "Osobiste", colorHex = "#4285F4", isPrivate = false),
                    Folder(id = 2L, name = "Prywatne", colorHex = "#EA4335", isPrivate = true)
                )
            )
            override suspend fun getFolderById(id: Long) = Result.success(null)
            override suspend fun saveFolder(folder: Folder) = Result.success(1L)
            override suspend fun deleteFolder(folderId: Long) = Result.success(Unit)
        }

        val summary = GetDashboardSummaryUseCase(noteRepo, taskRepo, folderRepo)().first()
        assertEquals(listOf(99L), summary.recentNotes.map { it.id })
        assertEquals(10, summary.lockedNotes)
    }

    @Test
    fun `DataMappers serializes image paths and hides them when locked without decryption`() {
        val serialized = DataMappers.serializeImagePaths(listOf("/data/img1.jpg", " ", "/data/img2.jpg"))
        assertEquals("/data/img1.jpg\n/data/img2.jpg", serialized)
        assertEquals(listOf("/data/img1.jpg", "/data/img2.jpg"), DataMappers.parseImagePaths(serialized))

        val lockedEntity = NoteEntity(
            id = 1L,
            title = "Sekret",
            content = "",
            isLocked = true,
            imagePaths = serialized
        )
        assertTrue(DataMappers.noteEntityToDomain(lockedEntity).imagePaths.isEmpty())
        assertEquals(
            listOf("/data/img1.jpg", "/data/img2.jpg"),
            DataMappers.noteEntityToDomain(lockedEntity, decryptedContent = "ok").imagePaths
        )
    }

    @Test
    fun `SaveNoteUseCase accepts note containing only attached image`() = runTest {
        val repo = object : NoteRepository {
            override fun observeActiveNotes(): Flow<List<Note>> = MutableStateFlow(emptyList())
            override fun observeDeletedNotes(): Flow<List<Note>> = MutableStateFlow(emptyList())
            override fun searchActiveNotes(query: String): Flow<List<Note>> = MutableStateFlow(emptyList())
            override fun observeActiveNoteCount(): Flow<Int> = MutableStateFlow(0)
            override fun observeLockedNoteCount(): Flow<Int> = MutableStateFlow(0)
            override suspend fun getNoteById(id: Long, decrypt: Boolean) = Result.success(null)
            override suspend fun saveNote(note: Note) = Result.success(42L)
            override suspend fun lockNote(noteId: Long) = Result.success(Unit)
            override suspend fun unlockNotePermanently(noteId: Long) = Result.failure<Note>(NotImplementedError())
            override suspend fun togglePin(noteId: Long, isPinned: Boolean) = Result.success(Unit)
            override suspend fun softDeleteNote(noteId: Long) = Result.success(Unit)
            override suspend fun restoreNote(noteId: Long) = Result.success(Unit)
            override suspend fun deleteNotePermanently(noteId: Long) = Result.success(Unit)
            override suspend fun emptyTrash() = Result.success(Unit)
            override suspend fun getAllActiveNotesSnapshot() = Result.success(emptyList<Note>())
        }
        val saved = SaveNoteUseCase(repo)(
            Note(title = "", content = "", imagePaths = listOf("/data/img1.jpg"))
        ).getOrThrow()
        assertEquals(42L, saved)
    }
}
