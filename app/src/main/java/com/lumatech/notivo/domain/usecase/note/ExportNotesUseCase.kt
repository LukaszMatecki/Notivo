package com.lumatech.notivo.domain.usecase.note

import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.repository.FolderRepository
import com.lumatech.notivo.domain.repository.NoteRepository
import kotlinx.coroutines.flow.first

/**
 * Exports active notes to a JSON string. Locked notes and notes inside biometric/PIN private
 * folders keep their content redacted.
 */
class ExportNotesUseCase(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository? = null
) {
    suspend operator fun invoke(): Result<String> {
        val privateFolderIds = folderRepository?.observeFolders()?.first()
            ?.filter { it.isPrivate }
            ?.map { it.id }
            ?.toSet()
            ?: emptySet()
        return noteRepository.getAllActiveNotesSnapshot().map { notes ->
            buildNotesJson(notes, privateFolderIds)
        }
    }

    private fun buildNotesJson(notes: List<Note>, privateFolderIds: Set<Long>): String = buildString {
        append("{\n")
        append("  \"app\": \"Notivo\",\n")
        append("  \"exportedAt\": ").append(System.currentTimeMillis()).append(",\n")
        append("  \"count\": ").append(notes.size).append(",\n")
        append("  \"notes\": [\n")
        notes.forEachIndexed { index, note ->
            val inPrivateFolder = note.folderId != null && note.folderId in privateFolderIds
            val shouldRedactContent = note.isLocked || inPrivateFolder
            val exportedTitle = if (inPrivateFolder) "[PRIVATE_FOLDER]" else note.title
            val exportedContent = if (shouldRedactContent) "[ENCRYPTED]" else note.content
            append("    {\n")
            append("      \"id\": ").append(note.id).append(",\n")
            append("      \"title\": \"").append(escapeJson(exportedTitle)).append("\",\n")
            append("      \"content\": \"").append(escapeJson(exportedContent)).append("\",\n")
            append("      \"isLocked\": ").append(note.isLocked).append(",\n")
            append("      \"isPinned\": ").append(note.isPinned).append(",\n")
            append("      \"colorHex\": ").append(note.colorHex?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",\n")
            append("      \"folderId\": ").append(note.folderId?.toString() ?: "null").append(",\n")
            append("      \"createdAt\": ").append(note.createdAt).append(",\n")
            append("      \"updatedAt\": ").append(note.updatedAt).append("\n")
            append("    }")
            if (index < notes.lastIndex) append(",")
            append("\n")
        }
        append("  ]\n")
        append("}")
    }

    private fun escapeJson(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u%04x".format(ch.code))
                } else {
                    append(ch)
                }
            }
        }
    }
}
