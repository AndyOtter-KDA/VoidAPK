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
            theme = prefs.theme
        )
    )
    val settings = _settings.asStateFlow()

    private val _displayId = MutableStateFlow("")
    val displayId = _displayId.asStateFlow()

    init {
        viewModelScope.launch {
            val identity = db.identityDao().getIdentity()
            _displayId.value = identity?.displayId ?: "VOID-NODE-NULL"
        }
    }

    fun updateUsername(newUsername: String, onComplete: (Boolean) -> Unit) {
        val trimmed = newUsername.trim()
        if (trimmed.isEmpty()) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val available = FirestoreManager.checkUsernameAvailability(trimmed)
            if (available) {
                FirestoreManager.registerUsername(trimmed, _displayId.value)
                prefs.username = trimmed
                
                val currentIdentity = db.identityDao().getIdentity()
                currentIdentity?.let {
                    db.identityDao().insertIdentity(it.copy(username = trimmed))
                }
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.soundEnabled = newSettings.soundEnabled
        prefs.defaultSelfDestruct = newSettings.defaultSelfDestruct
        prefs.biometricLock = newSettings.biometricLock
        prefs.theme = newSettings.theme
        _settings.value = newSettings
    }

    fun getDisplayId(): String {
        return _displayId.value
    }

    fun getAppVersion(): String {
        return "v2.4.9-TERMINAL"
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
