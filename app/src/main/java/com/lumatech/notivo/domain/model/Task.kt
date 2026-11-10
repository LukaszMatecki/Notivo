package com.lumatech.notivo.domain.model

/**
 * Priority levels for planner tasks.
 */
enum class TaskPriority(val level: Int) {
    LOW(0),
    MEDIUM(1),
    HIGH(2);

    companion object {
        fun fromLevel(level: Int): TaskPriority =
            entries.firstOrNull { it.level == level } ?: MEDIUM
    }
}

/**
 * Pure domain model representing a planner task, optionally linked to a note.
 */
data class Task(
    val id: Long = 0L,
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false,
    val dueDate: Long? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val noteId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
