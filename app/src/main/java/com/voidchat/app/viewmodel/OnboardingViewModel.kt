package com.voidchat.app.viewmodel

import android.app.Application
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
        viewModelScope.launch {
            _state.value = OnboardingState.Creating
            try {
                val identity = IdentityManager.createIdentity()
                _state.value = OnboardingState.Created(identity.displayId, identity.recoveryPhrase)
            } catch (e: Exception) {
                _state.value = OnboardingState.Error("Failed to initiate quantum key generation: ${e.localizedMessage}")
            }
        }
    }

    fun restoreFromPhrase(phraseStr: String) {
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
                        _state.value = OnboardingState.Created(identity.displayId, identity.recoveryPhrase)
                    },
                    onFailure = {
                        _state.value = OnboardingState.Error("Failure executing recovery handshake.")
                    }
                )
            } catch (e: Exception) {
                _state.value = OnboardingState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    fun setUsername(username: String, displayId: String, phrase: List<String>) {
        viewModelScope.launch {
            try {
                val trimmed = username.trim()
                if (trimmed.isEmpty()) {
                    _state.value = OnboardingState.Error("Ident handle cannot be empty.")
                    return@launch
                }
                
                val success = FirestoreManager.checkUsernameAvailability(trimmed)
                if (!success) {
                    _state.value = OnboardingState.Error("Ident handle is taken.")
                    return@launch
                }

                FirestoreManager.registerUsername(trimmed, displayId)
                
                // Write to database
                val localIdentity = com.voidchat.app.data.models.LocalIdentity(
                    id = UUID().toString(),
                    keyPairAlias = "void_identity_key",
                    publicKeyBase64 = "MOCK_BASE64_PUBLIC_KEY",
                    displayId = displayId,
                    username = trimmed,
                    recoveryPhraseHash = phrase.hashCode().toString(),
                    createdAt = System.currentTimeMillis(),
                    deviceName = android.os.Build.MODEL
                )
                
                db.identityDao().insertIdentity(localIdentity)
                prefs.username = trimmed

                _state.value = OnboardingState.Restored
            } catch (e: Exception) {
                _state.value = OnboardingState.Error("Register identity handle error: ${e.localizedMessage}")
            }
        }
    }
}

private fun UUID(): String {
    return java.util.UUID.randomUUID().toString()
}
