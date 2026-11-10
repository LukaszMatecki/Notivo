package com.lumatech.notivo.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lumatech.notivo.di.ServiceLocator
import com.lumatech.notivo.ui.dashboard.DashboardViewModel
import com.lumatech.notivo.ui.folders.FolderManagerViewModel
import com.lumatech.notivo.ui.notes.editor.NoteEditorViewModel
import com.lumatech.notivo.ui.notes.list.NoteListViewModel
import com.lumatech.notivo.ui.security.SecurityViewModel
import com.lumatech.notivo.ui.settings.SettingsViewModel
import com.lumatech.notivo.ui.settings.TrashViewModel
import com.lumatech.notivo.ui.tasks.TaskListViewModel

/**
 * Factory creating ViewModels wired with pure domain use cases and repositories.
 */
class NotivoViewModelFactory(
    private val locator: ServiceLocator
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(NoteListViewModel::class.java) -> NoteListViewModel(
                getNotesUseCase = locator.provideGetNotesUseCase(),
                getFoldersUseCase = locator.provideGetFoldersUseCase(),
                saveFolderUseCase = locator.provideSaveFolderUseCase(),
                deleteNoteUseCase = locator.provideDeleteNoteUseCase(),
                togglePinNoteUseCase = locator.provideTogglePinNoteUseCase(),
                preferencesRepository = locator.preferencesRepository,
                privateFolderSession = locator.privateFolderSession
            )
            modelClass.isAssignableFrom(NoteEditorViewModel::class.java) -> NoteEditorViewModel(
                getNoteByIdUseCase = locator.provideGetNoteByIdUseCase(),
                saveNoteUseCase = locator.provideSaveNoteUseCase(),
                unlockNoteUseCase = locator.provideUnlockNoteUseCase(),
                deleteNoteUseCase = locator.provideDeleteNoteUseCase(),
                getFoldersUseCase = locator.provideGetFoldersUseCase(),
                saveFolderUseCase = locator.provideSaveFolderUseCase(),
                saveTaskUseCase = locator.provideSaveTaskUseCase(),
                preferencesRepository = locator.preferencesRepository,
                privateFolderSession = locator.privateFolderSession
            )
            modelClass.isAssignableFrom(TaskListViewModel::class.java) -> TaskListViewModel(
                getTasksUseCase = locator.provideGetTasksUseCase(),
                saveTaskUseCase = locator.provideSaveTaskUseCase(),
                toggleTaskUseCase = locator.provideToggleTaskUseCase(),
                archiveTaskUseCase = locator.provideArchiveTaskUseCase(),
                deleteTaskUseCase = locator.provideDeleteTaskUseCase(),
                preferencesRepository = locator.preferencesRepository
            )
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(
                getDashboardSummaryUseCase = locator.provideGetDashboardSummaryUseCase(),
                toggleTaskUseCase = locator.provideToggleTaskUseCase(),
                saveTaskUseCase = locator.provideSaveTaskUseCase(),
                preferencesRepository = locator.preferencesRepository
            )
            modelClass.isAssignableFrom(FolderManagerViewModel::class.java) -> FolderManagerViewModel(
                getFoldersUseCase = locator.provideGetFoldersUseCase(),
                saveFolderUseCase = locator.provideSaveFolderUseCase(),
                deleteFolderUseCase = locator.provideDeleteFolderUseCase()
            )
            modelClass.isAssignableFrom(SecurityViewModel::class.java) -> SecurityViewModel(
                pinManager = locator.pinManager
            )
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                preferencesRepository = locator.preferencesRepository,
                pinManager = locator.pinManager,
                cryptoEngine = locator.cryptoManager,
                exportNotesUseCase = locator.provideExportNotesUseCase()
            )
            modelClass.isAssignableFrom(TrashViewModel::class.java) -> TrashViewModel(
                getDeletedNotesUseCase = locator.provideGetDeletedNotesUseCase(),
                getFoldersUseCase = locator.provideGetFoldersUseCase(),
                deleteNoteUseCase = locator.provideDeleteNoteUseCase()
            )
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
