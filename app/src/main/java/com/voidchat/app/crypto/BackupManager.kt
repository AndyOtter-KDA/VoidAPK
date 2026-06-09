package com.voidchat.app.crypto

import android.util.Base64
import com.voidchat.app.data.models.BackupPayload
import com.voidchat.app.data.models.KeyPairExport
import com.voidchat.app.data.models.AppSettings
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import org.json.JSONArray

object BackupManager {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATION_COUNT = 10000
    private const val KEY_LENGTH = 256
    private const val SALT = "VOID_STATIC_SALT_128_BITS" // For simple file backup operations

    private fun deriveKey(password: String): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(
            password.toCharArray(),
            SALT.toByteArray(Charsets.UTF_8),
            ITERATION_COUNT,
            KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val secretBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(secretBytes, "AES")
    }

    fun exportBackup(password: String, includeMessages: Boolean, payload: BackupPayload): ByteArray {
        val secretKey = deriveKey(password)
        
        // Serialize backup into JSON representation for robust transfer
        val json = JSONObject().apply {
            put("version", payload.version)
            put("identity", JSONObject().apply {
                put("privateKeyBase64", payload.identityKeyPair.privateKeyBase64)
                put("publicKeyBase64", payload.identityKeyPair.publicKeyBase64)
                put("displayId", payload.identityKeyPair.displayId)
            })
            put("chatKeys", JSONObject(payload.chatKeys))
            put("contacts", JSONArray(payload.contacts))
            put("settings", JSONObject().apply {
                put("soundEnabled", payload.settings.soundEnabled)
                put("defaultSelfDestruct", payload.settings.defaultSelfDestruct)
                put("biometricLock", payload.settings.biometricLock)
                put("theme", payload.settings.theme)
            })
            if (includeMessages) {
                put("messageHistory", JSONArray(payload.messageHistory))
            } else {
                put("messageHistory", JSONArray())
            }
        }

        val plainBytes = json.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12) { i -> (i + 15).toByte() } // Specific stable GCM parameters for static device files
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        return cipher.doFinal(plainBytes)
    }

    fun importBackup(fileBytes: ByteArray, password: String): Result<BackupPayload> {
        return try {
            val secretKey = deriveKey(password)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12) { i -> (i + 15).toByte() }
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(fileBytes)
            val jsonStr = String(decryptedBytes, Charsets.UTF_8)
            val json = JSONObject(jsonStr)

            val identityObj = json.getJSONObject("identity")
            val identity = KeyPairExport(
                privateKeyBase64 = identityObj.getString("privateKeyBase64"),
                publicKeyBase64 = identityObj.getString("publicKeyBase64"),
                displayId = identityObj.getString("displayId")
            )

            val chatKeysObj = json.getJSONObject("chatKeys")
            val chatKeys = mutableMapOf<String, String>()
            chatKeysObj.keys().forEach { k ->
                chatKeys[k] = chatKeysObj.getString(k)
            }

            val contactsArr = json.getJSONArray("contacts")
            val contacts = mutableListOf<String>()
            for (i in 0 until contactsArr.length()) {
                contacts.add(contactsArr.getString(i))
            }

            val settingsObj = json.getJSONObject("settings")
            val settings = AppSettings(
                soundEnabled = settingsObj.optBoolean("soundEnabled", true),
                defaultSelfDestruct = settingsObj.optInt("defaultSelfDestruct", 0),
                biometricLock = settingsObj.optBoolean("biometricLock", false),
                theme = settingsObj.optString("theme", "DARK")
            )

            val msgHistoryArr = json.getJSONArray("messageHistory")
            val msgHistory = mutableListOf<String>()
            for (i in 0 until msgHistoryArr.length()) {
                msgHistory.add(msgHistoryArr.getString(i))
            }

            val payload = BackupPayload(
                version = json.optInt("version", 1),
                identityKeyPair = identity,
                chatKeys = chatKeys,
                contacts = contacts,
                settings = settings,
                messageHistory = msgHistory
            )
            Result.success(payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun verifyBackupPassword(fileBytes: ByteArray, password: String): Boolean {
        return importBackup(fileBytes, password).isSuccess
    }
}
