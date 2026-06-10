package com.voidchat.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidchat.app.crypto.CryptoManager
import com.voidchat.app.crypto.IdentityManager
import com.voidchat.app.crypto.KeyExchangeManager
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.models.Message
import com.voidchat.app.data.models.Contact
import com.voidchat.app.data.remote.FirestoreManager
import com.google.firebase.firestore.FirebaseFirestore
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

        viewModelScope.launch {
            try {
                // Determine our profile identity displayId
                val identity = db.identityDao().getIdentity()
                myDisplayId = identity?.displayId ?: IdentityManager.getDisplayId() ?: "UNKNOWN"
                Log.d("VoidChatVM", "initChat profile checked: myDisplayId = $myDisplayId")

                // 1. Generate ECDH key pair
                Log.d("VoidChatVM", "initChat: Generating ECDH key pair for chatId: $chatId")
                KeyExchangeManager.generateChatKeyPair(chatId)

                // 2. Get public key
                val publicKey = KeyExchangeManager.getPublicKeyBase64(chatId)
                if (publicKey != null) {
                    // 3. Upload to Firestore
                    Log.d("VoidChatVM", "initChat: Uploading public key to Firestore for chatId: $chatId")
                    FirestoreManager.uploadPublicKey(chatId, myDisplayId, publicKey)
                } else {
                    Log.e("VoidChatVM", "initChat error: public key base64 was generated null")
                    _chatState.value = ChatState.ERROR("Handshake key compilation failed")
                    return@launch
                }

                // 4. Start listening for key exchange completion
                listenForKeyExchange(chatId)

                // 5. Load messages
                loadMessages(chatId)

            } catch (e: Exception) {
                Log.e("VoidChatVM", "initChat transition failed: ${e.message}", e)
                _chatState.value = ChatState.ERROR("Handshake connection failed: ${e.localizedMessage}")
            }
        }
    }

    fun listenForKeyExchange(chatId: String) {
        Log.d("VoidChatVM", "listenForKeyExchange: start listening to chat doc in Firestore for chatId: $chatId")
        FirebaseFirestore.getInstance().collection("chats").document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("VoidChatVM", "listenForKeyExchange: snapshot listener error: ${error.message}", error)
                    _chatState.value = ChatState.ERROR("Handshake connection failed: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val pubA = snapshot.getString("publicKeyA") ?: ""
                    val pubB = snapshot.getString("publicKeyB") ?: ""
                    Log.d("VoidChatVM", "listenForKeyExchange update: publicKeyA is blank = ${pubA.isEmpty()}, publicKeyB is blank = ${pubB.isEmpty()}")
                    if (pubA.isNotEmpty() && pubB.isNotEmpty()) {
                        try {
                            // Find the other user's public key from the chat doc
                            val participantA = snapshot.getString("participantA") ?: ""
                            val otherPublicKey = if (participantA == myDisplayId) pubB else pubA
                            Log.d("VoidChatVM", "listenForKeyExchange: both keys online. otherPublicKey length: ${otherPublicKey.length}")

                            // Perform ECDH and save shared key
                            val derivedKey = KeyExchangeManager.performKeyExchange(chatId, otherPublicKey)
                            if (derivedKey != null) {
                                sharedKey = derivedKey
                                _chatState.value = ChatState.ENCRYPTED
                                Log.d("VoidChatVM", "listenForKeyExchange: ECDH succeeded. Key established! UI State is ENCRYPTED")
                                viewModelScope.launch {
                                    try {
                                        FirestoreManager.markKeyExchangeComplete(chatId)
                                    } catch (e: Exception) {
                                        Log.e("VoidChatVM", "Failed to mark key exchange complete: ${e.message}")
                                    }
                                }
                                // Decrypt loaded messages now that we have derived the AES session key
                                decryptMessages(_messages.value)
                            } else {
                                Log.e("VoidChatVM", "listenForKeyExchange error: KeyExchangeManager performKeyExchange returned null")
                                _chatState.value = ChatState.ERROR("Deriving secure AES session key failed")
                            }
                        } catch (e: Exception) {
                            Log.e("VoidChatVM", "listenForKeyExchange compilation error: ${e.message}", e)
                            _chatState.value = ChatState.ERROR("Key exchange failed: ${e.localizedMessage}")
                        }
                    } else {
                        Log.d("VoidChatVM", "listenForKeyExchange status: WAITING_FOR_KEY_EXCHANGE")
                        _chatState.value = ChatState.WAITING_FOR_KEY_EXCHANGE
                    }
                } else {
                    Log.d("VoidChatVM", "listenForKeyExchange status: Chat doc empty. WAITING_FOR_KEY_EXCHANGE")
                    _chatState.value = ChatState.WAITING_FOR_KEY_EXCHANGE
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
                        _messages.value = remoteMessages
                        decryptMessages(remoteMessages)
                        markUnreadMessagesAsRead(chatId, remoteMessages)
                    }
            } catch (e: Exception) {
                Log.e("VoidChatVM", "loadMessages failed: ${e.message}", e)
                _chatState.value = ChatState.ERROR("Failed to load messages: ${e.localizedMessage}")
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
                    isRead = false
                )

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
}
