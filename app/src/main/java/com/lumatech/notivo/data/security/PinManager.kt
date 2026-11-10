@file:Suppress("DEPRECATION")

package com.lumatech.notivo.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lumatech.notivo.util.Constants
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Outcome of validating a user-entered PIN.
 */
sealed interface PinVerifyResult {
    data object Success : PinVerifyResult
    data class InvalidPin(val remainingAttempts: Int) : PinVerifyResult
    data class LockedOut(val remainingSeconds: Long) : PinVerifyResult
    data object NotConfigured : PinVerifyResult
}

/**
 * Secure storage abstraction for PIN hashes and lockout counters.
 */
interface SecureKeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, defaultValue: Long): Long
    fun putLong(key: String, value: Long)
}

/**
 * EncryptedSharedPreferences-backed implementation of [SecureKeyValueStore].
 */
class AndroidSecureKeyValueStore(context: Context) : SecureKeyValueStore {
    private val prefs: SharedPreferences by lazy {
        val appContext = context.applicationContext
        runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                appContext,
                Constants.ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            // Fallback only if Keystore is unavailable (e.g., certain test environments)
            appContext.getSharedPreferences(Constants.ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }
}

/**
 * Manages salted SHA-256 PIN hashing, constant-time verification, and brute-force lockout.
 */
class PinManager(
    private val store: SecureKeyValueStore,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val secureRandom = SecureRandom()

    fun isPinSet(): Boolean {
        return !store.getString(KEY_PIN_HASH).isNullOrEmpty() &&
            !store.getString(KEY_PIN_SALT).isNullOrEmpty()
    }

    fun setPin(pin: String) {
        require(pin.length >= Constants.PIN_LENGTH && pin.all { it.isDigit() }) {
            "PIN must contain at least ${Constants.PIN_LENGTH} digits."
        }
        val salt = ByteArray(SALT_SIZE_BYTES).apply { secureRandom.nextBytes(this) }
        val hash = deriveHash(pin, salt)
        store.putString(KEY_PIN_SALT, salt.toHex())
        store.putString(KEY_PIN_HASH, hash.toHex())
        resetAttempts()
    }

    fun verifyPin(pin: String): PinVerifyResult {
        if (!isPinSet()) return PinVerifyResult.NotConfigured

        val remainingLockout = getRemainingLockoutSeconds()
        if (remainingLockout > 0L) {
            return PinVerifyResult.LockedOut(remainingLockout)
        }

        val saltEncoded = store.getString(KEY_PIN_SALT) ?: return PinVerifyResult.NotConfigured
        val hashEncoded = store.getString(KEY_PIN_HASH) ?: return PinVerifyResult.NotConfigured

        val salt = saltEncoded.hexToBytes()
        val expectedHash = hashEncoded.hexToBytes()
        val actualHash = deriveHash(pin, salt)

        return if (MessageDigest.isEqual(expectedHash, actualHash)) {
            resetAttempts()
            PinVerifyResult.Success
        } else {
            val failedAttempts = store.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            store.putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
            if (failedAttempts >= Constants.MAX_PIN_ATTEMPTS) {
                val lockoutUntil = timeProvider() + Constants.LOCKOUT_DURATION_MS
                store.putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
                store.putInt(KEY_FAILED_ATTEMPTS, 0)
                PinVerifyResult.LockedOut((Constants.LOCKOUT_DURATION_MS / 1000L).coerceAtLeast(1L))
            } else {
                PinVerifyResult.InvalidPin(Constants.MAX_PIN_ATTEMPTS - failedAttempts)
            }
        }
    }

    fun clearPin() {
        store.putString(KEY_PIN_HASH, null)
        store.putString(KEY_PIN_SALT, null)
        resetAttempts()
    }

    fun getRemainingLockoutSeconds(): Long {
        val lockoutUntil = store.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val now = timeProvider()
        if (lockoutUntil <= now) {
            if (lockoutUntil != 0L) {
                store.putLong(KEY_LOCKOUT_UNTIL, 0L)
            }
            return 0L
        }
        val remainingMillis = lockoutUntil - now
        return ((remainingMillis + 999L) / 1000L).coerceAtLeast(1L)
    }

    private fun resetAttempts() {
        store.putInt(KEY_FAILED_ATTEMPTS, 0)
        store.putLong(KEY_LOCKOUT_UNTIL, 0L)
    }

    private fun deriveHash(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        var current = digest.digest(salt + pin.toByteArray(Charsets.UTF_8))
        // Key stretching with 10,000 SHA-256 rounds works identically across API 24+ and JVM tests
        repeat(HASH_ITERATIONS - 1) {
            digest.reset()
            digest.update(salt)
            current = digest.digest(current)
        }
        return current
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex string length." }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private companion object {
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_LOCKOUT_UNTIL = "lockout_until"
        const val SALT_SIZE_BYTES = 16
        const val HASH_ITERATIONS = 10_000
    }
}
