package com.lumatech.notivo.domain.usecase.dashboard

import com.lumatech.notivo.domain.model.DashboardSummary
import com.lumatech.notivo.domain.repository.FolderRepository
import com.lumatech.notivo.domain.repository.NoteRepository
import com.lumatech.notivo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines live note, task, and folder streams into the Dashboard summary snapshot.
 */
class GetDashboardSummaryUseCase(
    private val noteRepository: NoteRepository,
    private val taskRepository: TaskRepository,
    private val folderRepository: FolderRepository
) {
    operator fun invoke(): Flow<DashboardSummary> {
        return combine(
            noteRepository.observeActiveNotes(),
            taskRepository.observeAllTasks(),
            folderRepository.observeFolders()
        ) { notes, tasks, folders ->
            val pendingTasks = tasks
                .filter { !it.isCompleted && !it.isArchived }
                .sortedWith(
                    compareBy<com.lumatech.notivo.domain.model.Task> { it.dueDate ?: Long.MAX_VALUE }
                        .thenByDescending { it.priority.level }
                )

            val privateFolderIds = folders.filter { it.isPrivate }.map { it.id }.toSet()

            val recentNotes = notes
                .filter { it.folderId == null || it.folderId !in privateFolderIds }
                .sortedWith(
                    compareByDescending<com.lumatech.notivo.domain.model.Note> { it.isPinned }
                        .thenByDescending { it.updatedAt }
                )
                .take(8)

            DashboardSummary(
                totalNotes = notes.size,
                pendingTasks = pendingTasks.size,
                lockedNotes = notes.count {
                    it.isLocked || (it.folderId != null && it.folderId in privateFolderIds)
                },
                upcomingTasks = pendingTasks.take(4),
                recentNotes = recentNotes,
                folders = folders
            )
        }
    }
}
