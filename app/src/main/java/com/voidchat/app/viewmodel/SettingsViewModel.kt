package com.voidchat.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.local.PreferencesManager
import com.voidchat.app.data.models.AppSettings
import com.voidchat.app.data.remote.FirestoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val prefs = PreferencesManager(application)

    private val _settings = MutableStateFlow(
        AppSettings(
            soundEnabled = prefs.soundEnabled,
            defaultSelfDestruct = prefs.defaultSelfDestruct,
            biometricLock = prefs.biometricLock,
            theme = prefs.theme,
            pinCode = prefs.pinCode
        )
    )
    val settings = _settings.asStateFlow()

    private val _displayId = MutableStateFlow("")
    val displayId = _displayId.asStateFlow()

    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    init {
        viewModelScope.launch {
            val identity = db.identityDao().getIdentity()
            _displayId.value = identity?.displayId ?: "VOID-NODE-NULL"
            _username.value = identity?.username ?: prefs.username ?: "void_operative"
        }
    }

    fun updateUsername(newUsername: String, onComplete: (Boolean, String?) -> Unit) {
        val trimmed = newUsername.trim()
        if (trimmed.isEmpty()) {
            onComplete(false, "Handle cannot be empty.")
            return
        }
        
        // Add format validation: only alphanumeric + underscores, 3 to 20 chars
        val allowedRegex = Regex("^[a-zA-Z0-9_]{3,20}$")
        if (!allowedRegex.matches(trimmed)) {
            onComplete(false, "Format error: Handle must be 3-20 characters long and contain only alphanumeric characters and underscores (A-Z, 0-9, _).")
            return
        }

        viewModelScope.launch {
            try {
                val available = FirestoreManager.checkUsernameAvailability(trimmed)
                if (available) {
                    FirestoreManager.registerUsername(trimmed, _displayId.value)
                    prefs.username = trimmed
                    
                    val currentIdentity = db.identityDao().getIdentity()
                    currentIdentity?.let {
                        db.identityDao().insertIdentity(it.copy(username = trimmed))
                    }
                    _username.value = trimmed
                    onComplete(true, null)
                } else {
                    onComplete(false, "Handle is already taken.")
                }
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: "Unknown transmission failure"
                val friendlyMsg = when {
                    errMsg.contains("PERMISSION_DENIED", ignoreCase = true) -> 
                        "Security handshake rejected (Firestore PERMISSION_DENIED). Check database permissions."
                    errMsg.contains("UNAVAILABLE", ignoreCase = true) -> 
                        "Secure network node unreachable. Check internet connection."
                    else -> 
                        "Registration failed: $errMsg"
                }
                onComplete(false, friendlyMsg)
            }
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.soundEnabled = newSettings.soundEnabled
        prefs.defaultSelfDestruct = newSettings.defaultSelfDestruct
        prefs.biometricLock = newSettings.biometricLock
        prefs.theme = newSettings.theme
        prefs.pinCode = newSettings.pinCode
        _settings.value = newSettings
    }

    fun getDisplayId(): String {
        return _displayId.value
    }

    fun getAppVersion(): String {
        return "v0.0.9-TERMINAL"
    }

    fun checkForUpdate(): Boolean {
        return false // Client running latest Void Terminal Build
    }

    fun deleteIdentity(onComplete: () -> Unit) {
        viewModelScope.launch {
            db.identityDao().deleteIdentity()
            db.messageDao().deleteExpiredMessages()
            prefs.clearAll()
            onComplete()
        }
    }
}
