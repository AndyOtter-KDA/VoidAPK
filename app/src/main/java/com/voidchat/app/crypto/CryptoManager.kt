package com.voidchat.app.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedData(val payload: String, val iv: String)

object CryptoManager {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    fun encrypt(plaintext: String, key: SecretKey): EncryptedData {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedData(
            payload = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(payload: String, iv: String, key: SecretKey): Result<String> {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val ivBytes = Base64.decode(iv, Base64.NO_WRAP)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val decrypted = cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP))
            Result.success(String(decrypted, Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(256)
        return keyGen.generateKey()
    }

    fun secretKeyFromBytes(bytes: ByteArray): SecretKey {
        return SecretKeySpec(bytes, ALGORITHM)
    }
}
