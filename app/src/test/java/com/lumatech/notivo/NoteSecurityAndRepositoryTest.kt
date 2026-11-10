package com.lumatech.notivo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lumatech.notivo.data.db.NotivoDatabase
import com.lumatech.notivo.data.repository.FolderRepositoryImpl
import com.lumatech.notivo.data.repository.NoteRepositoryImpl
import com.lumatech.notivo.data.security.CryptoEngine
import com.lumatech.notivo.data.security.EncryptedPayload
import com.lumatech.notivo.data.security.KeySecurityLevel
import com.lumatech.notivo.domain.model.Folder
import com.lumatech.notivo.domain.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteSecurityAndRepositoryTest {

    private lateinit var database: NotivoDatabase
    private lateinit var noteRepository: NoteRepositoryImpl
    private lateinit var folderRepository: FolderRepositoryImpl

    private class JvmAesGcmCryptoEngine : CryptoEngine {
        private val secretKey: SecretKey = KeyGenerator.getInstance("AES").apply {
            init(256)
        }.generateKey()

        override fun encrypt(plainText: String): EncryptedPayload {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            return EncryptedPayload(cipherText = encrypted, iv = cipher.iv)
        }

        override fun decrypt(cipherText: ByteArray, iv: ByteArray): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            return String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
        }

        override fun getSecurityLevel(): KeySecurityLevel = KeySecurityLevel.TEE
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotivoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteRepository = NoteRepositoryImpl(
            noteDao = database.noteDao(),
            cryptoEngine = JvmAesGcmCryptoEngine()
        )
        folderRepository = FolderRepositoryImpl(database.folderDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `locked note encrypts content with unique IV and hides plaintext in database`() = runTest {
        val secretContent = "Tajne hasło do konta bankowego: XYZ-987"
        val noteId = noteRepository.saveNote(
            Note(
                title = "Bank",
                content = secretContent,
                isLocked = true
            )
        ).getOrThrow()

        val rawEntity = database.noteDao().getById(noteId)
        assertNotNull(rawEntity)
        assertTrue(rawEntity!!.isLocked)
        assertEquals("", rawEntity.content)
        assertNotNull(rawEntity.encryptedContent)
        assertNotNull(rawEntity.iv)

        // Listing (decrypt = false) never exposes secret content
        val masked = noteRepository.getNoteById(noteId, decrypt = false).getOrThrow()!!
        assertEquals("", masked.content)

        // Authorized load (decrypt = true) restores plaintext accurately
        val decrypted = noteRepository.getNoteById(noteId, decrypt = true).getOrThrow()!!
        assertEquals(secretContent, decrypted.content)

        // Search must NOT match encrypted body content, only title
        val bodySearch = noteRepository.searchActiveNotes("XYZ-987").first()
        assertTrue(bodySearch.isEmpty())

        val titleSearch = noteRepository.searchActiveNotes("Bank").first()
        assertEquals(1, titleSearch.size)

        // Re-saving generates a brand new IV
        val firstIv = rawEntity.iv!!.copyOf()
        noteRepository.saveNote(decrypted.copy(updatedAt = System.currentTimeMillis() + 10))
        val reSavedEntity = database.noteDao().getById(noteId)!!
        assertFalse(firstIv.contentEquals(reSavedEntity.iv!!))
    }

    @Test
    fun `lock and unlock transitions preserve content and update storage safely`() = runTest {
        val plain = "Notatka tymczasowo jawna"
        val id = noteRepository.saveNote(Note(title = "Tytuł", content = plain, isLocked = false)).getOrThrow()

        noteRepository.lockNote(id).getOrThrow()
        val lockedEntity = database.noteDao().getById(id)!!
        assertTrue(lockedEntity.isLocked)
        assertEquals("", lockedEntity.content)
        assertNotNull(lockedEntity.encryptedContent)

        val unlocked = noteRepository.unlockNotePermanently(id).getOrThrow()
        assertFalse(unlocked.isLocked)
        assertEquals(plain, unlocked.content)

        val unlockedEntity = database.noteDao().getById(id)!!
        assertFalse(unlockedEntity.isLocked)
        assertEquals(plain, unlockedEntity.content)
        assertArrayEquals(null, unlockedEntity.encryptedContent)
    }

    @Test
    fun `soft delete hides note from folder count and restore brings it back`() = runTest {
        val folderId = folderRepository.saveFolder(
            Folder(name = "Studia", colorHex = "#34A853", isPrivate = false)
        ).getOrThrow()

        val noteId = noteRepository.saveNote(
            Note(title = "Wykład 1", content = "SQL", folderId = folderId)
        ).getOrThrow()

        assertEquals(1, folderRepository.observeFolders().first().first().noteCount)

        noteRepository.softDeleteNote(noteId).getOrThrow()
        assertEquals(0, folderRepository.observeFolders().first().first().noteCount)
        assertEquals(1, noteRepository.observeDeletedNotes().first().size)

        noteRepository.restoreNote(noteId).getOrThrow()
        assertEquals(1, folderRepository.observeFolders().first().first().noteCount)
        assertEquals(0, noteRepository.observeDeletedNotes().first().size)
    }
}
