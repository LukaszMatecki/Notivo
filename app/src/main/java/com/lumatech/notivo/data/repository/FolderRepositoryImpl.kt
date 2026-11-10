package com.lumatech.notivo.data.repository

import com.lumatech.notivo.data.db.dao.FolderDao
import com.lumatech.notivo.data.mapper.DataMappers
import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.domain.repository.FolderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of [FolderRepository].
 */
class FolderRepositoryImpl(
    private val folderDao: FolderDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeFoldersWithCount().map { tuples ->
            tuples.map(DataMappers::folderTupleToDomain)
        }

    override suspend fun getFolderById(id: Long): Result<Folder?> = runCatching {
        withContext(ioDispatcher) { folderDao.getById(id) }
            ?.let { DataMappers.folderEntityToDomain(it) }
    }

    override suspend fun saveFolder(folder: Folder): Result<Long> = runCatching {
        withContext(ioDispatcher) {
            folderDao.insert(DataMappers.folderDomainToEntity(folder))
        }
    }

    override suspend fun deleteFolder(folderId: Long): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            folderDao.deleteById(folderId)
        }
    }
}
