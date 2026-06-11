package com.voidchat.app.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class NoteEncryptionResult(
    val encryptedPayload: String,
    val iv: String,
    val keyBase64: String,
    val saltBase64: String?
)

data class ShortNoteCode(
    val code: String,
    val hasPassword: Boolean,
    val fullNoteId: String,
    val fullKeyBase64: String?
)

object NoteCryptoManager {
    private val inMemoryMappings = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    fun storeMapping(shortCode: String, fullNoteId: String, fullKeyBase64: String) {
        inMemoryMappings[shortCode] = Pair(fullNoteId, fullKeyBase64)
        android.util.Log.d("VoidCrypto", "Stored in-memory note mapping: $shortCode -> ($fullNoteId, $fullKeyBase64)")
    }

    fun getMapping(shortCode: String): Pair<String, String>? {
        return inMemoryMappings[shortCode]
    }

    private fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKeySpec {
        val iterationCount = 100000
        val keyLength = 256
        val spec = PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encryptNote(plaintext: String, password: String?): NoteEncryptionResult {
        return if (password.isNullOrEmpty()) {
            val aesKey = CryptoManager.generateAESKey()
            val encData = CryptoManager.encrypt(plaintext, aesKey)
            val keyBase64 = Base64.encodeToString(aesKey.encoded, Base64.NO_WRAP)
            NoteEncryptionResult(
                encryptedPayload = encData.payload,
                iv = encData.iv,
                keyBase64 = keyBase64,
                saltBase64 = null
            )
        } else {
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            val keySpec = deriveKeyFromPassword(password, salt)
            val encData = CryptoManager.encrypt(plaintext, keySpec)
            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            NoteEncryptionResult(
                encryptedPayload = encData.payload,
                iv = encData.iv,
                keyBase64 = "",
                saltBase64 = saltBase64
            )
        }
    }

    fun decryptNote(encryptedPayload: String, iv: String, keyBase64: String?, password: String?, saltBase64: String?): Result<String> {
        return try {
            val secretKey = if (password.isNullOrEmpty()) {
                if (keyBase64.isNullOrEmpty()) {
                    return Result.failure(IllegalArgumentException("Decryption key must be provided if not password locked"))
                }
                val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
                SecretKeySpec(keyBytes, "AES")
            } else {
                if (saltBase64.isNullOrEmpty()) {
                    return Result.failure(IllegalArgumentException("Salt must be provided for password derivation"))
                }
                val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
                deriveKeyFromPassword(password, saltBytes)
            }
            CryptoManager.decrypt(encryptedPayload, iv, secretKey)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateNoteCode(noteId: String, keyBase64: String, hasPassword: Boolean): String {
        return if (!hasPassword) {
            val shortId = noteId.take(5)
            val shortKey = keyBase64.filter { it.isLetterOrDigit() }.take(6)
            "$shortId-$shortKey"
        } else {
            noteId.take(5)
        }
    }
}
