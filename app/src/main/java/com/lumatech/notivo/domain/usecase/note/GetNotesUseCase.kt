package com.lumatech.notivo.domain.usecase.note

import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.model.NoteFilter
import com.lumatech.notivo.domain.model.SortOrder
import com.lumatech.notivo.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes active notes filtered by folder and search query, ordered with pinned notes first.
 */
class GetNotesUseCase(
    private val noteRepository: NoteRepository
) {
    operator fun invoke(
        query: String = "",
        filter: NoteFilter = NoteFilter.All,
        sortOrder: SortOrder = SortOrder.UPDATED_DESC
    ): Flow<List<Note>> {
        val normalizedQuery = query.trim()
        val sourceFlow = if (normalizedQuery.isEmpty()) {
            noteRepository.observeActiveNotes()
        } else {
            noteRepository.searchActiveNotes(normalizedQuery)
        }

        return sourceFlow.map { notes ->
            notes
                .asSequence()
                .filter { note -> matchesFilter(note, filter) }
                .filter { note -> matchesQuerySafely(note, normalizedQuery) }
                .sortedWith(buildComparator(sortOrder))
                .toList()
        }
    }

    private fun matchesFilter(note: Note, filter: NoteFilter): Boolean = when (filter) {
        NoteFilter.All -> true
        NoteFilter.Unassigned -> note.folderId == null
        is NoteFilter.ByFolder -> note.folderId == filter.folderId
    }

    private fun matchesQuerySafely(note: Note, query: String): Boolean {
        if (query.isEmpty()) return true
        if (note.title.contains(query, ignoreCase = true)) return true
        // Never match plaintext previews on locked notes
        return !note.isLocked && note.content.contains(query, ignoreCase = true)
    }

    private fun buildComparator(sortOrder: SortOrder): Comparator<Note> {
        val secondary = when (sortOrder) {
            SortOrder.UPDATED_DESC -> compareByDescending<Note> { it.updatedAt }
            SortOrder.CREATED_DESC -> compareByDescending<Note> { it.createdAt }
            SortOrder.TITLE_ASC -> compareBy<Note> { it.title.lowercase() }
                .thenByDescending { it.updatedAt }
            SortOrder.COLOR -> compareBy<Note> { it.colorHex ?: "" }
                .thenByDescending { it.updatedAt }
        }
        return compareByDescending<Note> { it.isPinned }.then(secondary)
    }
}
