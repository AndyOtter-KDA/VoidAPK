package com.voidchat.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidchat.app.crypto.CryptoManager
import com.voidchat.app.crypto.IdentityManager
import com.voidchat.app.crypto.KeyExchangeManager
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.local.PreferencesManager
import com.voidchat.app.data.models.Message
import com.voidchat.app.data.models.Contact
import com.voidchat.app.data.remote.FirestoreManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.crypto.SecretKey

sealed interface ChatState {
    object LOADING : ChatState
    object WAITING_FOR_KEY_EXCHANGE : ChatState
    object ENCRYPTED : ChatState
    data class ERROR(val reason: String) : ChatState
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val prefs = PreferencesManager(application)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _chatState = MutableStateFlow<ChatState>(ChatState.LOADING)
    val chatState = _chatState.asStateFlow()

    private val _decryptedMessages = MutableStateFlow<Map<String, String>>(emptyMap())
    val decryptedMessages = _decryptedMessages.asStateFlow()

    private var currentChatId: String = ""
    private var myDisplayId: String = ""
    private var sharedKey: SecretKey? = null

    init {
        Log.d("VoidChatVM", "ChatViewModel loaded. Initializing self-destruct background daemon.")
        viewModelScope.launch {
            val identity = db.identityDao().getIdentity()
            myDisplayId = identity?.displayId ?: "UNKNOWN"
            
            while (true) {
                delay(1000)
                executeSelfDestructCycle()
            }
        }
    }

    fun initChat(chatId: String, otherUserDisplayId: String) {
        Log.d("VoidChatVM", "initChat: chatId = $chatId, otherUserDisplayId = $otherUserDisplayId")
        currentChatId = chatId
        _chatState.value = ChatState.LOADING
        sharedKey = null
        _decryptedMessages.value = emptyMap()

        // 1. Instantly listen to Room database for messages (zero network wait)
        viewModelScope.launch {
            db.messageDao().getMessagesByChatId(chatId)
                .collect { roomMessages ->
                    Log.d("VoidChatVM", "Room DB message collect: size = ${roomMessages.size}")
                    if (roomMessages.isNotEmpty()) {
                        _messages.value = roomMessages
                        val newDecryptedMap = _decryptedMessages.value.toMutableMap()
                        roomMessages.forEach { msg ->
                            if (msg.decryptedText.isNotEmpty()) {
                                newDecryptedMap[msg.messageId] = msg.decryptedText
                            }
                        }
                        _decryptedMessages.value = newDecryptedMap
                        
                        // NEVER show "Waiting for key exchange" if local messages exist
                        _chatState.value = ChatState.ENCRYPTED
                    }
                }
        }

        // 2. Load key from preferences if available
        val savedKeyBase64 = prefs.getChatKey(chatId)
        if (savedKeyBase64 != null) {
            try {
                val keyBytes = android.util.Base64.decode(savedKeyBase64, android.util.Base64.NO_WRAP)
                sharedKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
                _chatState.value = ChatState.ENCRYPTED
                Log.d("VoidChatVM", "initChat: Loaded saved AES session key from preferences. Key established!")
            } catch (e: Exception) {
                Log.e("VoidChatVM", "initChat: Failed to decode saved key: ${e.message}", e)
            }
        }

        // 3. Listen for chat deletion globally to tear down if terminated by other user
        viewModelScope.launch {
            FirestoreManager.listenForChatDeletion(chatId)
                .collect { isDeleted ->
                    if (isDeleted) {
                        Log.d("VoidChatVM", "Chat $chatId has been deleted globally by the participant")
                        db.messageDao().deleteMessagesByChatId(chatId)
                        prefs.deleteChatKey(chatId)
                        _chatState.value = ChatState.ERROR("This secure channel has been terminated.")
                    }
                }
        }

        // 4. Perform E2E handshake / key exchange if key not in prefs
        if (sharedKey == null) {
            viewModelScope.launch {
                try {
                    val identity = db.identityDao().getIdentity()
                    myDisplayId = identity?.displayId ?: IdentityManager.getDisplayId() ?: "UNKNOWN"
                    Log.d("VoidChatVM", "initChat: Generating ECDH key pair for chatId: $chatId")
                    KeyExchangeManager.generateChatKeyPair(chatId)

                    val publicKey = KeyExchangeManager.getPublicKeyBase64(chatId)
                    if (publicKey != null) {
                        Log.d("VoidChatVM", "initChat: Uploading public key to Firestore for chatId: $chatId")
                        FirestoreManager.uploadPublicKey(chatId, myDisplayId, publicKey)
                    } else {
                        Log.e("VoidChatVM", "initChat error: public key base64 was generated null")
                        if (_messages.value.isEmpty()) {
                            _chatState.value = ChatState.ERROR("Handshake key compilation failed")
                        }
                        return@launch
                    }

                    listenForKeyExchange(chatId)
                } catch (e: Exception) {
                    Log.e("VoidChatVM", "initChat transition failed: ${e.message}", e)
                    if (_messages.value.isEmpty()) {
                        _chatState.value = ChatState.ERROR("Handshake connection failed: ${e.localizedMessage}")
                    }
                }
            }
        } else {
            // Key already exists, just monitor for key updates in background if needed
            viewModelScope.launch {
                try {
                    listenForKeyExchange(chatId)
                } catch(e: Exception) {
                    Log.e("VoidChatVM", "listenForKeyExchange background monitor failed: ${e.message}")
                }
            }
        }

        // 5. Subscribe to Firestore updates
        loadMessages(chatId)
    }

    fun listenForKeyExchange(chatId: String) {
        Log.d("VoidChatVM", "listenForKeyExchange: start listening to chat doc in Firestore for chatId: $chatId")
        FirebaseFirestore.getInstance().collection("chats").document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("VoidChatVM", "listenForKeyExchange snapshot error: ${error.message}", error)
                    if (_messages.value.isEmpty()) {
                        _chatState.value = ChatState.ERROR("Handshake connection failed: ${error.localizedMessage}")
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val pubA = snapshot.getString("publicKeyA") ?: ""
                    val pubB = snapshot.getString("publicKeyB") ?: ""
                    Log.d("VoidChatVM", "listenForKeyExchange update: publicKeyA is blank = ${pubA.isEmpty()}, publicKeyB is blank = ${pubB.isEmpty()}")
                    if (pubA.isNotEmpty() && pubB.isNotEmpty()) {
                        try {
                            val participantA = snapshot.getString("participantA") ?: ""
                            val otherPublicKey = if (participantA == myDisplayId) pubB else pubA
                            Log.d("VoidChatVM", "listenForKeyExchange: both keys online. otherPublicKey length: ${otherPublicKey.length}")

                            val derivedKey = KeyExchangeManager.performKeyExchange(chatId, otherPublicKey)
                            if (derivedKey != null) {
                                sharedKey = derivedKey
                                _chatState.value = ChatState.ENCRYPTED
                                Log.d("VoidChatVM", "listenForKeyExchange: ECDH succeeded. Key established! UI State is ENCRYPTED")
                                
                                val derivedKeyBase64 = android.util.Base64.encodeToString(derivedKey.encoded, android.util.Base64.NO_WRAP)
                                prefs.saveChatKey(chatId, derivedKeyBase64)

                                viewModelScope.launch {
                                    try {
                                        FirestoreManager.markKeyExchangeComplete(chatId)
                                    } catch (e: Exception) {
                                        Log.e("VoidChatVM", "Failed to mark key exchange complete: ${e.message}")
                                    }
                                }
                                decryptMessages(_messages.value)
                            } else {
                                Log.e("VoidChatVM", "listenForKeyExchange error: KeyExchangeManager performKeyExchange returned null")
                                if (_messages.value.isEmpty()) {
                                    _chatState.value = ChatState.ERROR("Deriving secure AES session key failed")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("VoidChatVM", "listenForKeyExchange compilation error: ${e.message}", e)
                            if (_messages.value.isEmpty()) {
                                _chatState.value = ChatState.ERROR("Key exchange failed: ${e.localizedMessage}")
                            }
                        }
                    } else {
                        if (_messages.value.isEmpty()) {
                            Log.d("VoidChatVM", "listenForKeyExchange status: WAITING_FOR_KEY_EXCHANGE")
                            _chatState.value = ChatState.WAITING_FOR_KEY_EXCHANGE
                        }
                    }
                } else {
                    if (_messages.value.isEmpty()) {
                        Log.d("VoidChatVM", "listenForKeyExchange status: Chat doc empty. WAITING_FOR_KEY_EXCHANGE")
                        _chatState.value = ChatState.WAITING_FOR_KEY_EXCHANGE
                    }
                }
            }
    }

    fun loadMessages(chatId: String) {
        Log.d("VoidChatVM", "loadMessages: Subscribing to message subcollection updates for chatId: $chatId")
        currentChatId = chatId
        viewModelScope.launch {
            try {
                FirestoreManager.listenForMessages(chatId)
                    .collect { remoteMessages ->
                        Log.d("VoidChatVM", "loadMessages flow collected: size = ${remoteMessages.size}")
                        
                        val localMsgs = db.messageDao().getUndestroyedMessages(chatId).associateBy { it.messageId }
                        val key = sharedKey
                        val decryptedList = remoteMessages.map { msg ->
                            if (msg.decryptedText.isNotEmpty()) {
                                msg
                            } else {
                                val localMsg = localMsgs[msg.messageId]
                                if (localMsg != null && localMsg.decryptedText.isNotEmpty()) {
                                    localMsg
                                } else if (key != null) {
                                    val result = CryptoManager.decrypt(msg.encryptedPayload, msg.iv, key)
                                    if (result.isSuccess) {
                                        val plain = result.getOrNull() ?: ""
                                        val updated = msg.copy(decryptedText = plain)
                                        db.messageDao().insertMessage(updated)
                                        updated
                                    } else {
                                        Log.e("VoidChatVM", "Failed to decrypt message ${msg.messageId}")
                                        msg
                                    }
                                } else {
                                    msg
                                }
                            }
                        }

                        _messages.value = decryptedList
                        val newDecryptedMap = _decryptedMessages.value.toMutableMap()
                        decryptedList.forEach { msg ->
                            if (msg.decryptedText.isNotEmpty()) {
                                newDecryptedMap[msg.messageId] = msg.decryptedText
                            }
                        }
                        _decryptedMessages.value = newDecryptedMap

                        markUnreadMessagesAsRead(chatId, remoteMessages)
                    }
            } catch (e: Exception) {
                Log.e("VoidChatVM", "loadMessages failed: ${e.message}", e)
                if (_messages.value.isEmpty()) {
                    _chatState.value = ChatState.ERROR("Failed to load messages: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun markUnreadMessagesAsRead(chatId: String, remoteMessages: List<Message>) {
        val currentUserId = myDisplayId
        if (currentUserId.isEmpty() || currentUserId == "UNKNOWN") {
            Log.d("VoidChatVM", "markUnreadMessagesAsRead: Deferred. Current user identity displayId is not resolved yet. Resolving...")
            viewModelScope.launch {
                val identity = db.identityDao().getIdentity()
                val resolvedId = identity?.displayId ?: IdentityManager.getDisplayId() ?: "UNKNOWN"
                if (resolvedId != "UNKNOWN") {
                    myDisplayId = resolvedId
                    markUnreadMessagesAsRead(chatId, remoteMessages)
                }
            }
            return
        }
        val unreadReceivedMessages = remoteMessages.filter { msg ->
            msg.senderId != currentUserId && !msg.isRead
        }
        if (unreadReceivedMessages.isNotEmpty()) {
            Log.d("VoidChatVM", "markUnreadMessagesAsRead: Found ${unreadReceivedMessages.size} unread received messages. Launching update on Firestore.")
            viewModelScope.launch {
                unreadReceivedMessages.forEach { msg ->
                    try {
                        FirestoreManager.markMessageAsRead(chatId, msg.messageId)
                    } catch (e: Exception) {
                        Log.e("VoidChatVM", "markUnreadMessagesAsRead: Failed to mark message ${msg.messageId} as read: ${e.message}")
                    }
                }
            }
        }
    }

    private fun decryptMessages(msgList: List<Message>) {
        val key = sharedKey
        if (key == null) {
            Log.d("VoidChatVM", "decryptMessages: Deferred. Shared AES key not established yet.")
            return
        }
        val newDecryptedMap = _decryptedMessages.value.toMutableMap()
        var updated = false
        msgList.forEach { msg ->
            if (msg.destroyed) return@forEach
            if (!newDecryptedMap.containsKey(msg.messageId)) {
                val result = CryptoManager.decrypt(msg.encryptedPayload, msg.iv, key)
                if (result.isSuccess) {
                    newDecryptedMap[msg.messageId] = result.getOrNull() ?: ""
                    updated = true
                    Log.d("VoidChatVM", "decryptMessages: Successfully decrypted message ${msg.messageId}")
                } else {
                    Log.e("VoidChatVM", "decryptMessages: Failed to decrypt message ${msg.messageId}", result.exceptionOrNull())
                }
            }
        }
        if (updated) {
            _decryptedMessages.value = newDecryptedMap
        }
    }

    fun sendMessage(text: String, selfDestructSeconds: Int) {
        if (currentChatId.isEmpty() || text.trim().isEmpty()) return
        val key = sharedKey
        
        if (_chatState.value != ChatState.ENCRYPTED) {
            Log.d("VoidChatVM", "sendMessage: Refused. Current state is: ${_chatState.value}")
            android.widget.Toast.makeText(getApplication(), "Waiting for secure connection", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (key == null) {
            Log.e("VoidChatVM", "sendMessage: Refused. Error: established key is null")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("VoidChatVM", "sendMessage: Encrypting plaintext with AES...")
                val encrypted = CryptoManager.encrypt(text, key)
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
                    isRead = false,
                    decryptedText = text // Cache plaintext locally on write
                )

                // Save to Room DB instantly
                db.messageDao().insertMessage(message)

                Log.d("VoidChatVM", "sendMessage: Dispatching encrypted message to Firestore node")
                FirestoreManager.sendMessage(currentChatId, message)
                Log.d("VoidChatVM", "sendMessage: Dispatch complete for messageId = $messageId")
            } catch (e: Exception) {
                Log.e("VoidChatVM", "sendMessage failed: ${e.message}", e)
                _chatState.value = ChatState.ERROR("Message transmission failed: ${e.localizedMessage}")
            }
        }
    }

    fun performKeyExchange() {
        if (currentChatId.isEmpty()) return
        Log.d("VoidChatVM", "performKeyExchange: Manually re-triggering secure handshake for chatId: $currentChatId")
        viewModelScope.launch {
            _chatState.value = ChatState.LOADING
            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val alias = "void_chat_key_$currentChatId"
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias)
                    Log.d("VoidChatVM", "performKeyExchange: Deleted old KeyStore alias '$alias' to force fresh regeneration")
                }
            } catch (e: Exception) {
                Log.e("VoidChatVM", "performKeyExchange: Failed to delete old KeyStore alias: ${e.message}", e)
            }
            delay(500)
            initChat(currentChatId, "")
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
                    Log.d("VoidChatVM", "executeSelfDestructCycle: Message ${msg.messageId} reached self-destruct limit.")
                    db.messageDao().markMessageDestroyed(msg.messageId)
                    try {
                        FirestoreManager.destroyMessage(msg.chatId, msg.messageId)
                    } catch (e: Exception) {
                        Log.e("VoidChatVM", "executeSelfDestructCycle failed to delete message from Firestore: ${e.message}")
                    }
                }
            }
        }
        db.messageDao().deleteExpiredMessages()
    }

    fun deleteChatLocally(chatId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                db.messageDao().deleteMessagesByChatId(chatId)
                prefs.deleteChatKey(chatId)
                
                val deletedList = prefs.getLocallyDeletedChats().toMutableSet()
                deletedList.add(chatId)
                prefs.saveLocallyDeletedChats(deletedList)
                
                Log.d("VoidChatVM", "deleteChatLocally: chatId=$chatId deleted locally")
                onComplete()
            } catch (e: Exception) {
                Log.e("VoidChatVM", "deleteChatLocally failed: ${e.message}", e)
            }
        }
    }

    fun deleteChatForEveryone(chatId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                FirestoreManager.deleteChatForEveryone(chatId, myDisplayId)
                FirestoreManager.markAllMessagesDestroyed(chatId)
                
                db.messageDao().deleteMessagesByChatId(chatId)
                prefs.deleteChatKey(chatId)
                
                val deletedList = prefs.getLocallyDeletedChats().toMutableSet()
                deletedList.add(chatId)
                prefs.saveLocallyDeletedChats(deletedList)
                
                Log.d("VoidChatVM", "deleteChatForEveryone completed for chatId=$chatId")
                onComplete()
            } catch (e: Exception) {
                Log.e("VoidChatVM", "deleteChatForEveryone failed: ${e.message}", e)
            }
        }
    }

    fun clearMessages(chatId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                db.messageDao().deleteMessagesByChatId(chatId)
                FirestoreManager.markAllMessagesDestroyed(chatId)
                Log.d("VoidChatVM", "clearMessages completed for chatId=$chatId")
                onComplete()
            } catch (e: Exception) {
                Log.e("VoidChatVM", "clearMessages failed: ${e.message}", e)
            }
        }
    }

    fun startSupportChat(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val userDisplayId = IdentityManager.getDisplayId() ?: "UNKNOWN-USER"
            Log.d("VoidChatVM", "startSupportChat: Creating real direct support tunnel for user: $userDisplayId")
            val config = FirestoreManager.fetchConfig()
            val supportDisplayId = config["support_display_id"] ?: "VOID-SUPP-CHAT-LINE"
            
            try {
                db.contactDao().insertContact(
                    Contact(
                        displayId = supportDisplayId,
                        nickname = "Void Support",
                        publicKeyBase64 = "",
                        lastSeen = System.currentTimeMillis(),
                        isFavorite = true
                    )
                )
                Log.d("VoidChatVM", "startSupportChat: Added Support contact locally")
            } catch (e: Exception) {
                Log.e("VoidChatVM", "startSupportChat contact creation error: ${e.message}", e)
            }
            
            val chatId = FirestoreManager.createSupportChat(userDisplayId, supportDisplayId)
            onComplete(chatId)
        }
    }

    fun saveToContacts(chatId: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val dbFirestore = FirebaseFirestore.getInstance()
                val chatDoc = dbFirestore.collection("chats").document(chatId).get().await()
                if (!chatDoc.exists()) {
                    onComplete(false, "Cannot find chat document in Firestore.")
                    return@launch
                }
                val participantA = chatDoc.getString("participantA") ?: ""
                val participantB = chatDoc.getString("participantB") ?: ""
                val otherDisplayId = if (participantA == myDisplayId) participantB else participantA
                if (otherDisplayId.isEmpty()) {
                    onComplete(false, "No direct participant found for this channel.")
                    return@launch
                }
                val handle = FirestoreManager.getUsernameByDisplayId(otherDisplayId) ?: "NODE_${otherDisplayId.take(4).uppercase()}"
                
                val existing = db.contactDao().getContact(otherDisplayId)
                val contact = if (existing != null) {
                    existing.copy(nickname = handle, lastSeen = System.currentTimeMillis())
                } else {
                    Contact(
                        displayId = otherDisplayId,
                        nickname = handle,
                        publicKeyBase64 = chatDoc.getString(if (participantA == myDisplayId) "publicKeyB" else "publicKeyA") ?: "",
                        lastSeen = System.currentTimeMillis(),
                        isFavorite = false
                    )
                }
                db.contactDao().insertContact(contact)
                Log.d("VoidChatVM", "saveToContacts: Successfully saved contact: displayId=$otherDisplayId, handle=$handle")
                onComplete(true, "Saved @$handle next to ID $otherDisplayId to Contacts.")
            } catch (e: Exception) {
                Log.e("VoidChatVM", "saveToContacts error: ${e.message}", e)
                onComplete(false, "Failed to save contact: ${e.localizedMessage}")
            }
        }
    }

    fun blockUser(onComplete: () -> Unit) {
        if (currentChatId.isEmpty()) return
        viewModelScope.launch {
            try {
                val chatDoc = FirebaseFirestore.getInstance().collection("chats").document(currentChatId).get().await()
                if (chatDoc.exists()) {
                    val participantA = chatDoc.getString("participantA") ?: ""
                    val participantB = chatDoc.getString("participantB") ?: ""
                    val otherDisplayId = if (participantA == myDisplayId) participantB else participantA
                    if (otherDisplayId.isNotEmpty()) {
                        prefs.saveBlockedUser(otherDisplayId)
                        Log.d("VoidChatVM", "blockUser: successfully blocked displayId = $otherDisplayId")
                    }
                }
                onComplete()
            } catch (e: Exception) {
                Log.e("VoidChatVM", "blockUser error: ${e.message}", e)
                onComplete()
            }
        }
    }

    fun unblockUser(onComplete: () -> Unit) {
        if (currentChatId.isEmpty()) return
        viewModelScope.launch {
            try {
                val chatDoc = FirebaseFirestore.getInstance().collection("chats").document(currentChatId).get().await()
                if (chatDoc.exists()) {
                    val participantA = chatDoc.getString("participantA") ?: ""
                    val participantB = chatDoc.getString("participantB") ?: ""
                    val otherDisplayId = if (participantA == myDisplayId) participantB else participantA
                    if (otherDisplayId.isNotEmpty()) {
                        prefs.removeBlockedUser(otherDisplayId)
                        Log.d("VoidChatVM", "unblockUser: successfully unblocked displayId = $otherDisplayId")
                    }
                }
                onComplete()
            } catch (e: Exception) {
                Log.e("VoidChatVM", "unblockUser error: ${e.message}", e)
                onComplete()
            }
        }
    }

    fun setChatSelfDestructDefault(seconds: Int) {
        if (currentChatId.isNotEmpty()) {
            prefs.saveChatSelfDestructDefault(currentChatId, seconds)
        }
    }

    fun getChatSelfDestructDefault(): Int {
        return if (currentChatId.isNotEmpty()) {
            prefs.getChatSelfDestructDefault(currentChatId)
        } else {
            0
        }
    }

    fun deleteChatLocally(onComplete: () -> Unit) {
        deleteChatLocally(currentChatId, onComplete)
    }

    fun deleteChatForEveryone(onComplete: () -> Unit) {
        deleteChatForEveryone(currentChatId, onComplete)
    }

    fun clearMessages(onComplete: () -> Unit) {
        clearMessages(currentChatId, onComplete)
    }

    fun getChatInfo(onComplete: (username: String, displayId: String, keyStatus: String, createdAt: Long) -> Unit) {
        if (currentChatId.isEmpty()) return
        viewModelScope.launch {
            try {
                val chatDoc = FirebaseFirestore.getInstance().collection("chats").document(currentChatId).get().await()
                if (chatDoc.exists()) {
                    val participantA = chatDoc.getString("participantA") ?: ""
                    val participantB = chatDoc.getString("participantB") ?: ""
                    val otherDisplayId = if (participantA == myDisplayId) participantB else participantA
                    val otherUsername = FirestoreManager.getUsernameByDisplayId(otherDisplayId) ?: (if (otherDisplayId.length >= 8) "${otherDisplayId.take(4)}-${otherDisplayId.takeLast(4)}" else otherDisplayId)
                    val keyStatus = when (_chatState.value) {
                        is ChatState.ENCRYPTED -> "SECURE (E2E SHIELD ACTIVE)"
                        is ChatState.WAITING_FOR_KEY_EXCHANGE -> "PENDING HANDSHAKE OVER SECURE FIREWALL"
                        else -> "INITIAL STATUS DETECTED"
                    }
                    val createdAt = chatDoc.getLong("createdAt") ?: System.currentTimeMillis()
                    onComplete(otherUsername, otherDisplayId, keyStatus, createdAt)
                } else {
                    onComplete("UNKNOWN", "UNKNOWN-ID", "OFFLINE CHAT SEGMENT", System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Log.e("VoidChatVM", "getChatInfo failed: ${e.message}", e)
                onComplete("ERROR", "UNKNOWN-ID", "DEGRADED NETWORK", System.currentTimeMillis())
            }
        }
    }
}
