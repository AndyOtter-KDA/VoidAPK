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
import kotlinx.coroutines.tasks.await

data class Announcement(
    val id: String,
    val title: String,
    val body: String
)

data class ChatUiModel(
    val chat: Chat,
    val otherPartyDisplayId: String,
    val otherPartyUsername: String?,
    val lastMessagePreview: String,
    val timestampStr: String,
    val keyExchangeComplete: Boolean
)

sealed interface UnifiedChatItem {
    val id: String
    val title: String
    val lastMessage: String
    val lastMessageAt: Long
    val timestampStr: String
    val isGroup: Boolean
    val realId: String

    data class Direct(
        val chatUi: ChatUiModel
    ) : UnifiedChatItem {
        override val id = "direct_${chatUi.chat.chatId}"
        override val title = chatUi.otherPartyUsername ?: run {
            val clean = chatUi.otherPartyDisplayId.replace("-", "")
            if (clean.length >= 8) {
                "${clean.take(4)}-${clean.takeLast(4)}"
            } else {
                chatUi.otherPartyDisplayId
            }
        }
        override val lastMessage = chatUi.lastMessagePreview
        override val lastMessageAt = chatUi.chat.lastMessageAt
        override val timestampStr = chatUi.timestampStr
        override val isGroup = false
        override val realId = chatUi.chat.chatId
    }

    data class Group(
        val groupChat: com.voidchat.app.data.models.GroupChat
    ) : UnifiedChatItem {
        override val id = "group_${groupChat.groupId}"
        override val title = groupChat.name
        override val lastMessage = groupChat.description.ifEmpty { "E2E Secure multi-node session" }
        override val lastMessageAt = groupChat.createdAt
        override val timestampStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(groupChat.createdAt))
        override val isGroup = true
        override val realId = groupChat.groupId
    }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val prefs = PreferencesManager(application)

    private val _chats = MutableStateFlow<List<ChatUiModel>>(emptyList())
    val chats = _chats.asStateFlow()

    private val _groupChats = MutableStateFlow<List<com.voidchat.app.data.models.GroupChat>>(emptyList())
    val groupChats = _groupChats.asStateFlow()

    val unifiedChats: StateFlow<List<UnifiedChatItem>> = combine(_chats, _groupChats) { directs, groups ->
        val directItems = directs.map { UnifiedChatItem.Direct(it) }
        val groupItems = groups.map { UnifiedChatItem.Group(it) }
        (directItems + groupItems).sortedByDescending { it.lastMessageAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts = _contacts.asStateFlow()

    private val _announcement = MutableStateFlow<Announcement?>(null)
    val announcement = _announcement.asStateFlow()

    private var myDisplayId: String = ""
    private val usernameCache = mutableMapOf<String, String?>()

    init {
        Log.d("VoidHomeVM", "Initializing HomeViewModel parameters and channels.")
        viewModelScope.launch {
            try {
                val identity = db.identityDao().getIdentity()
                myDisplayId = identity?.displayId ?: "UNKNOWN"
                Log.d("VoidHomeVM", "Identity confirmed. myDisplayId = $myDisplayId")
                
                loadChats()
                loadGroupChats()
                loadContacts()
                loadAnnouncements()
            } catch (e: Exception) {
                Log.e("VoidHomeVM", "Failed to initialize HomeViewModel", e)
            }
        }
    }

    fun loadGroupChats() {
        Log.d("VoidHomeVM", "loadGroupChats: Initiating group chats load from Firestore")
        viewModelScope.launch {
            FirestoreManager.getGroups(myDisplayId)
                .catch { error ->
                    Log.e("VoidHomeVM", "loadGroupChats: error: ${error.message}", error)
                }
                .collect { list ->
                    val myGroups = list.filter { group ->
                        val isMember = group.createdBy == myDisplayId || group.members.split(",").contains(myDisplayId)
                        val isBanned = group.bannedMembers.split(",").contains(myDisplayId)
                        isMember && !isBanned
                    }
                    _groupChats.value = myGroups.sortedByDescending { it.createdAt }
                    Log.d("VoidHomeVM", "loadGroupChats: Found ${myGroups.size} groups for user.")
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
                    processChats(list)
                }
        }
    }

    private fun processChats(rawChats: List<Chat>) {
        viewModelScope.launch {
            val localDeleted = prefs.getLocallyDeletedChats()
            val processed = rawChats.mapNotNull { chat ->
                // Filter out locally deleted chats
                if (localDeleted.contains(chat.chatId)) return@mapNotNull null

                // Filter out deactivated/deleted chats globally
                val dbFs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val chatDoc = try {
                    dbFs.collection("chats").document(chat.chatId).get().await()
                } catch(e: Exception) {
                    null
                }
                val isDeleted = chatDoc?.getBoolean("deleted") ?: false
                if (isDeleted) return@mapNotNull null

                // Filter out if there are messages in the database but all are destroyed.
                // Note: if there are no messages at all yet (fresh chat), we don't filter out.
                val messagesRef = dbFs.collection("chats").document(chat.chatId).collection("messages")
                val totalMsgs = try { messagesRef.get().await().size() } catch(e: Exception) { 0 }
                val hasMsgs = FirestoreManager.hasActiveMessages(chat.chatId)
                if (totalMsgs > 0 && !hasMsgs) {
                    return@mapNotNull null
                }

                // Get other participant display ID
                val otherParty = if (chat.participantA == myDisplayId) chat.participantB else chat.participantA

                // Filter out if blocked
                if (prefs.isUserBlocked(otherParty)) return@mapNotNull null

                // Resolve username
                val cachedUsername = usernameCache[otherParty]
                val username = if (cachedUsername != null) {
                    cachedUsername
                } else {
                    launch {
                        val fetched = FirestoreManager.getUsernameByDisplayId(otherParty)
                        if (fetched != null) {
                            usernameCache[otherParty] = fetched
                            processChats(rawChats)
                        }
                    }
                    null
                }

                // Get last message preview from Room db
                val lastMsg = db.messageDao().getLastMessageForChat(chat.chatId)
                val preview = when {
                    lastMsg == null -> "Encrypted message"
                    lastMsg.decryptedText.isNotEmpty() -> lastMsg.decryptedText
                    else -> "Encrypted message"
                }

                val truncatedPreview = if (preview.length > 40) preview.take(40) + "..." else preview

                // self-destruct check or timestamp
                val timestamp = lastMsg?.timestamp ?: chat.lastMessageAt
                val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(timestamp))
                
                val selfDestructText = if (lastMsg != null && lastMsg.selfDestructSeconds > 0) {
                    "💥 DEP: ${lastMsg.selfDestructSeconds}s"
                } else null

                val timestampStr = if (selfDestructText != null) {
                    "$timeStr [$selfDestructText]"
                } else {
                    timeStr
                }

                ChatUiModel(
                    chat = chat,
                    otherPartyDisplayId = otherParty,
                    otherPartyUsername = username,
                    lastMessagePreview = truncatedPreview,
                    timestampStr = timestampStr,
                    keyExchangeComplete = chat.keyExchangeComplete
                )
            }
            _chats.value = processed.sortedByDescending { it.chat.lastMessageAt }
        }
    }

    fun deleteChatLocally(chatId: String) {
        viewModelScope.launch {
            try {
                db.messageDao().deleteMessagesByChatId(chatId)
                prefs.deleteChatKey(chatId)
                
                val deletedList = prefs.getLocallyDeletedChats().toMutableSet()
                deletedList.add(chatId)
                prefs.saveLocallyDeletedChats(deletedList)

                Log.d("VoidHomeVM", "deleteChatLocally: Finished deleting $chatId on this device")
                loadChats()
            } catch (e: Exception) {
                Log.e("VoidHomeVM", "deleteChatLocally failed: ${e.message}", e)
            }
        }
    }

    fun clearMessages(chatId: String) {
        viewModelScope.launch {
            try {
                db.messageDao().deleteMessagesByChatId(chatId)
                FirestoreManager.markAllMessagesDestroyed(chatId)
                Log.d("VoidHomeVM", "clearMessages: Keep chat intact, marked destroyed on Firestore & Room DB")
                loadChats()
            } catch (e: Exception) {
                Log.e("VoidHomeVM", "clearMessages failed: ${e.message}", e)
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

    fun deleteContact(displayId: String) {
        viewModelScope.launch {
            try {
                db.contactDao().deleteContact(displayId)
                Log.d("VoidHomeVM", "deleteContact: Successfully removed contact $displayId")
                loadContacts()
            } catch (e: Exception) {
                Log.e("VoidHomeVM", "deleteContact failed: ${e.message}", e)
            }
        }
    }

    private fun deriveChatId(idA: String, idB: String): String {
        return if (idA < idB) "${idA}_${idB}" else "${idB}_${idA}"
    }
}
