package com.lumatech.notivo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.usecase.folder.GetFoldersUseCase
import com.lumatech.notivo.domain.usecase.note.DeleteNoteUseCase
import com.lumatech.notivo.domain.usecase.note.GetDeletedNotesUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrashUiState(
    val notes: List<Note> = emptyList(),
    val privateFolderIds: Set<Long> = emptySet()
)

sealed interface TrashEvent {
    data object Restored : TrashEvent
    data object DeletedForever : TrashEvent
}

class TrashViewModel(
    getDeletedNotesUseCase: GetDeletedNotesUseCase,
    getFoldersUseCase: GetFoldersUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase
) : ViewModel() {

    val uiState: StateFlow<TrashUiState> = combine(
        getDeletedNotesUseCase(),
        getFoldersUseCase()
    ) { notes, folders ->
        TrashUiState(
            notes = notes,
            privateFolderIds = folders.filter { it.isPrivate }.map { it.id }.toSet()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrashUiState()
    )

    private val _events = Channel<TrashEvent>(Channel.BUFFERED)
    val events: Flow<TrashEvent> = _events.receiveAsFlow()

    fun restore(noteId: Long) {
        viewModelScope.launch {
            deleteNoteUseCase.restore(noteId)
                .onSuccess { _events.send(TrashEvent.Restored) }
        }
    }

    fun deletePermanently(noteId: Long) {
        viewModelScope.launch {
            deleteNoteUseCase.deletePermanently(noteId)
                .onSuccess { _events.send(TrashEvent.DeletedForever) }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            deleteNoteUseCase.emptyTrash()
        }
    }
}
