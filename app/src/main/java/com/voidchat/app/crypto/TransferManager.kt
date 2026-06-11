package com.voidchat.app.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import com.voidchat.app.VoidApp
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.local.PreferencesManager
import com.voidchat.app.data.remote.FirestoreManager
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class TransferData(val encryptedData: String, val iv: String)

data class RestoreData(
    val privateKey: String,
    val publicKey: String,
    val username: String,
    val displayId: String,
    val contacts: List<String>
)

object TransferManager {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATION_COUNT = 10000
    private const val KEY_LENGTH = 256
    private val TRANSFER_SALT = "VOID_TRANSFER_CRYPTO_STATIC_SALT_128_BITS".toByteArray(Charsets.UTF_8)

    fun generateTransferCode(): String {
        val code = (100000..999999).random().toString()
        Log.d("VoidTransfer", "Generated code: $code")
        return code
    }

    private fun deriveKey(code: String): SecretKey {
        val spec = PBEKeySpec(code.toCharArray(), TRANSFER_SALT, ITERATION_COUNT, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    suspend fun encryptIdentityForTransfer(code: String): TransferData {
        val context = VoidApp.instance
        val pm = PreferencesManager(context)
        val db = AppDatabase.getDatabase(context)

        // 1. Get identity data from Keystore and PreferencesManager
        val displayId = IdentityManager.getDisplayId() ?: "0000-0000-0000-0000"
        val prefs = context.getSharedPreferences("voidchat_secure_preferences", Context.MODE_PRIVATE)
        var privateKey = prefs.getString("identity_priv_b64", null)
        var publicKey = prefs.getString("identity_pub_b64", null)

        if (privateKey == null || publicKey == null) {
            privateKey = IdentityManager.exportPrivateKeyBase64() ?: ""
            publicKey = IdentityManager.getPublicKeyBase64() ?: ""
        }
        val username = pm.username ?: ""

        // Fetch contacts display IDs
        val contactsList = db.contactDao().getAllContacts().first()
        val contactsIds = contactsList.map { it.displayId }

        // 2. Serialize to JSON: { privateKey, publicKey, username, displayId, contacts }
        val json = JSONObject().apply {
            put("privateKey", privateKey)
            put("publicKey", publicKey)
            put("username", username)
            put("displayId", displayId)
            put("contacts", JSONArray(contactsIds))
        }

        // 3. Derive key from code
        val key = deriveKey(code)

        // 4. Encrypt JSON with AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val encryptedBytes = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val encryptedPayload = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        Log.d("VoidTransfer", "Identity encrypted for transfer")
        return TransferData(encryptedPayload, ivB64)
    }

    fun decryptIdentityFromTransfer(code: String, encryptedPayload: String, iv: String): Result<RestoreData> {
        Log.d("VoidTransfer", "Decrypting transfer with code: $code")
        return try {
            // 1. Derive encryption key from code
            val key = deriveKey(code)

            // 2. Decrypt with AES-256-GCM
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val ivBytes = Base64.decode(iv, Base64.NO_WRAP)
            val spec = GCMParameterSpec(128, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(Base64.decode(encryptedPayload, Base64.NO_WRAP))
            val jsonStr = String(decryptedBytes, Charsets.UTF_8)

            // 3. Parse JSON
            val json = JSONObject(jsonStr)
            val privateKey = json.getString("privateKey")
            val publicKey = json.getString("publicKey")
            val username = json.getString("username")
            val displayId = json.getString("displayId")

            val contactsArr = json.getJSONArray("contacts")
            val contacts = mutableListOf<String>()
            for (i in 0 until contactsArr.length()) {
                contacts.add(contactsArr.getString(i))
            }

            // 4. Return RestoreData
            Log.d("VoidTransfer", "Decrypting transfer completed successfully")
            Result.success(RestoreData(privateKey, publicKey, username, displayId, contacts))
        } catch (e: Exception) {
            Log.e("VoidTransfer", "Decrypting transfer FAILED: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun uploadTransfer(code: String, data: TransferData) {
        val expiresAt = System.currentTimeMillis() + 10 * 60 * 1000L // 10 minutes from now
        FirestoreManager.uploadTransfer(code, data.encryptedData, data.iv, expiresAt)
        Log.d("VoidTransfer", "Transfer uploaded with code: $code")
    }

    fun fetchTransfer(code: String, callback: (TransferData?) -> Unit) {
        FirestoreManager.fetchTransfer(code, callback)
        Log.d("VoidTransfer", "Fetching transfer: $code")
    }

    suspend fun deleteTransfer(code: String) {
        FirestoreManager.deleteTransfer(code)
        Log.d("VoidTransfer", "Transfer deleted: $code")
    }

    suspend fun incrementFailedAttempts(code: String) {
        Log.d("VoidTransfer", "incrementFailedAttempts: Logging failed verification attempt for code ID $code")
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        try {
            val docRef = db.collection("transfers").document(code)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val current = snapshot.getLong("failedAttempts") ?: 0L
                    val next = current + 1
                    if (next >= 5) {
                        transaction.delete(docRef)
                        Log.d("VoidFirestore", "failedAttempts limit (5) reached: Automatically deleted transfer doc for code: $code")
                    } else {
                        transaction.update(docRef, "failedAttempts", next)
                        Log.d("VoidFirestore", "Incremented failedAttempts to $next for code: $code")
                    }
                }
            }.await()
        } catch (e: Exception) {
            Log.e("VoidFirestore", "incrementFailedAttempts FAILED for code: $code: ${e.message}", e)
        }
    }
}
