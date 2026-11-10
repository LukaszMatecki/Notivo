package com.lumatech.notivo.data.repository

import com.lumatech.notivo.data.db.dao.TaskDao
import com.lumatech.notivo.data.mapper.DataMappers
import com.lumatech.notivo.domain.model.Task
import com.lumatech.notivo.domain.repository.TaskRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of [TaskRepository].
 */
class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TaskRepository {

    override fun observeAllTasks(): Flow<List<Task>> =
        taskDao.observeAllTasks().map { entities ->
            entities.map(DataMappers::taskEntityToDomain)
        }

    override fun observePendingTaskCount(): Flow<Int> = taskDao.observePendingTaskCount()

    override suspend fun getTaskById(id: Long): Result<Task?> = runCatching {
        withContext(ioDispatcher) { taskDao.getById(id) }?.let(DataMappers::taskEntityToDomain)
    }

    override suspend fun saveTask(task: Task): Result<Long> = runCatching {
        withContext(ioDispatcher) {
            taskDao.insertOrUpdate(DataMappers.taskDomainToEntity(task))
        }
    }

    override suspend fun toggleTaskCompletion(taskId: Long, isCompleted: Boolean): Result<Unit> =
        runCatching {
            withContext(ioDispatcher) {
                taskDao.updateCompletion(taskId, isCompleted)
            }
        }

    override suspend fun setTaskArchived(taskId: Long, isArchived: Boolean): Result<Unit> =
        runCatching {
            withContext(ioDispatcher) {
                taskDao.updateArchiveState(taskId, isArchived)
            }
        }

    override suspend fun deleteTask(taskId: Long): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            taskDao.deleteById(taskId)
        }
    }
}
