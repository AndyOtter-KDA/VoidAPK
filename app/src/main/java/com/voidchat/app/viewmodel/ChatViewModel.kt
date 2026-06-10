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
import android.util.Log

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

    private val _decryptedMessages = MutableStateFlow<Map<String, String>>(emptyMap())
    val decryptedMessages = _decryptedMessages.asStateFlow()

    private var currentChatId: String = ""
    private var myDisplayId: String = ""
    private var sharedAesKey: javax.crypto.SecretKey? = null

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

    private fun getSharedKeyForSupport(): javax.crypto.SecretKey {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val aesKeyBytes = md.digest("VOID_SUPPORT_TUNNEL_PRESHARED_SECRET".toByteArray(Charsets.UTF_8))
        return javax.crypto.spec.SecretKeySpec(aesKeyBytes, "AES")
    }

    fun loadMessages(chatId: String) {
        currentChatId = chatId
        _chatState.value = ChatState.Loading
        sharedAesKey = null
        _decryptedMessages.value = emptyMap()

        val isSupport = chatId.contains("SUPP") || chatId.contains("support") || chatId.contains("VOID-SUPP-CHAT-LINE")

        viewModelScope.launch {
            Log.d("VoidKeyExchange", "loadMessages: Initiated for chatId: $chatId")
            
            // Ensure Identity is loaded
            val identity = db.identityDao().getIdentity()
            myDisplayId = identity?.displayId ?: IdentityManager.getDisplayId() ?: "UNKNOWN"
            Log.d("VoidKeyExchange", "loadMessages: My displayId: $myDisplayId")

            if (isSupport) {
                sharedAesKey = getSharedKeyForSupport()
                _chatState.value = ChatState.Encrypted
                Log.d("VoidKeyExchange", "loadMessages: Support tunnel detected, initialized with preshared support key.")
            } else {
                // Generate and upload ECDH public key
                try {
                    Log.d("VoidKeyExchange", "loadMessages: Generating chat key pair for $chatId")
                    KeyExchangeManager.generateChatKeyPair(chatId)
                    val publicKey = KeyExchangeManager.getPublicKeyBase64(chatId)
                    if (publicKey != null) {
                        Log.d("VoidKeyExchange", "loadMessages: Generated public key. Uploading to Firestore...")
                        FirestoreManager.uploadPublicKey(chatId, myDisplayId, publicKey)
                    } else {
                        Log.e("VoidKeyExchange", "loadMessages: Public key from KeyExchangeManager was null.")
                    }
                } catch (e: Exception) {
                    Log.e("VoidKeyExchange", "loadMessages: Key generation/upload failure: ${e.message}", e)
                }

                // Listen for key exchange completion
                launch {
                    FirestoreManager.listenForKeyExchange(chatId, myDisplayId).collect { completed ->
                        Log.d("VoidKeyExchange", "loadMessages: listenForKeyExchange update: completed=$completed")
                        if (completed) {
                            try {
                                val chatDoc = FirestoreManager.getChat(chatId)
                                if (chatDoc != null) {
                                    val otherPublicKey = if (chatDoc.participantA == myDisplayId) chatDoc.publicKeyB else chatDoc.publicKeyA
                                    Log.d("VoidKeyExchange", "loadMessages: Resolving keys. otherPublicKey length=${otherPublicKey.length}")
                                    if (otherPublicKey.isNotEmpty()) {
                                        val sharedAes = KeyExchangeManager.performKeyExchange(chatId, otherPublicKey)
                                        if (sharedAes != null) {
                                            sharedAesKey = sharedAes
                                            _chatState.value = ChatState.Encrypted
                                            Log.d("VoidKeyExchange", "loadMessages: Key exchange perform succeeded! State is ENCRYPTED.")
                                            FirestoreManager.markKeyExchangeComplete(chatId)
                                            // Trigger decryption of cached messages
                                            decryptMessages(_messages.value)
                                        } else {
                                            _chatState.value = ChatState.Error("Handshake key compilation failed")
                                            Log.e("VoidKeyExchange", "loadMessages: performKeyExchange returned null")
                                        }
                                    } else {
                                        _chatState.value = ChatState.KeyExchange
                                        Log.w("VoidKeyExchange", "loadMessages: Key exchange status true, but other user key is blank.")
                                    }
                                } else {
                                    _chatState.value = ChatState.KeyExchange
                                    Log.e("VoidKeyExchange", "loadMessages: Failed to fetch chatDoc.")
                                }
                            } catch (e: Exception) {
                                _chatState.value = ChatState.Error("Key exchange failed: ${e.message}")
                                Log.e("VoidKeyExchange", "loadMessages: Key exchange perform exception: ${e.message}", e)
                            }
                        } else {
                            _chatState.value = ChatState.KeyExchange
                            Log.d("VoidKeyExchange", "loadMessages: Key exchange state is waiting.")
                        }
                    }
                }
            }

            // Flow real-time database messages
            launch {
                FirestoreManager.getMessages(chatId).collect { remoteMsgs ->
                    Log.d("VoidKeyExchange", "loadMessages: Flow received ${remoteMsgs.size} messages.")
                    remoteMsgs.forEach { msg ->
                        db.messageDao().insertMessage(msg)
                    }
                }
            }

            // Observe local database messages
            launch {
                db.messageDao().getMessagesByChatId(chatId).collect { dbMsgs ->
                    val freshMsgs = dbMsgs.filter { !it.destroyed }
                    Log.d("VoidKeyExchange", "loadMessages: Emitting ${freshMsgs.size} messages from local DB.")
                    _messages.value = freshMsgs
                    decryptMessages(freshMsgs)
                }
            }
        }
    }

    private fun decryptMessages(msgList: List<Message>) {
        val key = sharedAesKey
        val newDecryptedMap = _decryptedMessages.value.toMutableMap()
        var updated = false
        msgList.forEach { msg ->
            if (msg.destroyed) return@forEach
            if (!newDecryptedMap.containsKey(msg.messageId)) {
                if (key != null) {
                    val result = CryptoManager.decrypt(msg.encryptedPayload, msg.iv, key)
                    if (result.isSuccess) {
                        newDecryptedMap[msg.messageId] = result.getOrNull() ?: ""
                        updated = true
                        Log.d("VoidKeyExchange", "decryptMessages: Successfully decrypted msgId=${msg.messageId}")
                    } else {
                        Log.e("VoidKeyExchange", "decryptMessages: Decrypt failed for msgId=${msg.messageId}", result.exceptionOrNull())
                    }
                }
            }
        }
        if (updated) {
            _decryptedMessages.value = newDecryptedMap
        }
    }

    fun sendMessage(text: String, selfDestructSeconds: Int) {
        if (currentChatId.isEmpty() || text.trim().isEmpty()) return
        val key = sharedAesKey
        if (key == null) {
            Log.d("VoidKeyExchange", "sendMessage: Refusing message transmission - key exchange is not complete yet.")
            android.widget.Toast.makeText(getApplication(), "Waiting for key exchange...", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            try {
                Log.d("VoidKeyExchange", "sendMessage: Encrypting message with shared AES session key.")
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
                
                FirestoreManager.sendMessage(currentChatId, message)
                db.messageDao().insertMessage(message)
                Log.d("VoidKeyExchange", "sendMessage: Successfully dispatched E2E message: $messageId")
            } catch (e: Exception) {
                _chatState.value = ChatState.Error("Handshake payload signing failure: ${e.localizedMessage}")
                Log.e("VoidKeyExchange", "sendMessage: Dispatch error: ${e.message}", e)
            }
        }
    }

    fun performKeyExchange() {
        if (currentChatId.isEmpty()) return
        viewModelScope.launch {
            _chatState.value = ChatState.Loading
            delay(800)
            loadMessages(currentChatId)
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
                    db.messageDao().markMessageDestroyed(msg.messageId)
                    FirestoreManager.destroyMessage(msg.chatId, msg.messageId)
                }
            }
        }
        
        db.messageDao().deleteExpiredMessages()
    }

    fun startSupportChat(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val userDisplayId = IdentityManager.getDisplayId() ?: "UNKNOWN-USER"
            val config = FirestoreManager.fetchConfig()
            val supportDisplayId = config["support_display_id"] ?: "VOID-SUPP-CHAT-LINE"
            
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
