package com.voidchat.app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object KeyExchangeManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val ALGORITHM_ECDH = "ECDH"

    private fun getAlias(chatId: String) = "void_chat_key_$chatId"

    fun generateChatKeyPair(chatId: String) {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(getAlias(chatId))) {
                android.util.Log.d("VoidKeyExchange", "generateChatKeyPair: Key pair already exists in KeyStore for chatId: $chatId. Reusing existing key pair.")
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("VoidKeyExchange", "generateChatKeyPair: Error inspecting KeyStore; continuing to generate new key", e)
        }

        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            getAlias(chatId),
            KeyProperties.PURPOSE_AGREE_KEY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .build()
        kpg.initialize(spec)
        kpg.generateKeyPair()
    }

    fun getPublicKeyBase64(chatId: String): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val certificate = keyStore.getCertificate(getAlias(chatId)) ?: return null
            Base64.encodeToString(certificate.publicKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun performKeyExchange(chatId: String, otherPublicKeyBase64: String): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val entry = keyStore.getEntry(getAlias(chatId), null) as? KeyStore.PrivateKeyEntry ?: return null
            val privateKey = entry.privateKey

            val keyFactory = KeyFactory.getInstance("EC")
            val pubKeyBytes = Base64.decode(otherPublicKeyBase64, Base64.NO_WRAP)
            val otherPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubKeyBytes))

            val keyAgreement = KeyAgreement.getInstance(ALGORITHM_ECDH)
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(otherPublicKey, true)

            val sharedSecret = keyAgreement.generateSecret()
            // Derive AES secret key using SHA-256 representation of E2E Secret
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val aesKeyBytes = md.digest(sharedSecret)
            SecretKeySpec(aesKeyBytes, "AES")
        } catch (e: Exception) {
            null
        }
    }
}
