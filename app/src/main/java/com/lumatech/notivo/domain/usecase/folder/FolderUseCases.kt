package com.lumatech.notivo.domain.usecase.folder

import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.domain.model.NotivoError
import com.lumatech.notivo.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes all user folders along with their active note counts.
 */
class GetFoldersUseCase(
    private val folderRepository: FolderRepository
) {
    operator fun invoke(): Flow<List<Folder>> = folderRepository.observeFolders()
}

/**
 * Validates and creates or updates a folder.
 */
class SaveFolderUseCase(
    private val folderRepository: FolderRepository
) {
    suspend operator fun invoke(folder: Folder): Result<Long> {
        val trimmedName = folder.name.trim()
        if (trimmedName.isEmpty()) {
            return Result.failure(NotivoError.ValidationError("Folder name cannot be empty."))
        }
        return folderRepository.saveFolder(folder.copy(name = trimmedName))
    }
}

/**
 * Deletes a folder while keeping its notes unassigned.
 */
class DeleteFolderUseCase(
    private val folderRepository: FolderRepository
) {
    suspend operator fun invoke(folderId: Long): Result<Unit> =
        folderRepository.deleteFolder(folderId)
}
