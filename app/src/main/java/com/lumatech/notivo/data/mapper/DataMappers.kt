package com.lumatech.notivo.data.mapper

import com.lumatech.notivo.data.db.dao.FolderWithCountTuple
import com.lumatech.notivo.data.db.entity.FolderEntity
import com.lumatech.notivo.data.db.entity.NoteEntity
import com.lumatech.notivo.data.db.entity.TaskEntity
import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.model.Task
import com.lumatech.notivo.domain.model.TaskPriority

/**
 * Pure mapping functions between Room entities and domain models.
 */
object DataMappers {
    fun noteEntityToDomain(
        entity: NoteEntity,
        decryptedContent: String? = null
    ): Note = Note(
        id = entity.id,
        title = entity.title,
        content = when {
            decryptedContent != null -> decryptedContent
            entity.isLocked -> ""
            else -> entity.content
        },
        colorHex = entity.colorHex,
        isLocked = entity.isLocked,
        isPinned = entity.isPinned,
        folderId = entity.folderId,
        imagePaths = if (entity.isLocked && decryptedContent == null) {
            emptyList()
        } else {
            parseImagePaths(entity.imagePaths)
        },
        isDeleted = entity.isDeleted,
        deletedAt = entity.deletedAt,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    fun noteDomainToPlainEntity(note: Note): NoteEntity = NoteEntity(
        id = note.id,
        title = note.title,
        content = note.content,
        colorHex = note.colorHex,
        isLocked = false,
        encryptedContent = null,
        iv = null,
        isPinned = note.isPinned,
        folderId = note.folderId,
        imagePaths = serializeImagePaths(note.imagePaths),
        isDeleted = note.isDeleted,
        deletedAt = note.deletedAt,
        createdAt = note.createdAt,
        updatedAt = note.updatedAt
    )

    fun serializeImagePaths(paths: List<String>): String =
        paths.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    fun parseImagePaths(raw: String): List<String> =
        raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()

    fun folderTupleToDomain(tuple: FolderWithCountTuple): Folder = Folder(
        id = tuple.folder.id,
        name = tuple.folder.name,
        colorHex = tuple.folder.colorHex,
        isPrivate = tuple.folder.isPrivate,
        noteCount = tuple.noteCount,
        createdAt = tuple.folder.createdAt
    )

    fun folderEntityToDomain(entity: FolderEntity, noteCount: Int = 0): Folder = Folder(
        id = entity.id,
        name = entity.name,
        colorHex = entity.colorHex,
        isPrivate = entity.isPrivate,
        noteCount = noteCount,
        createdAt = entity.createdAt
    )

    fun folderDomainToEntity(folder: Folder): FolderEntity = FolderEntity(
        id = folder.id,
        name = folder.name,
        colorHex = folder.colorHex,
        isPrivate = folder.isPrivate,
        createdAt = folder.createdAt
    )

    fun taskEntityToDomain(entity: TaskEntity): Task = Task(
        id = entity.id,
        title = entity.title,
        description = entity.description,
        isCompleted = entity.isCompleted,
        isArchived = entity.isArchived,
        dueDate = entity.dueDate,
        priority = TaskPriority.fromLevel(entity.priority),
        noteId = entity.noteId,
        createdAt = entity.createdAt
    )

    fun taskDomainToEntity(task: Task): TaskEntity = TaskEntity(
        id = task.id,
        title = task.title,
        description = task.description,
        isCompleted = task.isCompleted,
        isArchived = task.isArchived,
        dueDate = task.dueDate,
        priority = task.priority.level,
        noteId = task.noteId,
        createdAt = task.createdAt
    )
}
