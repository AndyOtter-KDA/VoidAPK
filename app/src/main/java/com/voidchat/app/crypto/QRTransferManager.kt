package com.voidchat.app.crypto

import android.util.Base64
import org.json.JSONObject

data class TransferPayload(
    val encryptedData: String,
    val ephemeralKeyBase64: String,
    val timestamp: Long
)

object QRTransferManager {
    fun generateTransferPayload(displayId: String, username: String, contacts: List<String>): TransferPayload {
        val aesKey = CryptoManager.generateAESKey()
        val keyB64 = Base64.encodeToString(aesKey.encoded, Base64.NO_WRAP)
        
        val record = JSONObject().apply {
            put("id", displayId)
            put("user", username)
            put("contacts", org.json.JSONArray(contacts))
        }

        val encrypted = CryptoManager.encrypt(record.toString(), aesKey)
        return TransferPayload(
            encryptedData = "${encrypted.payload}:${encrypted.iv}",
            ephemeralKeyBase64 = keyB64,
            timestamp = System.currentTimeMillis()
        )
    }

    fun generateQRContent(payload: TransferPayload): String {
        return "void_transfer://${payload.ephemeralKeyBase64}/${Base64.encodeToString(payload.encryptedData.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)}/${payload.timestamp}"
    }

    fun parseQRContent(content: String): TransferPayload? {
        return try {
            if (!content.startsWith("void_transfer://")) return null
            val raw = content.removePrefix("void_transfer://")
            val parts = raw.split("/")
            if (parts.size >= 3) {
                val ephemeralKey = parts[0]
                val dataEncryptedB64 = parts[1]
                val timestamp = parts[2].toLong()
                
                val decryptedDataStr = String(Base64.decode(dataEncryptedB64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
                TransferPayload(decryptedDataStr, ephemeralKey, timestamp)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun importFromTransfer(payload: TransferPayload): Result<JSONObject> {
        return try {
            val keyBytes = Base64.decode(payload.ephemeralKeyBase64, Base64.NO_WRAP)
            val aesKey = CryptoManager.secretKeyFromBytes(keyBytes)
            val dataParts = payload.encryptedData.split(":")
            if (dataParts.size == 2) {
                val decrypted = CryptoManager.decrypt(dataParts[0], dataParts[1], aesKey)
                decrypted.map { JSONObject(it) }
            } else {
                Result.failure(IllegalArgumentException("Invalid secure transfer payload data bounds"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
