package com.voidchat.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.models.Chat
import com.voidchat.app.data.models.Contact
import com.voidchat.app.data.local.PreferencesManager
import com.voidchat.app.data.remote.FirestoreManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class Announcement(
    val id: String,
    val title: String,
    val body: String
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val prefs = PreferencesManager(application)

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats = _chats.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts = _contacts.asStateFlow()

    private val _announcement = MutableStateFlow<Announcement?>(null)
    val announcement = _announcement.asStateFlow()

    private var myDisplayId: String = ""

    init {
        Log.d("VoidHomeVM", "Initializing HomeViewModel parameters and channels.")
        viewModelScope.launch {
            try {
                val identity = db.identityDao().getIdentity()
                myDisplayId = identity?.displayId ?: "UNKNOWN"
                Log.d("VoidHomeVM", "Identity confirmed. myDisplayId = $myDisplayId")
                
                loadChats()
                loadContacts()
                loadAnnouncements()
            } catch (e: Exception) {
                Log.e("VoidHomeVM", "Failed to initialize HomeViewModel", e)
            }
        }
    }

    fun loadChats() {
        Log.d("VoidHomeVM", "loadChats: Initiating active channel listen from Firestore listener")
        viewModelScope.launch {
            FirestoreManager.listenForChats(myDisplayId)
                .catch { error ->
                    Log.e("VoidHomeVM", "loadChats: Chat stream listening crash: ${error.message}", error)
                }
                .collect { list ->
                    Log.d("VoidHomeVM", "loadChats: Received ${list.size} live chats from Firestore")
                    _chats.value = list.sortedByDescending { it.lastMessageAt }
                }
        }
    }

    fun loadContacts() {
        Log.d("VoidHomeVM", "loadContacts: Collecting local contact items from Room database")
        viewModelScope.launch {
            db.contactDao().getAllContacts()
                .catch { error ->
                    Log.e("VoidHomeVM", "loadContacts state error: ${error.message}", error)
                }
                .collect { list ->
                    _contacts.value = list
                }
        }
    }

    fun loadAnnouncements() {
        Log.d("VoidHomeVM", "loadAnnouncements: Collecting real-time announcments from Firestore")
        viewModelScope.launch {
            FirestoreManager.listenForAnnouncements()
                .catch { error ->
                    Log.e("VoidHomeVM", "loadAnnouncements: Crash reading Firestore config snapshot: ${error.message}", error)
                }
                .collect { (active, text) ->
                    Log.d("VoidHomeVM", "loadAnnouncements state update: active = $active text = $text")
                    if (active && text.isNotEmpty()) {
                        val activeId = "ann-v1"
                        if (prefs.dismissedAnnouncementId != activeId) {
                            _announcement.value = Announcement(activeId, "QUANTUM BROADCAST WIRE", text)
                        } else {
                            _announcement.value = null
                        }
                    } else {
                        _announcement.value = null
                    }
                }
        }
    }

    fun dismissAnnouncement() {
        _announcement.value?.let { ann ->
            Log.d("VoidHomeVM", "dismissAnnouncement: Dismissing active alert ID: ${ann.id}")
            prefs.dismissedAnnouncementId = ann.id
            _announcement.value = null
        }
    }

    fun startNewChat(displayId: String, callback: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val existingChatId = deriveChatId(myDisplayId, displayId)
                Log.d("VoidHomeVM", "startNewChat: Deriving E2E session container chatId = $existingChatId")
                
                val myPublicKey = try {
                    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    val entry = keyStore.getEntry("void_identity_key", null) as? java.security.KeyStore.PrivateKeyEntry
                    val pubKeyBytes = entry?.certificate?.publicKey?.encoded
                    if (pubKeyBytes != null) {
                        android.util.Base64.encodeToString(pubKeyBytes, android.util.Base64.NO_WRAP)
                    } else {
                        ""
                    }
                } catch (e: Exception) {
                    ""
                }

                val newChat = Chat(
                    chatId = existingChatId,
                    participantA = myDisplayId,
                    participantB = displayId,
                    publicKeyA = myPublicKey,
                    publicKeyB = "",
                    keyExchangeComplete = false,
                    createdAt = System.currentTimeMillis(),
                    lastMessageAt = System.currentTimeMillis(),
                    backgroundTheme = "DEFAULT"
                )
                
                Log.d("VoidHomeVM", "startNewChat: Creating document in Firestore chats collection")
                FirestoreManager.createChat(newChat)
                
                // Add to local contacts list in Room db
                val parts = displayId.split("-")
                val name = if (parts.isNotEmpty()) "Terminal Node ${parts.last()}" else "Terminal Channel"
                db.contactDao().insertContact(
                    Contact(displayId, name, "", System.currentTimeMillis(), false)
                )
                
                callback(existingChatId)
            } catch (e: Exception) {
                Log.e("VoidHomeVM", "startNewChat execution failed: ${e.message}", e)
            }
        }
    }

    private fun deriveChatId(idA: String, idB: String): String {
        return if (idA < idB) "${idA}_${idB}" else "${idB}_${idA}"
    }
}
