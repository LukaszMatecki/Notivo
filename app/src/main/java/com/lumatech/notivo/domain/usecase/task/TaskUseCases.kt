package com.lumatech.notivo.domain.usecase.task

import com.lumatech.notivo.domain.model.NotivoError
import com.lumatech.notivo.domain.model.Task
import com.lumatech.notivo.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes tasks filtered optionally by a search query.
 */
class GetTasksUseCase(
    private val taskRepository: TaskRepository
) {
    operator fun invoke(query: String = ""): Flow<List<Task>> {
        val trimmed = query.trim()
        return taskRepository.observeAllTasks().map { tasks ->
            val filtered = if (trimmed.isEmpty()) {
                tasks
            } else {
                tasks.filter { task ->
                    task.title.contains(trimmed, ignoreCase = true) ||
                        (task.description?.contains(trimmed, ignoreCase = true) == true)
                }
            }
            filtered.sortedWith(
                compareBy<Task> { it.isCompleted }
                    .thenBy { it.dueDate ?: Long.MAX_VALUE }
                    .thenByDescending { it.priority.level }
                    .thenByDescending { it.createdAt }
            )
        }
    }
}

/**
 * Validates and persists a planner task.
 */
class SaveTaskUseCase(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(task: Task): Result<Long> {
        if (task.title.isBlank()) {
            return Result.failure(NotivoError.ValidationError("Task title cannot be empty."))
        }
        return taskRepository.saveTask(
            task.copy(
                title = task.title.trim(),
                description = task.description?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
    }
}

/**
 * Toggles task completion state.
 */
class ToggleTaskUseCase(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(taskId: Long, isCompleted: Boolean): Result<Unit> =
        taskRepository.toggleTaskCompletion(taskId, isCompleted)
}

/**
 * Moves a task between active/completed lists and archive.
 */
class ArchiveTaskUseCase(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(taskId: Long, isArchived: Boolean): Result<Unit> =
        taskRepository.setTaskArchived(taskId, isArchived)
}

/**
 * Deletes a planner task.
 */
class DeleteTaskUseCase(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(taskId: Long): Result<Unit> =
        taskRepository.deleteTask(taskId)
}
