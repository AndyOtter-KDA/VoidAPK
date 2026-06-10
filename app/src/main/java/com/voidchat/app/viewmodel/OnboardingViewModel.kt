package com.voidchat.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidchat.app.crypto.IdentityManager
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.models.LocalIdentity
import com.voidchat.app.data.local.PreferencesManager
import com.voidchat.app.data.remote.FirestoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface OnboardingState {
    object Idle : OnboardingState
    object Creating : OnboardingState
    data class Created(val displayId: String, val phrase: List<String>) : OnboardingState
    object Restoring : OnboardingState
    object Restored : OnboardingState
    data class Error(val message: String) : OnboardingState
}

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val state = _state.asStateFlow()

    private val prefs = PreferencesManager(application)
    private val db = AppDatabase.getDatabase(application)

    fun createIdentity() {
        Log.d("VoidOnboardingVM", "createIdentity: Generating new secure identity in KeyStore...")
        viewModelScope.launch {
            _state.value = OnboardingState.Creating
            try {
                val identity = IdentityManager.createIdentity()
                Log.d("VoidOnboardingVM", "createIdentity success: displayId = ${identity.displayId}")
                _state.value = OnboardingState.Created(identity.displayId, identity.recoveryPhrase)
            } catch (e: Exception) {
                Log.e("VoidOnboardingVM", "createIdentity failed: ${e.message}", e)
                _state.value = OnboardingState.Error("Failed to initiate quantum key generation: ${e.localizedMessage}")
            }
        }
    }

    fun restoreFromPhrase(phraseStr: String) {
        Log.d("VoidOnboardingVM", "restoreFromPhrase: Restoring node identity from recovery phrase...")
        viewModelScope.launch {
            _state.value = OnboardingState.Restoring
            try {
                val words = phraseStr.lowercase().trim().split("\\s+".toRegex())
                if (words.size != 12) {
                    _state.value = OnboardingState.Error("A standard recovery terminal phrase requires exactly 12 words.")
                    return@launch
                }
                
                val result = IdentityManager.restoreFromPhrase(words)
                result.fold(
                    onSuccess = { identity ->
                        Log.d("VoidOnboardingVM", "restoreFromPhrase success: displayId = ${identity.displayId}")
                        _state.value = OnboardingState.Created(identity.displayId, identity.recoveryPhrase)
                    },
                    onFailure = {
                        Log.e("VoidOnboardingVM", "restoreFromPhrase failed: ${it.message}", it)
                        _state.value = OnboardingState.Error("Failure executing recovery handshake.")
                    }
                )
            } catch (e: Exception) {
                Log.e("VoidOnboardingVM", "restoreFromPhrase crashed: ${e.message}", e)
                _state.value = OnboardingState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    fun setUsername(username: String, displayId: String, phrase: List<String>) {
        val trimmed = username.trim()
        Log.d("VoidOnboardingVM", "setUsername: Registering handle: '$trimmed' displayId = $displayId")
        viewModelScope.launch {
            try {
                if (trimmed.isEmpty()) {
                    _state.value = OnboardingState.Error("Ident handle cannot be empty.")
                    return@launch
                }
                
                // Add format validation: only alphanumeric + underscores, 3 to 20 chars
                val allowedRegex = Regex("^[a-zA-Z0-9_]{3,20}$")
                if (!allowedRegex.matches(trimmed)) {
                    _state.value = OnboardingState.Error(
                        "Format error: Handle must be 3-20 characters long and contain only alphanumeric characters and underscores (A-Z, 0-9, _). No spaces allowed."
                    )
                    return@launch
                }
                
                // Real Firestore availability check
                val available = FirestoreManager.checkUsernameAvailability(trimmed)
                if (!available) {
                    _state.value = OnboardingState.Error("Ident handle is taken.")
                    return@launch
                }

                // Real Firestore registration
                FirestoreManager.registerUsername(trimmed, displayId)
                
                // Retrieve the actual security core public key
                val publicKeyBase64 = try {
                    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    val entry = keyStore.getEntry("void_identity_key", null) as? java.security.KeyStore.PrivateKeyEntry
                    val pubKeyBytes = entry?.certificate?.publicKey?.encoded
                    if (pubKeyBytes != null) {
                        android.util.Base64.encodeToString(pubKeyBytes, android.util.Base64.NO_WRAP)
                    } else {
                        "CORE_IDENTITY_KEY_FAIL"
                    }
                } catch (e: Exception) {
                    "CORE_IDENTITY_KEY_FAIL"
                }

                // Write real identity to Room database
                val localIdentity = LocalIdentity(
                    id = UUID.randomUUID().toString(),
                    keyPairAlias = "void_identity_key",
                    publicKeyBase64 = publicKeyBase64,
                    displayId = displayId,
                    username = trimmed,
                    recoveryPhraseHash = phrase.hashCode().toString(),
                    createdAt = System.currentTimeMillis(),
                    deviceName = android.os.Build.MODEL
                )
                
                Log.d("VoidOnboardingVM", "setUsername: Saving validated configuration in room storage")
                db.identityDao().insertIdentity(localIdentity)
                prefs.username = trimmed

                _state.value = OnboardingState.Restored
                Log.d("VoidOnboardingVM", "setUsername complete: state = Restored")
            } catch (e: Exception) {
                Log.e("VoidOnboardingVM", "setUsername failed: ${e.message}", e)
                val errMsg = e.localizedMessage ?: "Unknown transmission failure"
                val friendlyMsg = when {
                    errMsg.contains("PERMISSION_DENIED", ignoreCase = true) -> 
                        "Security handshake rejected (Firestore PERMISSION_DENIED). Check database permissions."
                    errMsg.contains("UNAVAILABLE", ignoreCase = true) -> 
                        "Secure network node unreachable. Check internet connection."
                    else -> 
                        "Register identity handle error: $errMsg"
                }
                _state.value = OnboardingState.Error(friendlyMsg)
            }
        }
    }
}
