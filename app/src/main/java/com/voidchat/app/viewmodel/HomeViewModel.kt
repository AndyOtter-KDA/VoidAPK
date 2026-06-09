package com.voidchat.app.viewmodel

import android.app.Application
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
        viewModelScope.launch {
            val identity = db.identityDao().getIdentity()
            myDisplayId = identity?.displayId ?: "UNKNOWN"
            loadChats()
            loadContacts()
            loadAnnouncement()
        }
    }

    fun loadChats() {
        viewModelScope.launch {
            FirestoreManager.getChats(myDisplayId).collect { list ->
                _chats.value = list.sortedByDescending { it.lastMessageAt }
            }
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            db.contactDao().getAllContacts().collect { list ->
                _contacts.value = list
            }
        }
    }

    private fun loadAnnouncement() {
        FirestoreManager.listenForAnnouncements { title, body ->
            val activeId = "ann-v1"
            if (prefs.dismissedAnnouncementId != activeId) {
                _announcement.value = Announcement(activeId, title, body)
            }
        }
    }

    fun dismissAnnouncement() {
        _announcement.value?.let { ann ->
            prefs.dismissedAnnouncementId = ann.id
            _announcement.value = null
        }
    }

    fun startNewChat(displayId: String, callback: (String) -> Unit) {
        viewModelScope.launch {
            val existingChatId = deriveChatId(myDisplayId, displayId)
            val newChat = Chat(
                chatId = existingChatId,
                participantA = myDisplayId,
                participantB = displayId,
                publicKeyA = "MOCK_KEY_A",
                publicKeyB = "MOCK_KEY_B",
                keyExchangeComplete = false,
                createdAt = System.currentTimeMillis(),
                lastMessageAt = System.currentTimeMillis(),
                backgroundTheme = "DEFAULT"
            )
            FirestoreManager.createChat(newChat)
            
            // Log target displayId as contact
            val parts = displayId.split("-")
            val name = if (parts.isNotEmpty()) "Terminal Node ${parts.last()}" else "Terminal Channel"
            db.contactDao().insertContact(
                Contact(displayId, name, "MOCK_PUBLIC_KEY", System.currentTimeMillis(), false)
            )
            
            callback(existingChatId)
        }
    }

    private fun deriveChatId(idA: String, idB: String): String {
        return if (idA < idB) "${idA}_${idB}" else "${idB}_${idA}"
    }
}
