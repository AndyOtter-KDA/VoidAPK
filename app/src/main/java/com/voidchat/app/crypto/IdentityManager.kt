package com.voidchat.app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

data class IdentityResult(val displayId: String, val recoveryPhrase: List<String>)

object IdentityManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "void_identity_key"

    fun createIdentity(): IdentityResult {
        // Generate EC P-256 key pair in Android Keystore
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        
        kpg.initialize(spec)
        val keyPair = kpg.generateKeyPair()
        val displayId = deriveDisplayId(keyPair.public)

        // Generate mock 12-word BIP39 phrase for UI demonstration based on high-entropy ID bytes
        val phrase = generatePhraseFromId(displayId)
        return IdentityResult(displayId, phrase)
    }

    fun getDisplayId(): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            entry?.let {
                deriveDisplayId(it.certificate.publicKey)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun restoreFromPhrase(phrase: List<String>): Result<IdentityResult> {
        return try {
            if (phrase.size != 12) {
                return Result.failure(IllegalArgumentException("Recovery phrase must contain exactly 12 words"))
            }
            // In a production BIP39 flow, we would derive seeds.
            // For this implementation, we reinitialize keying inside Keystore
            val result = createIdentity()
            Result.success(result)
        } catch (e: java.lang.Exception) {
            Result.failure(e)
        }
    }

    fun deleteIdentity() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun deriveDisplayId(publicKey: PublicKey): String {
        val bytes = publicKey.encoded ?: "void_mock_pubkey_seed_bytes".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = digest.joinToString("") { "%02X".format(it) }.take(16)
        // Format XXXX-XXXX-XXXX-XXXX
        return hex.chunked(4).joinToString("-")
    }

    private fun generatePhraseFromId(displayId: String): List<String> {
        val rawHex = displayId.replace("-", "")
        return (0 until 12).map { index ->
            val charIndex = if (index < rawHex.length) rawHex[index].code else index
            BIP39Wordlist.getWord(charIndex * (index + 7))
        }
    }
}
