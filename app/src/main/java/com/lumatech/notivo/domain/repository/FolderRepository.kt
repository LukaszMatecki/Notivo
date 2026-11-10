package com.lumatech.notivo.domain.repository

import com.lumatech.notivo.domain.model.Folder
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for note folders and privacy categories.
 */
interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>
    suspend fun getFolderById(id: Long): Result<Folder?>
    suspend fun saveFolder(folder: Folder): Result<Long>
    suspend fun deleteFolder(folderId: Long): Result<Unit>
}
