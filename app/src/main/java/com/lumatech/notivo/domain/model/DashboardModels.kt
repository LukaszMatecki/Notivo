package com.lumatech.notivo.domain.model

/**
 * Filter criteria applied to the notes list.
 */
sealed interface NoteFilter {
    data object All : NoteFilter
    data object Unassigned : NoteFilter
    data class ByFolder(val folderId: Long) : NoteFilter
}

/**
 * Aggregated overview rendered on the Dashboard screen.
 */
data class DashboardSummary(
    val totalNotes: Int,
    val pendingTasks: Int,
    val lockedNotes: Int,
    val upcomingTasks: List<Task>,
    val recentNotes: List<Note>,
    val folders: List<Folder>
)
