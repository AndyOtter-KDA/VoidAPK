package com.voidchat.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidchat.app.crypto.CryptoManager
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.models.GroupChat
import com.voidchat.app.data.models.GroupMessage
import com.voidchat.app.data.models.InviteLink
import com.voidchat.app.data.models.GroupMember
import com.voidchat.app.data.remote.FirestoreManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class GroupChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    private val _messages = MutableStateFlow<List<GroupMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _groupInfo = MutableStateFlow<GroupChat?>(null)
    val groupInfo = _groupInfo.asStateFlow()

    private var currentGroupId: String = ""
    private var myDisplayId: String = ""
    private var myUsername: String = ""

    init {
        viewModelScope.launch {
            val identity = db.identityDao().getIdentity()
            myDisplayId = identity?.displayId ?: "UNKNOWN"
            myUsername = identity?.username ?: "Anonymous"
        }
    }

    fun loadMessages(groupId: String) {
        currentGroupId = groupId
        viewModelScope.launch {
            FirestoreManager.getGroups(myDisplayId).collect { list ->
                _groupInfo.value = list.find { it.groupId == groupId }
            }
        }
        viewModelScope.launch {
            FirestoreManager.getGroupMessages(groupId).collect { groupMsgs ->
                _messages.value = groupMsgs.filter { !it.destroyed }
            }
        }
    }

    fun sendMessage(text: String, selfDestructSeconds: Int) {
        if (currentGroupId.isEmpty() || text.trim().isEmpty()) return
        viewModelScope.launch {
            try {
                val aesKey = CryptoManager.generateAESKey()
                val encrypted = CryptoManager.encrypt(text, aesKey)

                val message = GroupMessage(
                    messageId = "gmsg_${UUID.randomUUID()}",
                    groupId = currentGroupId,
                    senderId = myDisplayId,
                    encryptedPayload = encrypted.payload,
                    iv = encrypted.iv,
                    keyGeneration = _groupInfo.value?.currentGroupKeyGeneration ?: 1,
                    timestamp = System.currentTimeMillis(),
                    selfDestructSeconds = selfDestructSeconds,
                    destroyed = false
                )
                FirestoreManager.sendGroupMessage(currentGroupId, message)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    suspend fun createInviteLink(expiresAt: Long, maxUses: Int): String {
        val linkId = "inv_${UUID.randomUUID().toString().take(6)}"
        val invite = InviteLink(
            linkId = linkId,
            encryptedGroupKey = "ENC_GROUP_KEY_STUB",
            inviteKeyBase64 = "INV_KEY_BASE64_STUB",
            createdBy = myDisplayId,
            createdAt = System.currentTimeMillis(),
            expiresAt = expiresAt,
            maxUses = maxUses,
            currentUses = 0,
            active = true
        )
        return FirestoreManager.createInviteLink(currentGroupId, invite)
    }

    fun joinGroup(inviteCode: String, onComplete: (Boolean) -> Unit) {
        // Parse void://group/{groupId}/{inviteId}
        if (!inviteCode.startsWith("void://group/")) {
            onComplete(false)
            return
        }
        val clean = inviteCode.removePrefix("void://group/")
        val parts = clean.split("/")
        if (parts.size >= 2) {
            val groupId = parts[0]
            viewModelScope.launch {
                val grpMember = GroupMember(
                    displayId = myDisplayId,
                    publicKeyBase64 = "MOCK_EX_PUBKEY_B64",
                    role = "MEMBER",
                    joinedAt = System.currentTimeMillis()
                )
                FirestoreManager.joinGroup(groupId, grpMember)
                
                // Construct and register group locally
                val queryGroup = GroupChat(
                    groupId = groupId,
                    name = "Encrypted Workspace ${groupId.take(4).uppercase()}",
                    createdBy = "Anonymous",
                    createdAt = System.currentTimeMillis(),
                    members = myDisplayId,
                    currentGroupKeyGeneration = 1,
                    defaultSelfDestructSeconds = 0
                )
                FirestoreManager.createGroup(queryGroup)
                
                onComplete(true)
            }
        } else {
            onComplete(false)
        }
    }

    fun leaveGroup() {
        if (currentGroupId.isEmpty()) return
        viewModelScope.launch {
            FirestoreManager.leaveGroup(currentGroupId, myDisplayId)
            _groupInfo.value = null
            _messages.value = emptyList()
            currentGroupId = ""
        }
    }

    fun createGroup(name: String, defaultSelfDestructSeconds: Int, onComplete: (String) -> Unit) {
        val groupId = "grp_${UUID.randomUUID().toString().take(6)}"
        viewModelScope.launch {
            val group = GroupChat(
                groupId = groupId,
                name = name,
                createdBy = myDisplayId,
                createdAt = System.currentTimeMillis(),
                members = myDisplayId,
                currentGroupKeyGeneration = 1,
                defaultSelfDestructSeconds = defaultSelfDestructSeconds
            )
            FirestoreManager.createGroup(group)
            
            // Also add self as member
            val grpMember = GroupMember(
                displayId = myDisplayId,
                publicKeyBase64 = "MOCK_EX_PUBKEY_B64",
                role = "ADMIN",
                joinedAt = System.currentTimeMillis()
            )
            FirestoreManager.joinGroup(groupId, grpMember)
            
            onComplete(groupId)
        }
    }
}
