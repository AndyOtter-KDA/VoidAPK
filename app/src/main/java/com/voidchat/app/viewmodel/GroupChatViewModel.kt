package com.voidchat.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidchat.app.crypto.CryptoManager
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.models.Contact
import com.voidchat.app.data.models.GroupChat
import com.voidchat.app.data.models.GroupMessage
import com.voidchat.app.data.models.InviteLink
import com.voidchat.app.data.models.GroupMember
import com.voidchat.app.data.remote.FirestoreManager
import android.util.Log
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class GroupChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    private val _messages = MutableStateFlow<List<GroupMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _groupInfo = MutableStateFlow<GroupChat?>(null)
    val groupInfo = _groupInfo.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts = _contacts.asStateFlow()

    private var currentGroupId: String = ""
    var myDisplayId: String = ""
        private set
    var myUsername: String = ""
        private set

    init {
        viewModelScope.launch {
            val identity = db.identityDao().getIdentity()
            myDisplayId = identity?.displayId ?: "UNKNOWN"
            myUsername = identity?.username ?: "Anonymous"
        }
        loadLocalContacts()
    }

    fun loadLocalContacts() {
        viewModelScope.launch {
            db.contactDao().getAllContacts().collect { list ->
                _contacts.value = list
            }
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

    fun generateInviteLink(groupId: String): String {
        val linkId = "inv_${UUID.randomUUID().toString().take(6)}"
        viewModelScope.launch {
            try {
                val invite = InviteLink(
                    linkId = linkId,
                    encryptedGroupKey = "ENC_GROUP_KEY_STUB",
                    inviteKeyBase64 = "INV_KEY_BASE64_STUB",
                    createdBy = myDisplayId,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = 0L,
                    maxUses = 100,
                    currentUses = 0,
                    active = true
                )
                FirestoreManager.createInviteLink(groupId, invite)
                Log.d("VoidGroupVM", "generateInviteLink: invite link generated successfully for group: $groupId")
            } catch (e: Exception) {
                Log.e("VoidGroupVM", "generateInviteLink: error registering in Firestore: ${e.message}", e)
            }
        }
        return "void://group/$groupId/$linkId"
    }

    fun joinGroup(inviteCode: String, onComplete: (Boolean) -> Unit) {
        if (!inviteCode.startsWith("void://group/")) {
            onComplete(false)
            return
        }
        val clean = inviteCode.removePrefix("void://group/")
        val parts = clean.split("/")
        if (parts.size >= 2) {
            val groupId = parts[0]
            viewModelScope.launch {
                try {
                    val dbFirestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val groupDoc = dbFirestore.collection("groups").document(groupId).get().await()
                    if (groupDoc.exists()) {
                        val bans = groupDoc.getString("bannedMembers") ?: ""
                        if (bans.split(",").contains(myDisplayId)) {
                            Log.e("VoidGroupVM", "joinGroup: joining rejected. My ID $myDisplayId is in banned list!")
                            onComplete(false)
                            return@launch
                        }
                    }
                    
                    val grpMember = GroupMember(
                        displayId = myDisplayId,
                        publicKeyBase64 = com.voidchat.app.crypto.IdentityManager.getPublicKeyBase64() ?: "",
                        role = "MEMBER",
                        joinedAt = System.currentTimeMillis()
                    )
                    FirestoreManager.joinGroup(groupId, grpMember)
                    onComplete(true)
                } catch (e: Exception) {
                    Log.e("VoidGroupVM", "joinGroup failed: ${e.message}", e)
                    onComplete(false)
                }
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

    fun createGroup(name: String, description: String, defaultSelfDestructSeconds: Int, selectedMemberIds: List<String>, onComplete: (String) -> Unit) {
        val groupId = "grp_${UUID.randomUUID().toString().take(6)}"
        viewModelScope.launch {
            try {
                Log.d("VoidGroupVM", "createGroup: Starting creation for groupID: $groupId, name: $name")
                val identity = db.identityDao().getIdentity()
                val finalMyDisplayId = identity?.displayId ?: myDisplayId.ifEmpty { "UNKNOWN" }
                
                val initialMembers = (selectedMemberIds + finalMyDisplayId).distinct().filter { it.isNotEmpty() }.joinToString(",")
                val group = GroupChat(
                    groupId = groupId,
                    name = name,
                    createdBy = finalMyDisplayId,
                    createdAt = System.currentTimeMillis(),
                    members = initialMembers,
                    currentGroupKeyGeneration = 1,
                    defaultSelfDestructSeconds = defaultSelfDestructSeconds,
                    description = description
                )
                
                Log.d("VoidGroupVM", "createGroup: Writing GroupChat document to Firestore...")
                FirestoreManager.createGroup(group)
                Log.d("VoidGroupVM", "createGroup: Successfully wrote GroupChat document to Firestore.")
                
                // Also add self as member
                val grpMember = GroupMember(
                    displayId = finalMyDisplayId,
                    publicKeyBase64 = com.voidchat.app.crypto.IdentityManager.getPublicKeyBase64() ?: "",
                    role = "ADMIN",
                    joinedAt = System.currentTimeMillis()
                )
                Log.d("VoidGroupVM", "createGroup: Joining creator $finalMyDisplayId to group...")
                FirestoreManager.joinGroup(groupId, grpMember)
                Log.d("VoidGroupVM", "createGroup: Joined creator $finalMyDisplayId to group successfully.")
                
                // Join and add selected members
                for (mbrId in selectedMemberIds) {
                    if (mbrId != finalMyDisplayId && mbrId.isNotEmpty()) {
                        val otherMbr = GroupMember(
                            displayId = mbrId,
                            publicKeyBase64 = android.util.Base64.encodeToString("pubkey_$mbrId".toByteArray(), android.util.Base64.NO_WRAP),
                            role = "MEMBER",
                            joinedAt = System.currentTimeMillis()
                        )
                        Log.d("VoidGroupVM", "createGroup: Joining peer $mbrId to group...")
                        FirestoreManager.joinGroup(groupId, otherMbr)
                        Log.d("VoidGroupVM", "createGroup: Joined peer $mbrId successfully.")
                    }
                }
                
                Log.d("VoidGroupVM", "createGroup: Group $groupId fully provisioned.")
                onComplete(groupId)
            } catch (e: Exception) {
                Log.e("VoidGroupVM", "createGroup: FAILED with exception: ${e.message}", e)
                onComplete(groupId)
            }
        }
    }

    fun renameGroup(newName: String, onComplete: (Boolean) -> Unit) {
        if (currentGroupId.isEmpty() || newName.trim().isEmpty()) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            try {
                val dbFirestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                dbFirestore.collection("groups").document(currentGroupId).update("name", newName.trim()).await()
                Log.d("VoidGroupVM", "renameGroup: successfully updated group name to ${newName.trim()}")
                onComplete(true)
            } catch (e: Exception) {
                Log.e("VoidGroupVM", "renameGroup failed: ${e.message}", e)
                onComplete(false)
            }
        }
    }

    fun updateGroupDescription(newDesc: String, onComplete: (Boolean) -> Unit) {
        if (currentGroupId.isEmpty()) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            try {
                val dbFirestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                dbFirestore.collection("groups").document(currentGroupId).update("description", newDesc.trim()).await()
                Log.d("VoidGroupVM", "updateGroupDescription: successfully updated group description")
                onComplete(true)
            } catch (e: Exception) {
                Log.e("VoidGroupVM", "updateGroupDescription failed: ${e.message}", e)
                onComplete(false)
            }
        }
    }

    fun pinMessage(messageId: String, text: String) {
        if (currentGroupId.isEmpty()) return
        viewModelScope.launch {
            try {
                val dbFirestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                dbFirestore.collection("groups").document(currentGroupId).update(
                    mapOf(
                        "pinnedMessageId" to messageId,
                        "pinnedMessageText" to text
                    )
                ).await()
                Log.d("VoidGroupVM", "pinMessage secure update completed: messageId=$messageId")
            } catch (e: Exception) {
                Log.e("VoidGroupVM", "pinMessage failed: ${e.message}", e)
            }
        }
    }

    fun unpinMessage() {
        if (currentGroupId.isEmpty()) return
        viewModelScope.launch {
            try {
                val dbFirestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                dbFirestore.collection("groups").document(currentGroupId).update(
                    mapOf(
                        "pinnedMessageId" to "",
                        "pinnedMessageText" to ""
                    )
                ).await()
                Log.d("VoidGroupVM", "unpinMessage secure update completed")
            } catch (e: Exception) {
                Log.e("VoidGroupVM", "unpinMessage failed: ${e.message}", e)
            }
        }
    }

    fun deleteGroupMessage(messageId: String) {
        if (currentGroupId.isEmpty()) return
        viewModelScope.launch {
            try {
                val dbFirestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                dbFirestore.collection("groups").document(currentGroupId)
                    .collection("messages").document(messageId)
                    .update("destroyed", true).await()
                Log.d("VoidGroupVM", "deleteGroupMessage: Message $messageId destroyed successfully")
            } catch (e: Exception) {
                Log.e("VoidGroupVM", "deleteGroupMessage failed for $messageId: ${e.message}", e)
            }
        }
    }

    fun kickMember(memberDisplayId: String, onComplete: (Boolean) -> Unit) {
        if (currentGroupId.isEmpty() || memberDisplayId == myDisplayId) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            try {
                FirestoreManager.leaveGroup(currentGroupId, memberDisplayId)
                Log.d("VoidGroupVM", "kickMember: successfully kicked member $memberDisplayId")
                onComplete(true)
            } catch (e: Exception) {
                Log.e("VoidGroupVM", "kickMember failed for $memberDisplayId: ${e.message}", e)
                onComplete(false)
            }
        }
    }

    fun banMember(memberDisplayId: String, onComplete: (Boolean) -> Unit) {
        if (currentGroupId.isEmpty() || memberDisplayId == myDisplayId) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            try {
                FirestoreManager.leaveGroup(currentGroupId, memberDisplayId)
                val dbFirestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val groupDoc = dbFirestore.collection("groups").document(currentGroupId).get().await()
                if (groupDoc.exists()) {
                    val banStr = groupDoc.getString("bannedMembers") ?: ""
                    val updatedBans = if (banStr.isEmpty()) memberDisplayId else "$banStr,$memberDisplayId"
                    dbFirestore.collection("groups").document(currentGroupId).update("bannedMembers", updatedBans).await()
                }
                Log.d("VoidGroupVM", "banMember: successfully banned member $memberDisplayId")
                onComplete(true)
            } catch (e: Exception) {
                Log.e("VoidGroupVM", "banMember failed for $memberDisplayId: ${e.message}", e)
                onComplete(false)
            }
        }
    }

    fun promoteMemberToAdmin(memberDisplayId: String, onComplete: (Boolean) -> Unit) {
        if (currentGroupId.isEmpty()) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            try {
                val dbFirestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                dbFirestore.collection("groups").document(currentGroupId)
                    .collection("members").document(memberDisplayId)
                    .update("role", "ADMIN").await()
                Log.d("VoidGroupVM", "promoteMemberToAdmin: successfully promoted $memberDisplayId")
                onComplete(true)
            } catch (e: Exception) {
                Log.e("VoidGroupVM", "promoteMemberToAdmin failed for $memberDisplayId: ${e.message}", e)
                onComplete(false)
            }
        }
    }

    fun addMemberByDisplayId(memberDisplayId: String, onComplete: (Boolean) -> Unit) {
        if (currentGroupId.isEmpty() || memberDisplayId.isEmpty()) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            try {
                Log.d("VoidFirestore", "addMemberByDisplayId: Starting join process for $memberDisplayId under group $currentGroupId")
                val grpMember = GroupMember(
                    displayId = memberDisplayId,
                    publicKeyBase64 = android.util.Base64.encodeToString("pubkey_$memberDisplayId".toByteArray(), android.util.Base64.NO_WRAP),
                    role = "MEMBER",
                    joinedAt = System.currentTimeMillis()
                )
                FirestoreManager.joinGroup(currentGroupId, grpMember)
                Log.d("VoidFirestore", "addMemberByDisplayId: Successfully added member $memberDisplayId to group $currentGroupId")
                onComplete(true)
            } catch (e: Exception) {
                Log.e("VoidFirestore", "addMemberByDisplayId failed for $memberDisplayId under group $currentGroupId: ${e.message}", e)
                onComplete(false)
            }
        }
    }
}
