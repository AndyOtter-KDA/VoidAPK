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
        val displayId = getDisplayId() ?: "0000-0000-0000-0000"
        val context = com.voidchat.app.VoidApp.instance
        val prefs = context.getSharedPreferences("voidchat_secure_preferences", android.content.Context.MODE_PRIVATE)
        var privB64 = prefs.getString("identity_priv_b64", null)
        var pubB64 = prefs.getString("identity_pub_b64", null)
        
        if (privB64 == null) {
            privB64 = exportPrivateKeyBase64() ?: "NO_PRIVATE_KEY"
            pubB64 = getPublicKeyBase64() ?: ""
        }
        
        val combined = "$privB64:$pubB64"
        val base64PrivateKeyCombined = Base64.encodeToString(combined.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "void-recover-$displayId-$base64PrivateKeyCombined"
    }

    fun restoreFromRecoveryCode(code: String): Result<String> {
        return try {
            if (!code.startsWith("void-recover-")) {
                return Result.failure(IllegalArgumentException("Invalid code prefix"))
            }
            
            val rest = code.removePrefix("void-recover-")
            val lastHyphenIdx = rest.lastIndexOf('-')
            if (lastHyphenIdx == -1) {
                return Result.failure(IllegalArgumentException("Invalid code structure"))
            }
            val displayId = rest.substring(0, lastHyphenIdx)
            val base64PrivateKeyCombined = rest.substring(lastHyphenIdx + 1)
            
            val combinedBytes = Base64.decode(base64PrivateKeyCombined, Base64.NO_WRAP)
            val combinedStr = String(combinedBytes, Charsets.UTF_8)
            val subParts = combinedStr.split(":")
            if (subParts.size < 2) {
                return Result.failure(IllegalArgumentException("Invalid recovery payload structure"))
            }
            val privB64 = subParts[0]
            val pubB64 = subParts[1]
            
            val pubKeyBytes = Base64.decode(pubB64, Base64.NO_WRAP)
            val keyFactory = java.security.KeyFactory.getInstance("EC")
            val pubKeySpec = java.security.spec.X509EncodedKeySpec(pubKeyBytes)
            val publicKey = keyFactory.generatePublic(pubKeySpec)
            val computedDisplayId = deriveDisplayId(publicKey)
            
            if (computedDisplayId != displayId) {
                return Result.failure(IllegalArgumentException("Display ID mismatch"))
            }
            
            val privKeyBytes = Base64.decode(privB64, Base64.NO_WRAP)
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
            
            val context = com.voidchat.app.VoidApp.instance
            val prefs = context.getSharedPreferences("voidchat_secure_preferences", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("identity_priv_b64", privB64)
                .putString("identity_pub_b64", pubB64)
                .apply()
                
            Result.success(displayId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportIdentityToFile(password: String): ByteArray {
        val displayId = getDisplayId() ?: ""
        val context = com.voidchat.app.VoidApp.instance
        val prefs = context.getSharedPreferences("voidchat_secure_preferences", android.content.Context.MODE_PRIVATE)
        val username = prefs.getString("username", "anonymous_node") ?: "anonymous_node"
        
        var privB64 = prefs.getString("identity_priv_b64", null)
        var pubB64 = prefs.getString("identity_pub_b64", null)
        
        if (privB64 == null) {
            privB64 = exportPrivateKeyBase64() ?: ""
            pubB64 = getPublicKeyBase64() ?: ""
        }
        
        val json = org.json.JSONObject().apply {
            put("displayId", displayId)
            put("username", username)
            put("privateKeyBase64", privB64)
            put("publicKeyBase64", pubB64)
        }.toString()
        
        val salt = ByteArray(16).apply { java.security.SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }
        
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 5000, 256)
        val f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = f.generateSecret(spec).encoded
        val keySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val specGcm = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, specGcm)
        val encryptedData = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        
        val result = ByteArray(16 + 12 + encryptedData.size)
        System.arraycopy(salt, 0, result, 0, 16)
        System.arraycopy(iv, 0, result, 16, 12)
        System.arraycopy(encryptedData, 0, result, 28, encryptedData.size)
        return result
    }

    fun importIdentityFromFile(fileBytes: ByteArray, password: String): Result<String> {
        return try {
            if (fileBytes.size < 28) {
                return Result.failure(IllegalArgumentException("Invalid file format"))
            }
            
            val salt = ByteArray(16)
            val iv = ByteArray(12)
            val encryptedLength = fileBytes.size - 28
            val encryptedData = ByteArray(encryptedLength)
            
            System.arraycopy(fileBytes, 0, salt, 0, 16)
            System.arraycopy(fileBytes, 16, iv, 0, 12)
            System.arraycopy(fileBytes, 28, encryptedData, 0, encryptedLength)
            
            val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 5000, 256)
            val f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = f.generateSecret(spec).encoded
            val keySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val specGcm = javax.crypto.spec.GCMParameterSpec(128, iv)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, specGcm)
            val decryptedBytes = cipher.doFinal(encryptedData)
            val jsonStr = String(decryptedBytes, Charsets.UTF_8)
            val json = org.json.JSONObject(jsonStr)
            
            val displayId = json.getString("displayId")
            val username = json.getString("username")
            val privB64 = json.getString("privateKeyBase64")
            val pubB64 = json.getString("publicKeyBase64")
            
            val keyFactory = java.security.KeyFactory.getInstance("EC")
            val pubKeyBytes = Base64.decode(pubB64, Base64.NO_WRAP)
            val pubKeySpec = java.security.spec.X509EncodedKeySpec(pubKeyBytes)
            val publicKey = keyFactory.generatePublic(pubKeySpec)
            
            val privKeyBytes = Base64.decode(privB64, Base64.NO_WRAP)
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
            
            val context = com.voidchat.app.VoidApp.instance
            val prefs = context.getSharedPreferences("voidchat_secure_preferences", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("identity_priv_b64", privB64)
                .putString("identity_pub_b64", pubB64)
                .putString("username", username)
                .apply()
                
            Result.success(displayId)
        } catch (e: Exception) {
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
