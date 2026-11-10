package com.lumatech.notivo.ui.notes.list

import com.lumatech.notivo.domain.model.AppPreferences
import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.domain.model.FontSizeOption
import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.model.NoteFilter
import com.lumatech.notivo.domain.model.NoteLayoutMode
import com.lumatech.notivo.domain.model.SortOrder
import com.lumatech.notivo.domain.repository.PreferencesRepository
import com.lumatech.notivo.domain.security.PrivateFolderSession
import com.lumatech.notivo.domain.usecase.folder.GetFoldersUseCase
import com.lumatech.notivo.domain.usecase.folder.SaveFolderUseCase
import com.lumatech.notivo.domain.usecase.note.DeleteNoteUseCase
import com.lumatech.notivo.domain.usecase.note.GetNotesUseCase
import com.lumatech.notivo.domain.usecase.note.TogglePinNoteUseCase
import com.lumatech.notivo.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

data class NoteListUiState(
    val notes: List<Note> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val activeFilter: NoteFilter = NoteFilter.All,
    val isCurrentFolderLocked: Boolean = false,
    val lockedActiveFolder: Folder? = null,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.UPDATED_DESC,
    val layoutMode: NoteLayoutMode = NoteLayoutMode.GRID,
    val fontSizeOption: FontSizeOption = FontSizeOption.MEDIUM,
    val isLoading: Boolean = true
)

sealed interface NoteListEvent {
    data class ShowUndoDelete(val noteId: Long) : NoteListEvent
    data class ShowMessage(val message: String) : NoteListEvent
    data object NotePinned : NoteListEvent
    data object NoteUnpinned : NoteListEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NoteListViewModel(
    private val getNotesUseCase: GetNotesUseCase,
    private val getFoldersUseCase: GetFoldersUseCase,
    private val saveFolderUseCase: SaveFolderUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val togglePinNoteUseCase: TogglePinNoteUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val privateFolderSession: PrivateFolderSession
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow<NoteFilter>(NoteFilter.All)

    private val _events = Channel<NoteListEvent>(Channel.BUFFERED)
    val events: Flow<NoteListEvent> = _events.receiveAsFlow()

    private val debouncedQueryFlow = searchQueryFlow.debounce(Constants.SEARCH_DEBOUNCE_MS)

    private val notesFlow = combine(
        debouncedQueryFlow,
        filterFlow,
        preferencesRepository.observePreferences()
    ) { query, filter, prefs ->
        TripleWrapper(query, filter, prefs)
    }.flatMapLatest { params ->
        getNotesUseCase(
            query = params.query,
            filter = params.filter,
            sortOrder = params.prefs.sortOrder
        )
    }

    private val sessionInputsFlow = combine(
        filterFlow,
        searchQueryFlow,
        preferencesRepository.observePreferences(),
        privateFolderSession.unlockedFolderIds
    ) { filter, rawQuery, prefs, unlockedIds ->
        SessionInputs(filter, rawQuery, prefs, unlockedIds)
    }

    val uiState: StateFlow<NoteListUiState> = combine(
        notesFlow,
        getFoldersUseCase(),
        sessionInputsFlow
    ) { notes, folders, inputs ->
        val privateFoldersById = folders.filter { it.isPrivate }.associateBy { it.id }
        val activePrivateFolder = (inputs.filter as? NoteFilter.ByFolder)?.folderId?.let { id ->
            privateFoldersById[id]
        }
        val isLockedFolderSelected = activePrivateFolder != null &&
            activePrivateFolder.id !in inputs.unlockedIds

        // Private-folder notes are strictly isolated: hidden from All/Unassigned/Search and
        // shown only while inside their explicitly unlocked folder filter.
        val visibleNotes = when (val filter = inputs.filter) {
            is NoteFilter.ByFolder -> {
                if (filter.folderId in privateFoldersById && filter.folderId !in inputs.unlockedIds) {
                    emptyList()
                } else {
                    notes
                }
            }
            else -> notes.filter { note ->
                note.folderId == null || note.folderId !in privateFoldersById
            }
        }

        NoteListUiState(
            notes = visibleNotes,
            folders = folders,
            activeFilter = inputs.filter,
            isCurrentFolderLocked = isLockedFolderSelected,
            lockedActiveFolder = if (isLockedFolderSelected) activePrivateFolder else null,
            searchQuery = inputs.rawQuery,
            sortOrder = inputs.prefs.sortOrder,
            layoutMode = inputs.prefs.noteLayoutMode,
            fontSizeOption = inputs.prefs.fontSizeOption,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NoteListUiState()
    )

    fun onSearchQueryChanged(query: String) {
        searchQueryFlow.value = query
    }

    fun selectFilter(filter: NoteFilter) {
        privateFolderSession.lockAll()
        filterFlow.value = filter
    }

    fun authorizePrivateFolder(folderId: Long) {
        privateFolderSession.unlock(folderId)
        filterFlow.value = NoteFilter.ByFolder(folderId)
    }

    fun isFolderUnlocked(folder: Folder): Boolean {
        return !folder.isPrivate || privateFolderSession.isUnlocked(folder.id)
    }

    fun createFolder(name: String, colorHex: String, isPrivate: Boolean) {
        viewModelScope.launch {
            val folder = Folder(name = name, colorHex = colorHex, isPrivate = isPrivate)
            saveFolderUseCase(folder)
                .onSuccess { newId ->
                    if (!isPrivate) {
                        filterFlow.value = NoteFilter.ByFolder(newId)
                    }
                }
                .onFailure { error ->
                    _events.send(NoteListEvent.ShowMessage(error.message ?: "Nie udało się utworzyć folderu."))
                }
        }
    }

    fun softDeleteNote(noteId: Long) {
        viewModelScope.launch {
            deleteNoteUseCase.softDelete(noteId)
                .onSuccess { _events.send(NoteListEvent.ShowUndoDelete(noteId)) }
                .onFailure { _events.send(NoteListEvent.ShowMessage("Nie udało się usunąć notatki.")) }
        }
    }

    fun restoreDeletedNote(noteId: Long) {
        viewModelScope.launch {
            deleteNoteUseCase.restore(noteId)
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            val nextPinned = !note.isPinned
            togglePinNoteUseCase(note.id, nextPinned)
                .onSuccess {
                    _events.send(
                        if (nextPinned) NoteListEvent.NotePinned else NoteListEvent.NoteUnpinned
                    )
                }
        }
    }

    private data class TripleWrapper(
        val query: String,
        val filter: NoteFilter,
        val prefs: AppPreferences
    )

    private data class SessionInputs(
        val filter: NoteFilter,
        val rawQuery: String,
        val prefs: AppPreferences,
        val unlockedIds: Set<Long>
    )
}
