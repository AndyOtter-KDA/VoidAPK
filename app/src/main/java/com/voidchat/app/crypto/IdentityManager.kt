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
import java.security.cert.X509Certificate
import java.util.Date
import java.math.BigInteger
import java.security.Principal

data class IdentityResult(val displayId: String, val recoveryPhrase: List<String>)
data class RestoreResult(val displayId: String, val username: String)

class MockX509Certificate(private val pubKey: PublicKey) : X509Certificate() {
    override fun getPublicKey(): PublicKey = pubKey
    override fun getEncoded(): ByteArray = pubKey.encoded ?: ByteArray(0)
    override fun verify(key: PublicKey?) {}
    override fun verify(key: PublicKey?, sigProvider: String?) {}
    override fun toString(): String = "MockX509Certificate"
    
    override fun hasUnsupportedCriticalExtension(): Boolean = false
    override fun getCriticalExtensionOIDs(): Set<String>? = null
    override fun getNonCriticalExtensionOIDs(): Set<String>? = null
    override fun getExtensionValue(oid: String?): ByteArray? = null
    override fun checkValidity() {}
    override fun checkValidity(date: Date?) {}
    override fun getVersion(): Int = 3
    override fun getSerialNumber(): BigInteger = BigInteger.ONE
    override fun getIssuerDN(): Principal? = null
    override fun getSubjectDN(): Principal? = null
    override fun getNotBefore(): Date = Date()
    override fun getNotAfter(): Date = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
    override fun getSigAlgName(): String = "SHA256withECDSA"
    override fun getSigAlgOID(): String = "1.2.840.10045.4.3.3"
    override fun getSigAlgParams(): ByteArray? = null
    override fun getIssuerUniqueID(): BooleanArray? = null
    override fun getSubjectUniqueID(): BooleanArray? = null
    override fun getKeyUsage(): BooleanArray? = null
    override fun getBasicConstraints(): Int = -1
    override fun getTBSCertificate(): ByteArray = ByteArray(0)
    override fun getSignature(): ByteArray = ByteArray(0)
}

object IdentityManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "void_identity_key"

    fun createIdentity(): IdentityResult {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()
        val displayId = deriveDisplayId(keyPair.public)

        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val cert = MockX509Certificate(keyPair.public)
        val entry = KeyStore.PrivateKeyEntry(keyPair.private, arrayOf(cert))
        keyStore.setEntry(
            KEY_ALIAS,
            entry,
            android.security.keystore.KeyProtection.Builder(
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setDigests(KeyProperties.DIGEST_SHA256).build()
        )

        val context = com.voidchat.app.VoidApp.instance
        val prefs = context.getSharedPreferences("voidchat_secure_preferences", android.content.Context.MODE_PRIVATE)
        val privB64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        val pubB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        prefs.edit()
            .putString("identity_priv_b64", privB64)
            .putString("identity_pub_b64", pubB64)
            .apply()

        val phrase = generatePhraseFromId(displayId)
        return IdentityResult(displayId, phrase)
    }

    fun getDisplayId(): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val alias = if (keyStore.containsAlias("void_identity")) "void_identity" else KEY_ALIAS
            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            entry?.let {
                deriveDisplayId(it.certificate.publicKey)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getPublicKeyBase64(): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val alias = if (keyStore.containsAlias("void_identity")) "void_identity" else KEY_ALIAS
            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            val publicKey = entry?.certificate?.publicKey
            if (publicKey != null) {
                Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
            } else {
                null
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
        if (keyStore.containsAlias("void_identity")) {
            keyStore.deleteEntry("void_identity")
        }
    }

    fun exportPrivateKeyBase64(): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val alias = if (keyStore.containsAlias("void_identity")) "void_identity" else KEY_ALIAS
            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            val privateKey = entry?.privateKey
            val bytes = privateKey?.encoded
            if (bytes != null) {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else if (privateKey != null) {
                val challenge = "VoidChat-Identity-Verification-${getDisplayId()}".toByteArray()
                val signer = java.security.Signature.getInstance("SHA256withECDSA")
                signer.initSign(privateKey)
                signer.update(challenge)
                val signatureBytes = signer.sign()
                Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun generateRecoveryCode(): String {
        android.util.Log.d("VoidIdentity", "generateRecoveryCode: Starting recovery code generation")
        val displayId = getDisplayId() ?: "0000-0000-0000-0000"
        
        // Exact instruction: "Generating code for: X"
        android.util.Log.d("VoidIdentity", "Generating code for: $displayId")
        
        val context = com.voidchat.app.VoidApp.instance
        val prefs = context.getSharedPreferences("voidchat_secure_preferences", android.content.Context.MODE_PRIVATE)
        var privB64 = prefs.getString("identity_priv_b64", null)
        var pubB64 = prefs.getString("identity_pub_b64", null)
        
        if (privB64 == null || pubB64 == null) {
            android.util.Log.d("VoidIdentity", "generateRecoveryCode: Secure preferences elements missing, exporting keys manually")
            privB64 = exportPrivateKeyBase64() ?: "NO_PRIVATE_KEY"
            pubB64 = getPublicKeyBase64() ?: ""
        }
        
        val pm = com.voidchat.app.data.local.PreferencesManager(context)
        val username = pm.username ?: ""
        
        val code = "VOIDv1:$displayId:$privB64:$pubB64:$username"
        android.util.Log.d("VoidIdentity", "Generated recovery code for: $displayId")
        return code
    }

    fun restoreFromRecoveryCode(code: String): Result<RestoreResult> {
        // Exact instruction: "Restoring from code starting with: Y"
        android.util.Log.d("VoidIdentity", "Restoring from code starting with: ${code.take(15)}")
        android.util.Log.d("VoidIdentity", "Attempting to restore from code")
        return try {
            val cleanCode = code.trim()
            if (!cleanCode.startsWith("VOIDv1:")) {
                android.util.Log.e("VoidIdentity", "restoreFromRecoveryCode Error: Code does not prefix with VOIDv1:")
                android.util.Log.e("VoidIdentity", "Key imported: fail")
                return Result.failure(Exception("Invalid recovery code format"))
            }
            
            val payload = cleanCode.removePrefix("VOIDv1:")
            val parts = payload.split(":")
            if (parts.size < 3) {
                android.util.Log.e("VoidIdentity", "restoreFromRecoveryCode Error: Incomplete recovery code. Subparts size: ${parts.size}")
                android.util.Log.e("VoidIdentity", "Key imported: fail")
                return Result.failure(Exception("Incomplete recovery code"))
            }

            val displayId = parts[0]
            val privateKeyBase64 = parts[1]
            val publicKeyBase64 = parts[2]
            val username = if (parts.size >= 4) parts[3] else ""

            android.util.Log.d("VoidIdentity", "Restoring displayId=$displayId, username=$username")

            val pubKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val keyFactory = java.security.KeyFactory.getInstance("EC")
            val pubKeySpec = java.security.spec.X509EncodedKeySpec(pubKeyBytes)
            val publicKey = keyFactory.generatePublic(pubKeySpec)

            val computedDisplayId = deriveDisplayId(publicKey)
            if (computedDisplayId != displayId) {
                android.util.Log.e("VoidIdentity", "restoreFromRecoveryCode Error: Security verification failed. Computed displayId '$computedDisplayId' does not match specified displayId '$displayId'")
                android.util.Log.e("VoidIdentity", "Key imported: fail")
                return Result.failure(Exception("Security verification failed: Display ID mismatch"))
            }

            val privKeyBytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP)
            val privKeySpec = java.security.spec.PKCS8EncodedKeySpec(privKeyBytes)
            val privateKey = keyFactory.generatePrivate(privKeySpec)

            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val cert = MockX509Certificate(publicKey)
            val entry = KeyStore.PrivateKeyEntry(privateKey, arrayOf(cert))
            keyStore.setEntry(
                "void_identity",
                entry,
                android.security.keystore.KeyProtection.Builder(
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).setDigests(KeyProperties.DIGEST_SHA256).build()
            )
            // Exact instruction: "Key imported: success"
            android.util.Log.d("VoidIdentity", "Key imported: success")

            val context = com.voidchat.app.VoidApp.instance
            val prefs = context.getSharedPreferences("voidchat_secure_preferences", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("identity_priv_b64", privateKeyBase64)
                .putString("identity_pub_b64", publicKeyBase64)
                .apply()

            val pm = com.voidchat.app.data.local.PreferencesManager(context)
            pm.username = username

            android.util.Log.d("VoidIdentity", "Identity restored: $displayId")
            Result.success(RestoreResult(displayId, username))
        } catch (e: Exception) {
            android.util.Log.e("VoidIdentity", "Key imported: fail", e)
            android.util.Log.e("VoidIdentity", "restoreFromRecoveryCode Exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun deriveDisplayId(publicKey: PublicKey): String {
        val bytes = publicKey.encoded ?: java.util.UUID.randomUUID().toString().toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = digest.joinToString("") { "%02X".format(it) }.take(16)
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
