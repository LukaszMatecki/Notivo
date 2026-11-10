package com.lumatech.notivo

import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.model.NoteFilter
import com.lumatech.notivo.domain.model.NotivoError
import com.lumatech.notivo.domain.model.SortOrder
import com.lumatech.notivo.domain.model.Task
import com.lumatech.notivo.domain.model.TaskPriority
import com.lumatech.notivo.domain.repository.FolderRepository
import com.lumatech.notivo.domain.repository.NoteRepository
import com.lumatech.notivo.domain.repository.TaskRepository
import com.lumatech.notivo.domain.usecase.dashboard.GetDashboardSummaryUseCase
import com.lumatech.notivo.domain.usecase.note.ExportNotesUseCase
import com.lumatech.notivo.domain.usecase.note.GetNotesUseCase
import com.lumatech.notivo.domain.usecase.note.SaveNoteUseCase
import com.lumatech.notivo.domain.usecase.task.ArchiveTaskUseCase
import com.lumatech.notivo.domain.usecase.task.GetTasksUseCase
import com.lumatech.notivo.ui.common.UndoRedoHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCasesTest {

    private class FakeNoteRepo(initial: List<Note>) : NoteRepository {
        private val notes = MutableStateFlow(initial)
        override fun observeActiveNotes(): Flow<List<Note>> = notes.map { list -> list.filter { !it.isDeleted } }
        override fun observeDeletedNotes(): Flow<List<Note>> = notes.map { list -> list.filter { it.isDeleted } }
        override fun searchActiveNotes(query: String): Flow<List<Note>> = observeActiveNotes()
        override fun observeActiveNoteCount(): Flow<Int> = observeActiveNotes().map { it.size }
        override fun observeLockedNoteCount(): Flow<Int> = observeActiveNotes().map { list -> list.count { it.isLocked } }
        override suspend fun getNoteById(id: Long, decrypt: Boolean): Result<Note?> =
            Result.success(notes.value.firstOrNull { it.id == id })
        override suspend fun saveNote(note: Note): Result<Long> = Result.success(1L)
        override suspend fun lockNote(noteId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun unlockNotePermanently(noteId: Long): Result<Note> =
            Result.failure(NotImplementedError())
        override suspend fun togglePin(noteId: Long, isPinned: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun softDeleteNote(noteId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun restoreNote(noteId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun deleteNotePermanently(noteId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun emptyTrash(): Result<Unit> = Result.success(Unit)
        override suspend fun getAllActiveNotesSnapshot(): Result<List<Note>> = Result.success(notes.value)
    }

    private class FakeTaskRepo(initial: List<Task>) : TaskRepository {
        private val tasks = MutableStateFlow(initial)
        override fun observeAllTasks(): Flow<List<Task>> = tasks
        override fun observePendingTaskCount(): Flow<Int> =
            tasks.map { list -> list.count { !it.isCompleted && !it.isArchived } }
        override suspend fun getTaskById(id: Long): Result<Task?> = Result.success(null)
        override suspend fun saveTask(task: Task): Result<Long> = Result.success(1L)
        override suspend fun toggleTaskCompletion(taskId: Long, isCompleted: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun setTaskArchived(taskId: Long, isArchived: Boolean): Result<Unit> {
            tasks.value = tasks.value.map { task ->
                if (task.id == taskId) task.copy(isArchived = isArchived) else task
            }
            return Result.success(Unit)
        }
        override suspend fun deleteTask(taskId: Long): Result<Unit> = Result.success(Unit)
    }

    private class FakeFolderRepo(initial: List<Folder>) : FolderRepository {
        private val folders = MutableStateFlow(initial)
        override fun observeFolders(): Flow<List<Folder>> = folders
        override suspend fun getFolderById(id: Long): Result<Folder?> = Result.success(null)
        override suspend fun saveFolder(folder: Folder): Result<Long> = Result.success(1L)
        override suspend fun deleteFolder(folderId: Long): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `GetNotesUseCase keeps pinned notes first and sorts remaining`() = runTest {
        val repo = FakeNoteRepo(
            listOf(
                Note(id = 1, title = "Zebra", content = "a", isPinned = false, updatedAt = 300),
                Note(id = 2, title = "Alfa", content = "b", isPinned = false, updatedAt = 100),
                Note(id = 3, title = "Beta", content = "c", isPinned = true, updatedAt = 50)
            )
        )
        val useCase = GetNotesUseCase(repo)

        val alphaSorted = useCase(filter = NoteFilter.All, sortOrder = SortOrder.TITLE_ASC).first()
        assertEquals(listOf(3L, 2L, 1L), alphaSorted.map { it.id })
    }

    @Test
    fun `SaveNoteUseCase rejects completely blank notes`() = runTest {
        val useCase = SaveNoteUseCase(FakeNoteRepo(emptyList()))
        val result = useCase(Note(title = "   ", content = ""))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NotivoError.ValidationError)
    }

    @Test
    fun `ExportNotesUseCase redacts locked content and produces valid JSON fields`() = runTest {
        val repo = FakeNoteRepo(
            listOf(
                Note(id = 1, title = "Jawna \"test\"", content = "Linia1\nLinia2", isLocked = false),
                Note(id = 2, title = "Sekret", content = "tajne", isLocked = true)
            )
        )
        val json = ExportNotesUseCase(repo)().getOrThrow()

        assertTrue(json.contains("\"app\": \"Notivo\""))
        assertTrue(json.contains("Jawna \\\"test\\\""))
        assertTrue(json.contains("Linia1\\nLinia2"))
        assertTrue(json.contains("[ENCRYPTED]"))
        assertFalse(json.contains("\"tajne\""))
    }

    @Test
    fun `GetDashboardSummaryUseCase aggregates totals accurately and ignores archived tasks`() = runTest {
        val summary = GetDashboardSummaryUseCase(
            noteRepository = FakeNoteRepo(
                listOf(
                    Note(id = 1, title = "A", content = "1", isLocked = false),
                    Note(id = 2, title = "B", content = "", isLocked = true)
                )
            ),
            taskRepository = FakeTaskRepo(
                listOf(
                    Task(id = 1, title = "T1", isCompleted = false, priority = TaskPriority.HIGH),
                    Task(id = 2, title = "T2", isCompleted = true, priority = TaskPriority.LOW),
                    Task(id = 3, title = "Archived", isCompleted = false, isArchived = true, priority = TaskPriority.HIGH)
                )
            ),
            folderRepository = FakeFolderRepo(
                listOf(Folder(id = 1, name = "Studia", colorHex = "#34A853"))
            )
        )().first()

        assertEquals(2, summary.totalNotes)
        assertEquals(1, summary.pendingTasks)
        assertEquals(1, summary.lockedNotes)
        assertEquals(1, summary.upcomingTasks.size)
        assertEquals(2, summary.recentNotes.size)
        assertEquals(1, summary.folders.size)
    }

    @Test
    fun `ArchiveTaskUseCase toggles archive state reflected by GetTasksUseCase`() = runTest {
        val repo = FakeTaskRepo(
            listOf(Task(id = 10L, title = "Do zarchiwizowania", isArchived = false))
        )
        ArchiveTaskUseCase(repo)(10L, true).getOrThrow()
        assertTrue(GetTasksUseCase(repo)().first().first().isArchived)

        ArchiveTaskUseCase(repo)(10L, false).getOrThrow()
        assertFalse(GetTasksUseCase(repo)().first().first().isArchived)
    }

    @Test
    fun `UndoRedoHistory tracks edits and resets redo on new edit`() {
        val history = UndoRedoHistory()
        history.reset("Start")
        assertFalse(history.canUndo())
        assertFalse(history.canRedo())

        history.recordChange("Start A")
        history.recordChange("Start AB")
        assertTrue(history.canUndo())

        assertEquals("Start A", history.undo())
        assertTrue(history.canRedo())

        history.recordChange("Start C")
        assertFalse(history.canRedo())
        assertEquals("Start A", history.undo())
        assertEquals("Start", history.undo())
        assertNull(history.undo())
    }
}
