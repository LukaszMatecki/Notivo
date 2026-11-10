package com.lumatech.notivo.domain.model

/**
 * Typed domain errors for predictable error mapping in presentation.
 */
sealed class NotivoError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class EncryptionFailed(cause: Throwable? = null) :
        NotivoError("Failed to encrypt note content.", cause)

    class DecryptionFailed(cause: Throwable? = null) :
        NotivoError("Failed to decrypt note content.", cause)

    class ValidationError(message: String) :
        NotivoError(message)

    class NotFoundError(message: String) :
        NotivoError(message)
}
