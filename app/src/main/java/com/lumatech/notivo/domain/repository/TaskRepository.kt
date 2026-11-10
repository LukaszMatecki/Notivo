package com.lumatech.notivo.domain.repository

import com.lumatech.notivo.domain.model.Task
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for planner tasks.
 */
interface TaskRepository {
    fun observeAllTasks(): Flow<List<Task>>
    fun observePendingTaskCount(): Flow<Int>
    suspend fun getTaskById(id: Long): Result<Task?>
    suspend fun saveTask(task: Task): Result<Long>
    suspend fun toggleTaskCompletion(taskId: Long, isCompleted: Boolean): Result<Unit>
    suspend fun setTaskArchived(taskId: Long, isArchived: Boolean): Result<Unit>
    suspend fun deleteTask(taskId: Long): Result<Unit>
}
