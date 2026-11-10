package com.lumatech.notivo.di

import android.content.Context
import com.lumatech.notivo.data.db.NotivoDatabase
import com.lumatech.notivo.data.preferences.PreferencesManager
import com.lumatech.notivo.data.repository.FolderRepositoryImpl
import com.lumatech.notivo.data.repository.NoteRepositoryImpl
import com.lumatech.notivo.data.repository.TaskRepositoryImpl
import com.lumatech.notivo.data.security.AndroidSecureKeyValueStore
import com.lumatech.notivo.data.security.BiometricHelper
import com.lumatech.notivo.data.security.CryptoManager
import com.lumatech.notivo.data.security.PinManager
import com.lumatech.notivo.domain.repository.FolderRepository
import com.lumatech.notivo.domain.repository.NoteRepository
import com.lumatech.notivo.domain.repository.PreferencesRepository
import com.lumatech.notivo.domain.repository.TaskRepository
import com.lumatech.notivo.domain.security.PrivateFolderSession
import com.lumatech.notivo.domain.usecase.dashboard.GetDashboardSummaryUseCase
import com.lumatech.notivo.domain.usecase.folder.DeleteFolderUseCase
import com.lumatech.notivo.domain.usecase.folder.GetFoldersUseCase
import com.lumatech.notivo.domain.usecase.folder.SaveFolderUseCase
import com.lumatech.notivo.domain.usecase.note.DeleteNoteUseCase
import com.lumatech.notivo.domain.usecase.note.ExportNotesUseCase
import com.lumatech.notivo.domain.usecase.note.GetDeletedNotesUseCase
import com.lumatech.notivo.domain.usecase.note.GetNoteByIdUseCase
import com.lumatech.notivo.domain.usecase.note.GetNotesUseCase
import com.lumatech.notivo.domain.usecase.note.LockNoteUseCase
import com.lumatech.notivo.domain.usecase.note.SaveNoteUseCase
import com.lumatech.notivo.domain.usecase.note.TogglePinNoteUseCase
import com.lumatech.notivo.domain.usecase.note.UnlockNoteUseCase
import com.lumatech.notivo.domain.usecase.task.ArchiveTaskUseCase
import com.lumatech.notivo.domain.usecase.task.DeleteTaskUseCase
import com.lumatech.notivo.domain.usecase.task.GetTasksUseCase
import com.lumatech.notivo.domain.usecase.task.SaveTaskUseCase
import com.lumatech.notivo.domain.usecase.task.ToggleTaskUseCase

/**
 * Lightweight manual dependency container initialized from [NotivoApplication].
 */
class ServiceLocator(context: Context) {
    private val appContext: Context = context.applicationContext

    val database: NotivoDatabase by lazy { NotivoDatabase.create(appContext) }
    val cryptoManager: CryptoManager by lazy { CryptoManager() }
    val biometricHelper: BiometricHelper by lazy { BiometricHelper(cryptoManager) }
    val pinManager: PinManager by lazy {
        PinManager(AndroidSecureKeyValueStore(appContext))
    }
    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesManager(appContext)
    }
    val privateFolderSession: PrivateFolderSession by lazy {
        PrivateFolderSession()
    }

    val noteRepository: NoteRepository by lazy {
        NoteRepositoryImpl(database.noteDao(), cryptoManager)
    }
    val taskRepository: TaskRepository by lazy {
        TaskRepositoryImpl(database.taskDao())
    }
    val folderRepository: FolderRepository by lazy {
        FolderRepositoryImpl(database.folderDao())
    }

    // Note use cases
    fun provideGetNotesUseCase() = GetNotesUseCase(noteRepository)
    fun provideGetNoteByIdUseCase() = GetNoteByIdUseCase(noteRepository)
    fun provideSaveNoteUseCase() = SaveNoteUseCase(noteRepository)
    fun provideDeleteNoteUseCase() = DeleteNoteUseCase(noteRepository)
    fun provideGetDeletedNotesUseCase() = GetDeletedNotesUseCase(noteRepository)
    fun provideLockNoteUseCase() = LockNoteUseCase(noteRepository)
    fun provideUnlockNoteUseCase() = UnlockNoteUseCase(noteRepository)
    fun provideTogglePinNoteUseCase() = TogglePinNoteUseCase(noteRepository)
    fun provideExportNotesUseCase() = ExportNotesUseCase(noteRepository, folderRepository)

    // Task use cases
    fun provideGetTasksUseCase() = GetTasksUseCase(taskRepository)
    fun provideSaveTaskUseCase() = SaveTaskUseCase(taskRepository)
    fun provideToggleTaskUseCase() = ToggleTaskUseCase(taskRepository)
    fun provideArchiveTaskUseCase() = ArchiveTaskUseCase(taskRepository)
    fun provideDeleteTaskUseCase() = DeleteTaskUseCase(taskRepository)

    // Folder use cases
    fun provideGetFoldersUseCase() = GetFoldersUseCase(folderRepository)
    fun provideSaveFolderUseCase() = SaveFolderUseCase(folderRepository)
    fun provideDeleteFolderUseCase() = DeleteFolderUseCase(folderRepository)

    // Dashboard use case
    fun provideGetDashboardSummaryUseCase() =
        GetDashboardSummaryUseCase(noteRepository, taskRepository, folderRepository)
}
