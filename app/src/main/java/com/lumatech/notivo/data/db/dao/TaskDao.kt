package com.lumatech.notivo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lumatech.notivo.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for planner tasks.
 */
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY is_archived ASC, is_completed ASC, due_date ASC, priority DESC, created_at DESC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 0 AND is_archived = 0")
    fun observePendingTaskCount(): Flow<Int>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getById(taskId: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(task: TaskEntity): Long

    @Query("UPDATE tasks SET is_completed = :isCompleted WHERE id = :taskId")
    suspend fun updateCompletion(taskId: Long, isCompleted: Boolean)

    @Query("UPDATE tasks SET is_archived = :isArchived WHERE id = :taskId")
    suspend fun updateArchiveState(taskId: Long, isArchived: Boolean)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: Long)
}
