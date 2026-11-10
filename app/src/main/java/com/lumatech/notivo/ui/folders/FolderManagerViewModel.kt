package com.lumatech.notivo.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.domain.usecase.folder.DeleteFolderUseCase
import com.lumatech.notivo.domain.usecase.folder.GetFoldersUseCase
import com.lumatech.notivo.domain.usecase.folder.SaveFolderUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface FolderManagerEvent {
    data class ShowMessage(val message: String) : FolderManagerEvent
}

class FolderManagerViewModel(
    getFoldersUseCase: GetFoldersUseCase,
    private val saveFolderUseCase: SaveFolderUseCase,
    private val deleteFolderUseCase: DeleteFolderUseCase
) : ViewModel() {

    val folders: StateFlow<List<Folder>> = getFoldersUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _events = Channel<FolderManagerEvent>(Channel.BUFFERED)
    val events: Flow<FolderManagerEvent> = _events.receiveAsFlow()

    fun saveFolder(id: Long = 0L, name: String, colorHex: String, isPrivate: Boolean) {
        viewModelScope.launch {
            saveFolderUseCase(
                Folder(
                    id = id,
                    name = name,
                    colorHex = colorHex,
                    isPrivate = isPrivate
                )
            ).onFailure { error ->
                _events.send(
                    FolderManagerEvent.ShowMessage(
                        error.message ?: "Nie udało się zapisać folderu."
                    )
                )
            }
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            deleteFolderUseCase(folderId)
                .onFailure {
                    _events.send(FolderManagerEvent.ShowMessage("Nie udało się usunąć folderu."))
                }
        }
    }
}
