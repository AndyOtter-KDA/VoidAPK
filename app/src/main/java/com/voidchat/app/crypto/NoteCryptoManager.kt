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

data class NoteCode(
    val fullNoteId: String,
    val fullKeyBase64: String,
    val shortCode: String,
    val shortKey: String,
    val displayCode: String
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
        val factory = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        } catch (e: Exception) {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        }
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encryptNote(plaintext: String): NoteEncryptionResult {
        val aesKey = CryptoManager.generateAESKey()
        val encData = CryptoManager.encrypt(plaintext, aesKey)
        val keyBase64 = Base64.encodeToString(aesKey.encoded, Base64.NO_WRAP)
        return NoteEncryptionResult(
            encryptedPayload = encData.payload,
            iv = encData.iv,
            keyBase64 = keyBase64,
            saltBase64 = null
        )
    }

    fun decryptNote(encryptedPayload: String, iv: String, keyBase64: String?): Result<String> {
        return try {
            if (keyBase64.isNullOrEmpty()) {
                return Result.failure(IllegalArgumentException("Decryption key must be provided"))
            }
            val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            CryptoManager.decrypt(encryptedPayload, iv, secretKey)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateNoteCode(fullNoteId: String, fullKeyBase64: String): NoteCode {
        val cleanedNoteId = fullNoteId.replace("-", "")
        val shortCode = cleanedNoteId.take(8)
        val shortKey = fullKeyBase64.take(8)
        val displayCode = "$shortCode-$shortKey"
        android.util.Log.d("VoidNote", "Code: $displayCode")
        return NoteCode(
            fullNoteId = fullNoteId,
            fullKeyBase64 = fullKeyBase64,
            shortCode = shortCode,
            shortKey = shortKey,
            displayCode = displayCode
        )
    }
}
