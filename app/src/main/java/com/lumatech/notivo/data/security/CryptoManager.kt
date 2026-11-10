package com.lumatech.notivo.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.lumatech.notivo.util.Constants
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Result of encrypting a plaintext string with a unique IV.
 */
data class EncryptedPayload(
    val cipherText: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedPayload
        return cipherText.contentEquals(other.cipherText) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        var result = cipherText.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}

/**
 * Hardware security tier reported for the active Keystore key.
 */
enum class KeySecurityLevel {
    STRONGBOX,
    TEE,
    SOFTWARE
}

/**
 * Abstraction for symmetric AES-256-GCM note encryption so tests can swap the Android Keystore engine.
 */
interface CryptoEngine {
    fun encrypt(plainText: String): EncryptedPayload
    fun decrypt(cipherText: ByteArray, iv: ByteArray): String
    fun getSecurityLevel(): KeySecurityLevel
}

/**
 * Manages hardware-backed AES-256-GCM encryption inside the Android Keystore (StrongBox / TEE).
 */
class CryptoManager(
    private val keyAlias: String = Constants.KEYSTORE_ALIAS
) : CryptoEngine {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    override fun encrypt(plainText: String): EncryptedPayload {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return EncryptedPayload(cipherText = encryptedBytes, iv = cipher.iv)
    }

    override fun decrypt(cipherText: ByteArray, iv: ByteArray): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    override fun getSecurityLevel(): KeySecurityLevel {
        return runCatching {
            val secretKey = getOrCreateSecretKey()
            val factory = SecretKeyFactory.getInstance(secretKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
                    KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> KeySecurityLevel.TEE
                    else -> KeySecurityLevel.SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                if (keyInfo.isInsideSecureHardware) KeySecurityLevel.TEE else KeySecurityLevel.SOFTWARE
            }
        }.getOrDefault(KeySecurityLevel.SOFTWARE)
    }

    /**
     * Provides a Cipher initialized with a biometric-bound key when biometric CryptoObject binding
     * is supported on the device.
     */
    fun createBiometricBoundCipher(): Cipher? {
        return runCatching {
            val key = getOrCreateBiometricBoundKey()
            try {
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.ENCRYPT_MODE, key)
                }
            } catch (_: KeyPermanentlyInvalidatedException) {
                keyStore.deleteEntry(Constants.BIOMETRIC_KEYSTORE_ALIAS)
                val regeneratedKey = getOrCreateBiometricBoundKey()
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.ENCRYPT_MODE, regeneratedKey)
                }
            }
        }.getOrNull()
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val existingEntry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        if (existingEntry != null) {
            return existingEntry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                keyGenerator.init(buildKeyGenSpec(keyAlias, useStrongBox = true, requireAuth = false))
                return keyGenerator.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                // Fall back cleanly to TEE when dedicated StrongBox hardware is absent
            } catch (_: Exception) {
                // Some OEMs throw ProviderException instead of StrongBoxUnavailableException
            }
        }
        keyGenerator.init(buildKeyGenSpec(keyAlias, useStrongBox = false, requireAuth = false))
        return keyGenerator.generateKey()
    }

    @Synchronized
    private fun getOrCreateBiometricBoundKey(): SecretKey {
        val alias = Constants.BIOMETRIC_KEYSTORE_ALIAS
        val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
            return existing.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(buildKeyGenSpec(alias, useStrongBox = false, requireAuth = true))
        return keyGenerator.generateKey()
    }

    private fun buildKeyGenSpec(
        alias: String,
        useStrongBox: Boolean,
        requireAuth: Boolean
    ): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)

        if (requireAuth) {
            builder.setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
        }
        if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
