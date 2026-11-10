package com.lumatech.notivo.ui.notes.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.domain.model.FontSizeOption
import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.model.Task
import com.lumatech.notivo.domain.model.TaskPriority
import com.lumatech.notivo.domain.repository.PreferencesRepository
import com.lumatech.notivo.domain.security.PrivateFolderSession
import com.lumatech.notivo.domain.usecase.folder.GetFoldersUseCase
import com.lumatech.notivo.domain.usecase.folder.SaveFolderUseCase
import com.lumatech.notivo.domain.usecase.note.DeleteNoteUseCase
import com.lumatech.notivo.domain.usecase.note.GetNoteByIdUseCase
import com.lumatech.notivo.domain.usecase.note.SaveNoteUseCase
import com.lumatech.notivo.domain.usecase.note.UnlockNoteUseCase
import com.lumatech.notivo.domain.usecase.task.SaveTaskUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NoteEditorUiState(
    val noteId: Long = 0L,
    val title: String = "",
    val content: String = "",
    val colorHex: String? = null,
    val isLocked: Boolean = false,
    val isPinned: Boolean = false,
    val folderId: Long? = null,
    val isPrivateFolder: Boolean = false,
    val imagePaths: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val fontSizeOption: FontSizeOption = FontSizeOption.MEDIUM,
    val isLoaded: Boolean = false,
    val hasChanges: Boolean = false
)

sealed interface NoteEditorEvent {
    data object NoteSaved : NoteEditorEvent
    data object NoteLocked : NoteEditorEvent
    data object NoteLockPending : NoteEditorEvent
    data object NoteUnlocked : NoteEditorEvent
    data object NoteDeletedAndClose : NoteEditorEvent
    data object TaskCreatedFromNote : NoteEditorEvent
    data class ShowError(val message: String) : NoteEditorEvent
}

class NoteEditorViewModel(
    private val getNoteByIdUseCase: GetNoteByIdUseCase,
    private val saveNoteUseCase: SaveNoteUseCase,
    private val unlockNoteUseCase: UnlockNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val getFoldersUseCase: GetFoldersUseCase,
    private val saveFolderUseCase: SaveFolderUseCase,
    private val saveTaskUseCase: SaveTaskUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val privateFolderSession: PrivateFolderSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    val folders: StateFlow<List<Folder>> = getFoldersUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _events = Channel<NoteEditorEvent>(Channel.BUFFERED)
    val events: Flow<NoteEditorEvent> = _events.receiveAsFlow()

    private var initialSnapshotTitle: String = ""
    private var initialSnapshotContent: String = ""
    private var wasDeletedExplicitly = false
    private val persistMutex = Mutex()

    init {
        viewModelScope.launch {
            preferencesRepository.observePreferences().collect { prefs ->
                _uiState.update { it.copy(fontSizeOption = prefs.fontSizeOption) }
            }
        }
    }

    fun loadNote(noteId: Long, initialFolderId: Long? = null, isAlreadyAuthorized: Boolean = false) {
        if (_uiState.value.isLoaded) return
        val prefs = preferencesRepository.getCurrentPreferences()

        if (noteId <= 0L) {
            viewModelScope.launch {
                val isPrivate = initialFolderId != null &&
                    getFoldersUseCase().first().any { it.id == initialFolderId && it.isPrivate }
                _uiState.update {
                    it.copy(
                        noteId = 0L,
                        colorHex = prefs.defaultNoteColorHex,
                        folderId = initialFolderId,
                        isPrivateFolder = isPrivate,
                        fontSizeOption = prefs.fontSizeOption,
                        isLoaded = true
                    )
                }
            }
            return
        }

        viewModelScope.launch {
            val allFolders = getFoldersUseCase().first()
            getNoteByIdUseCase(noteId, decrypt = isAlreadyAuthorized)
                .onSuccess { loaded ->
                    if (loaded == null) {
                        _events.send(NoteEditorEvent.ShowError("Nie znaleziono notatki."))
                        return@onSuccess
                    }
                    val isPrivate = loaded.folderId != null &&
                        allFolders.any { it.id == loaded.folderId && it.isPrivate }
                    initialSnapshotTitle = loaded.title
                    initialSnapshotContent = loaded.content
                    _uiState.update {
                        it.copy(
                            noteId = loaded.id,
                            title = loaded.title,
                            content = loaded.content,
                            colorHex = loaded.colorHex,
                            isLocked = loaded.isLocked,
                            isPinned = loaded.isPinned,
                            folderId = loaded.folderId,
                            isPrivateFolder = isPrivate,
                            imagePaths = loaded.imagePaths,
                            createdAt = loaded.createdAt,
                            updatedAt = loaded.updatedAt,
                            fontSizeOption = prefs.fontSizeOption,
                            isLoaded = true,
                            hasChanges = false
                        )
                    }
                }
                .onFailure {
                    _events.send(NoteEditorEvent.ShowError("Nie udało się odszyfrować notatki."))
                }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { state ->
            state.copy(
                title = title,
                hasChanges = true
            )
        }
    }

    fun updateContent(content: String) {
        _uiState.update { state ->
            state.copy(
                content = content,
                hasChanges = true
            )
        }
    }

    fun updateColor(colorHex: String?) {
        _uiState.update { it.copy(colorHex = colorHex, hasChanges = true) }
    }

    fun updateFolder(folderId: Long?) {
        val isPrivate = folderId != null && folders.value.any { it.id == folderId && it.isPrivate }
        _uiState.update { it.copy(folderId = folderId, isPrivateFolder = isPrivate, hasChanges = true) }
    }

    fun addImage(path: String) {
        if (path.isBlank()) return
        _uiState.update { state ->
            if (path in state.imagePaths) return@update state
            state.copy(
                imagePaths = state.imagePaths + path,
                hasChanges = true
            )
        }
    }

    fun removeImage(path: String) {
        _uiState.update { state ->
            val updated = state.imagePaths.filterNot { it == path }
            if (updated.size == state.imagePaths.size) return@update state
            state.copy(
                imagePaths = updated,
                hasChanges = true
            )
        }
    }

    fun togglePin() {
        _uiState.update { it.copy(isPinned = !it.isPinned, hasChanges = true) }
    }

    fun lockNoteNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocked = true, hasChanges = true) }
            val current = _uiState.value
            if (current.title.isBlank() && current.content.isBlank() && current.imagePaths.isEmpty()) {
                _events.send(NoteEditorEvent.NoteLockPending)
                return@launch
            }
            persistCurrentNote(notifyUser = false)
                .onSuccess { _events.send(NoteEditorEvent.NoteLocked) }
                .onFailure { _events.send(NoteEditorEvent.ShowError("Błąd szyfrowania notatki.")) }
        }
    }

    fun unlockNotePermanently() {
        val currentId = _uiState.value.noteId
        if (currentId <= 0L) {
            _uiState.update { it.copy(isLocked = false, hasChanges = true) }
            return
        }
        viewModelScope.launch {
            val state = _uiState.value
            // Persist current edits first, then decrypt storage representation
            val note = buildDomainNote(state).copy(isLocked = false)
            saveNoteUseCase(note)
                .onSuccess {
                    unlockNoteUseCase(currentId)
                    _uiState.update { it.copy(isLocked = false, hasChanges = false) }
                    _events.send(NoteEditorEvent.NoteUnlocked)
                }
                .onFailure {
                    _events.send(NoteEditorEvent.ShowError("Nie udało się odblokować notatki."))
                }
        }
    }

    fun saveExplicitly() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.title.isBlank() && state.content.isBlank() && state.imagePaths.isEmpty()) {
                _events.send(NoteEditorEvent.ShowError("Wpisz tytuł lub treść przed zapisaniem."))
                return@launch
            }
            persistCurrentNote(notifyUser = true)
        }
    }

    fun autoSaveIfNeeded() {
        if (wasDeletedExplicitly) return
        val state = _uiState.value
        if (!state.isLoaded || !state.hasChanges) return
        if (state.title.isBlank() && state.content.isBlank() && state.imagePaths.isEmpty()) return

        _uiState.update { it.copy(hasChanges = false) }
        viewModelScope.launch {
            persistCurrentNote(notifyUser = false)
        }
    }

    fun createFolderAndAssign(name: String, colorHex: String, isPrivate: Boolean) {
        viewModelScope.launch {
            saveFolderUseCase(Folder(name = name, colorHex = colorHex, isPrivate = isPrivate))
                .onSuccess { newFolderId ->
                    if (isPrivate) {
                        privateFolderSession.unlock(newFolderId)
                    }
                    _uiState.update {
                        it.copy(
                            folderId = newFolderId,
                            isPrivateFolder = isPrivate,
                            hasChanges = true
                        )
                    }
                }
                .onFailure {
                    _events.send(NoteEditorEvent.ShowError("Nie udało się utworzyć folderu."))
                }
        }
    }

    fun createLinkedTask() {
        val state = _uiState.value
        val taskTitle = state.title.trim().ifEmpty {
            state.content.lineSequence().firstOrNull { it.isNotBlank() }?.take(60)?.trim().orEmpty()
        }
        if (taskTitle.isEmpty()) {
            viewModelScope.launch {
                _events.send(NoteEditorEvent.ShowError("Dodaj treść notatki, aby utworzyć zadanie."))
            }
            return
        }

        viewModelScope.launch {
            val ensuredNoteId = if (state.noteId > 0L) {
                state.noteId
            } else {
                persistCurrentNote(notifyUser = false).getOrNull()
            }

            val task = Task(
                title = taskTitle,
                priority = TaskPriority.MEDIUM,
                noteId = ensuredNoteId
            )
            saveTaskUseCase(task)
                .onSuccess { _events.send(NoteEditorEvent.TaskCreatedFromNote) }
                .onFailure { _events.send(NoteEditorEvent.ShowError("Nie udało się utworzyć zadania.")) }
        }
    }

    fun deleteNote() {
        val currentId = _uiState.value.noteId
        wasDeletedExplicitly = true
        viewModelScope.launch {
            if (currentId > 0L) {
                deleteNoteUseCase.softDelete(currentId)
            }
            _events.send(NoteEditorEvent.NoteDeletedAndClose)
        }
    }

    private suspend fun persistCurrentNote(notifyUser: Boolean): Result<Long> = persistMutex.withLock {
        val state = _uiState.value
        val note = buildDomainNote(state)
        return@withLock saveNoteUseCase(note)
            .onSuccess { savedId ->
                initialSnapshotTitle = state.title
                initialSnapshotContent = state.content
                _uiState.update {
                    it.copy(
                        noteId = savedId,
                        updatedAt = System.currentTimeMillis(),
                        hasChanges = false
                    )
                }
                if (notifyUser) {
                    _events.send(NoteEditorEvent.NoteSaved)
                }
            }
            .onFailure {
                _uiState.update { it.copy(hasChanges = true) }
                if (notifyUser) {
                    _events.send(NoteEditorEvent.ShowError("Nie udało się zapisać notatki."))
                }
            }
    }

    private fun buildDomainNote(state: NoteEditorUiState): Note = Note(
        id = state.noteId,
        title = state.title.ifBlank { "" },
        content = state.content,
        colorHex = state.colorHex,
        isLocked = state.isLocked,
        isPinned = state.isPinned,
        folderId = state.folderId,
        imagePaths = state.imagePaths,
        createdAt = state.createdAt,
        updatedAt = System.currentTimeMillis()
    )
}
