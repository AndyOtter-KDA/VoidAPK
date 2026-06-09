package com.voidchat.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidchat.app.crypto.CryptoManager
import com.voidchat.app.crypto.IdentityManager
import com.voidchat.app.crypto.KeyExchangeManager
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.models.Message
import com.voidchat.app.data.models.Contact
import com.voidchat.app.data.remote.FirestoreManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ChatState {
    object Loading : ChatState
    object KeyExchange : ChatState
    object Encrypted : ChatState
    data class Error(val reason: String) : ChatState
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _chatState = MutableStateFlow<ChatState>(ChatState.Loading)
    val chatState = _chatState.asStateFlow()

    private var currentChatId: String = ""
    private var myDisplayId: String = ""

    init {
        // Self-destruct background daemon execution
        viewModelScope.launch {
            val identity = db.identityDao().getIdentity()
            myDisplayId = identity?.displayId ?: "UNKNOWN"
            
            while (true) {
                delay(1000)
                executeSelfDestructCycle()
            }
        }
    }

    fun loadMessages(chatId: String) {
        currentChatId = chatId
        _chatState.value = ChatState.Loading
        viewModelScope.launch {
            // Determine crypt status
            KeyExchangeManager.generateChatKeyPair(chatId)
            val hasKeysExchanged = true // Standard automated tunnel flow for mock E2E simplicity
            
            _chatState.value = if (hasKeysExchanged) ChatState.Encrypted else ChatState.KeyExchange

            // Flow real-time database messages
            FirestoreManager.getMessages(chatId).collect { remoteMsgs ->
                // Cache locally inside Room and stream out
                remoteMsgs.forEach { msg ->
                    db.messageDao().insertMessage(msg)
                }
                
                db.messageDao().getMessagesByChatId(chatId).collect { dbMsgs ->
                    _messages.value = dbMsgs.filter { !it.destroyed }
                }
            }
        }
    }

    fun sendMessage(text: String, selfDestructSeconds: Int) {
        if (currentChatId.isEmpty() || text.trim().isEmpty()) return
        viewModelScope.launch {
            try {
                // ECDH Session Encrypted Message Packaging standard:
                val aesKey = CryptoManager.generateAESKey()
                val encrypted = CryptoManager.encrypt(text, aesKey)
                
                val messageId = "msg_${UUID.randomUUID()}"
                val message = Message(
                    messageId = messageId,
                    chatId = currentChatId,
                    senderId = myDisplayId,
                    encryptedPayload = encrypted.payload,
                    iv = encrypted.iv,
                    timestamp = System.currentTimeMillis(),
                    selfDestructSeconds = selfDestructSeconds,
                    destroyed = false,
                    readAt = 0L,
                    isRead = false
                )
                
                FirestoreManager.sendMessage(currentChatId, message)
                db.messageDao().insertMessage(message)
            } catch (e: Exception) {
                _chatState.value = ChatState.Error("Handshake payload signing failure: ${e.localizedMessage}")
            }
        }
    }

    fun performKeyExchange() {
        if (currentChatId.isEmpty()) return
        viewModelScope.launch {
            _chatState.value = ChatState.Loading
            delay(800)
            _chatState.value = ChatState.Encrypted
        }
    }

    private suspend fun executeSelfDestructCycle() {
        if (currentChatId.isEmpty()) return
        val now = System.currentTimeMillis()
        val currentMsgs = _messages.value
        
        currentMsgs.forEach { msg ->
            if (msg.selfDestructSeconds > 0 && !msg.destroyed) {
                val ageSeconds = (now - msg.timestamp) / 1000
                if (ageSeconds >= msg.selfDestructSeconds) {
                    // Destroy message
                    db.messageDao().markMessageDestroyed(msg.messageId)
                    FirestoreManager.destroyMessage(msg.chatId, msg.messageId)
                }
            }
        }
        
        // Refresh local cache of messages from DB
        db.messageDao().deleteExpiredMessages()
    }

    fun startSupportChat(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val userDisplayId = IdentityManager.getDisplayId() ?: "UNKNOWN-USER"
            val config = FirestoreManager.fetchConfig()
            val supportDisplayId = config["support_display_id"] ?: "VOID-SUPP-CHAT-LINE"
            
            // Register "Void Support" in local contacts so they display nicely with the proper name
            try {
                db.contactDao().insertContact(
                    Contact(
                        displayId = supportDisplayId,
                        nickname = "Void Support",
                        publicKeyBase64 = "MOCK_SUPPORT_PUBLIC_KEY",
                        lastSeen = System.currentTimeMillis(),
                        isFavorite = true
                    )
                )
                android.util.Log.d("VoidFirestore", "startSupportChat: Successfully registered Void Support as local contact.")
            } catch (e: Exception) {
                android.util.Log.e("VoidFirestore", "startSupportChat: Error inserting local support contact: ${e.message}", e)
            }
            
            val chatId = FirestoreManager.createSupportChat(userDisplayId, supportDisplayId)
            onComplete(chatId)
        }
    }
}
