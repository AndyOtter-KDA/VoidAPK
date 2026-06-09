package com.voidchat.app.crypto

import android.util.Base64
import javax.crypto.spec.SecretKeySpec

data class NoteEncryptionResult(
    val encryptedPayload: String,
    val iv: String,
    val keyBase64: String
)

object NoteCryptoManager {
    fun encryptNote(plaintext: String): NoteEncryptionResult {
        val aesKey = CryptoManager.generateAESKey()
        val encData = CryptoManager.encrypt(plaintext, aesKey)
        val keyBase64 = Base64.encodeToString(aesKey.encoded, Base64.NO_WRAP)
        return NoteEncryptionResult(
            encryptedPayload = encData.payload,
            iv = encData.iv,
            keyBase64 = keyBase64
        )
    }

    fun decryptNote(payload: String, iv: String, keyBase64: String): Result<String> {
        return try {
            val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            CryptoManager.decrypt(payload, iv, secretKey)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateShareCode(noteId: String, keyBase64: String): String {
        val raw = "$noteId:$keyBase64"
        return Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun parseShareCode(code: String): Pair<String, String>? {
        return try {
            val decoded = String(Base64.decode(code, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
            val parts = decoded.split(":", limit = 2)
            if (parts.size == 2) {
                Pair(parts[0], parts[1])
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
