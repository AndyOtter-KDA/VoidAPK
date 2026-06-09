package com.voidchat.app.data.remote

import android.util.Log
import com.voidchat.app.data.models.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.android.gms.tasks.Task

// Extension helper to safely await Task results as suspend functions
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: Exception("Firestore task failed"))
        }
    }
}

object FirestoreManager {
    private const val TAG = "VoidFirestore"

    // Default configuration values when Firestore has not yet been populated
    private val defaultConfigs = mapOf(
        "site_url" to "https://voidchat.app",
        "support_display_id" to "VOID-SUPP-CHAT-LINE",
        "announcement_id" to "ann-v1",
        "announcement_title" to "ESTABLISHING QUANTUM NOISE HANDSHAKE...",
        "announcement_body" to "Welcome to the Void. All messages are ephemeral and self-destruct. Establish an E2E tunnel to start secure transmission.",
        "monthly_cost" to "120.0",
        "monthly_donations_fiat" to "36.0",
        "funding_percentage" to "30"
    )

    suspend fun fetchConfig(): Map<String, String> {
        Log.d(TAG, "fetchConfig: Initiating fetch from collection 'config'...")
        val db = FirebaseFirestore.getInstance()
        val result = defaultConfigs.toMutableMap()
        try {
            val snapshot = db.collection("config").get().await()
            Log.d(TAG, "fetchConfig: Successfully queried 'config' collection. Document count: ${snapshot.size()}")
            for (doc in snapshot.documents) {
                Log.d(TAG, "fetchConfig: Found doc ID '${doc.id}' with data: ${doc.data}")
                doc.data?.forEach { (key, value) ->
                    result[key] = value?.toString() ?: ""
                }
            }
            // Also fall back to loading from a unified 'global' document if present
            val globalDoc = db.collection("config").document("global").get().await()
            if (globalDoc.exists()) {
                Log.d(TAG, "fetchConfig: Found dedicated 'global' settings document: ${globalDoc.data}")
                globalDoc.data?.forEach { (key, value) ->
                    result[key] = value?.toString() ?: ""
                }
            }
            Log.d(TAG, "fetchConfig: Completed. Combined keys: ${result.keys}")
        } catch (e: Exception) {
            Log.e(TAG, "fetchConfig: Exception occurred while fetching Firestore config: ${e.message}", e)
        }
        return result
    }

    suspend fun createChat(chat: Chat): String {
        Log.d(TAG, "createChat: Creating chat document in Firestore. chatId = ${chat.chatId}, participantA = ${chat.participantA}, participantB = ${chat.participantB}")
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "chatId" to chat.chatId,
            "participantA" to chat.participantA,
            "participantB" to chat.participantB,
            "publicKeyA" to chat.publicKeyA,
            "publicKeyB" to chat.publicKeyB,
            "keyExchangeComplete" to chat.keyExchangeComplete,
            "createdAt" to chat.createdAt,
            "lastMessageAt" to chat.lastMessageAt,
            "backgroundTheme" to chat.backgroundTheme
        )
        try {
            db.collection("chats").document(chat.chatId).set(data, SetOptions.merge()).await()
            Log.d(TAG, "createChat: Successfully created/merged chat with ID: ${chat.chatId}")
        } catch (e: Exception) {
            Log.e(TAG, "createChat: Failed to write chat to Firestore for ID ${chat.chatId}: ${e.message}", e)
        }
        return chat.chatId
    }

    fun getChats(userDisplayId: String): Flow<List<Chat>> = callbackFlow {
        Log.d(TAG, "getChats: Subscribing to real-time chats snapshot for displayId: $userDisplayId")
        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("chats")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getChats: Snapshot listener error for $userDisplayId: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            Chat(
                                chatId = doc.id,
                                participantA = doc.getString("participantA") ?: "",
                                participantB = doc.getString("participantB") ?: "",
                                publicKeyA = doc.getString("publicKeyA") ?: "",
                                publicKeyB = doc.getString("publicKeyB") ?: "",
                                keyExchangeComplete = doc.getBoolean("keyExchangeComplete") ?: false,
                                createdAt = doc.getLong("createdAt") ?: 0L,
                                lastMessageAt = doc.getLong("lastMessageAt") ?: 0L,
                                backgroundTheme = doc.getString("backgroundTheme") ?: ""
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "getChats: Error parsing chat document ${doc.id}", e)
                            null
                        }
                    }.filter { it.participantA == userDisplayId || it.participantB == userDisplayId }
                    
                    Log.d(TAG, "getChats: Emitting updated chat list. Total matching: ${list.size}")
                    trySend(list)
                }
            }
        awaitClose {
            Log.d(TAG, "getChats: Closing snapshot listener for $userDisplayId")
            listener.remove()
        }
    }

    suspend fun sendMessage(chatId: String, message: Message) {
        val isSupport = chatId.contains("SUPP") || chatId.contains("support") || chatId.contains("VOID-SUPP-CHAT-LINE")
        if (isSupport) {
            sendSupportMessage(chatId, message)
            return
        }
        Log.d(TAG, "sendMessage: Sending message for chatId: $chatId, messageId: ${message.messageId}")
        val db = FirebaseFirestore.getInstance()
        val msgData = hashMapOf(
            "messageId" to message.messageId,
            "chatId" to message.chatId,
            "senderId" to message.senderId,
            "encryptedPayload" to message.encryptedPayload,
            "iv" to message.iv,
            "timestamp" to message.timestamp,
            "selfDestructSeconds" to message.selfDestructSeconds,
            "destroyed" to message.destroyed,
            "readAt" to message.readAt,
            "isRead" to message.isRead
        )
        try {
            db.collection("messages").document(message.messageId).set(msgData, SetOptions.merge()).await()
            Log.d(TAG, "sendMessage: Successfully wrote message ${message.messageId}")
            
            // Touch/update last message timestamp in chats
            db.collection("chats").document(chatId).update("lastMessageAt", message.timestamp).await()
            Log.d(TAG, "sendMessage: Updated lastMessageAt for chatId: $chatId to ${message.timestamp}")
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage: Failed to send message in chat $chatId: ${e.message}", e)
        }
    }

    fun getMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        Log.d(TAG, "getMessages: Subscribing to messages for chatId: $chatId")
        val db = FirebaseFirestore.getInstance()
        val isSupport = chatId.contains("SUPP") || chatId.contains("support") || chatId.contains("VOID-SUPP-CHAT-LINE")
        
        val query = if (isSupport) {
            db.collection("chats").document(chatId).collection("messages")
        } else {
            db.collection("messages").whereEqualTo("chatId", chatId)
        }
        
        val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getMessages: Snapshot listener error for chatId $chatId: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            Message(
                                messageId = doc.id,
                                chatId = doc.getString("chatId") ?: "",
                                senderId = doc.getString("senderId") ?: "",
                                encryptedPayload = doc.getString("encryptedPayload") ?: "",
                                iv = doc.getString("iv") ?: "",
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                selfDestructSeconds = doc.getLong("selfDestructSeconds")?.toInt() ?: 0,
                                destroyed = doc.getBoolean("destroyed") ?: false,
                                readAt = doc.getLong("readAt") ?: 0L,
                                isRead = doc.getBoolean("isRead") ?: false
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "getMessages: Error parsing message document ${doc.id}", e)
                            null
                        }
                    }.filter { !it.destroyed }
                     .sortedBy { it.timestamp }
                    
                    Log.d(TAG, "getMessages: Emitting messages for $chatId. Undestroyed count: ${list.size}")
                    trySend(list)
                }
            }
        awaitClose {
            Log.d(TAG, "getMessages: Closing messages listener for chatId: $chatId")
            listener.remove()
        }
    }

    suspend fun markMessageRead(chatId: String, messageId: String) {
        Log.d(TAG, "markMessageRead: Requesting read update for messageId: $messageId in chatId: $chatId")
        val db = FirebaseFirestore.getInstance()
        val isSupport = chatId.contains("SUPP") || chatId.contains("support") || chatId.contains("VOID-SUPP-CHAT-LINE")
        val docRef = if (isSupport) {
            db.collection("chats").document(chatId).collection("messages").document(messageId)
        } else {
            db.collection("messages").document(messageId)
        }
        try {
            docRef.update(
                "isRead", true,
                "readAt", System.currentTimeMillis()
            ).await()
            Log.d(TAG, "markMessageRead: Successfully marked message $messageId as read")
        } catch (e: Exception) {
            Log.e(TAG, "markMessageRead: Failed to mark message $messageId as read: ${e.message}", e)
        }
    }

    suspend fun destroyMessage(chatId: String, messageId: String) {
        Log.d(TAG, "destroyMessage: Ephemeral trigger: marking message $messageId as destroyed in chatId $chatId")
        val db = FirebaseFirestore.getInstance()
        val isSupport = chatId.contains("SUPP") || chatId.contains("support") || chatId.contains("VOID-SUPP-CHAT-LINE")
        val docRef = if (isSupport) {
            db.collection("chats").document(chatId).collection("messages").document(messageId)
        } else {
            db.collection("messages").document(messageId)
        }
        try {
            docRef.update("destroyed", true).await()
            Log.d(TAG, "destroyMessage: Successfully set destroyed = true on $messageId")
        } catch (e: Exception) {
            Log.e(TAG, "destroyMessage: Failed to destroy message $messageId: ${e.message}", e)
        }
    }

    suspend fun registerUsername(username: String, displayId: String): Result<Boolean> {
        val normalized = username.lowercase().trim()
        Log.d(TAG, "registerUsername: Registering normalized username '$normalized' for displayId: $displayId")
        val db = FirebaseFirestore.getInstance()
        try {
            val doc = db.collection("usernames").document(normalized).get().await()
            if (doc.exists()) {
                Log.w(TAG, "registerUsername: Username '$normalized' is already taken by displayId ${doc.getString("displayId")}")
                return Result.failure(Exception("Username already registered"))
            }
            db.collection("usernames").document(normalized).set(
                hashMapOf(
                    "username" to normalized,
                    "displayId" to displayId,
                    "registeredAt" to System.currentTimeMillis()
                )
            ).await()
            Log.d(TAG, "registerUsername: Registration successful for '$normalized'")
            return Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "registerUsername: Transaction failed for username registration on '$normalized': ${e.message}", e)
            return Result.failure(e)
        }
    }

    suspend fun checkUsernameAvailability(username: String): Boolean {
        val normalized = username.lowercase().trim()
        Log.d(TAG, "checkUsernameAvailability: Verifying availability for username '$normalized'")
        if (normalized.isEmpty()) {
            Log.d(TAG, "checkUsernameAvailability: Username is empty. Returning false.")
            return false
        }
        val db = FirebaseFirestore.getInstance()
        return try {
            val doc = db.collection("usernames").document(normalized).get().await()
            val available = !doc.exists()
            Log.d(TAG, "checkUsernameAvailability: Username '$normalized' availability result: $available")
            available
        } catch (e: Exception) {
            Log.e(TAG, "checkUsernameAvailability: Error checking availability for '$normalized': ${e.message}", e)
            false
        }
    }

    suspend fun getUsernameByDisplayId(displayId: String): String? {
        Log.d(TAG, "getUsernameByDisplayId: Looking up registered username associated with displayId: $displayId")
        val db = FirebaseFirestore.getInstance()
        return try {
            val query = db.collection("usernames")
                .whereEqualTo("displayId", displayId)
                .get()
                .await()
            val username = query.documents.firstOrNull()?.id
            Log.d(TAG, "getUsernameByDisplayId: Resolution result for $displayId: $username")
            username
        } catch (e: Exception) {
            Log.e(TAG, "getUsernameByDisplayId: Error during username lookup: ${e.message}", e)
            null
        }
    }

    suspend fun createGroup(group: GroupChat): String {
        Log.d(TAG, "createGroup: Writing new group chat to Firestore. Name = '${group.name}', ID = ${group.groupId}")
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "name" to group.name,
            "createdBy" to group.createdBy,
            "createdAt" to group.createdAt,
            "members" to group.members,
            "currentGroupKeyGeneration" to group.currentGroupKeyGeneration,
            "defaultSelfDestructSeconds" to group.defaultSelfDestructSeconds
        )
        try {
            db.collection("groups").document(group.groupId).set(data, SetOptions.merge()).await()
            Log.d(TAG, "createGroup: Successfully wrote group: ${group.groupId}")
        } catch (e: Exception) {
            Log.e(TAG, "createGroup: Failed to create group chat: ${e.message}", e)
        }
        return group.groupId
    }

    fun getGroups(userDisplayId: String): Flow<List<GroupChat>> = callbackFlow {
        Log.d(TAG, "getGroups: Real-time subscription to group chats for displayId: $userDisplayId")
        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("groups")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getGroups: Snapshot error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            GroupChat(
                                groupId = doc.id,
                                name = doc.getString("name") ?: "",
                                createdBy = doc.getString("createdBy") ?: "",
                                createdAt = doc.getLong("createdAt") ?: 0L,
                                members = doc.getString("members") ?: "",
                                currentGroupKeyGeneration = doc.getLong("currentGroupKeyGeneration")?.toInt() ?: 1,
                                defaultSelfDestructSeconds = doc.getLong("defaultSelfDestructSeconds")?.toInt() ?: 0
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "getGroups: Error parsing group document ${doc.id}", e)
                            null
                        }
                    }.filter { grp ->
                        grp.members.split(",").map { it.trim() }.contains(userDisplayId)
                    }
                    Log.d(TAG, "getGroups: Emitting matching group chats: ${list.size}")
                    trySend(list)
                }
            }
        awaitClose {
            Log.d(TAG, "getGroups: Closing real-time subscription for group chats")
            listener.remove()
        }
    }

    suspend fun sendGroupMessage(groupId: String, message: GroupMessage) {
        Log.d(TAG, "sendGroupMessage: Transmitting group message. groupId = $groupId, messageId = ${message.messageId}")
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "messageId" to message.messageId,
            "groupId" to message.groupId,
            "senderId" to message.senderId,
            "encryptedPayload" to message.encryptedPayload,
            "iv" to message.iv,
            "keyGeneration" to message.keyGeneration,
            "timestamp" to message.timestamp,
            "selfDestructSeconds" to message.selfDestructSeconds,
            "destroyed" to message.destroyed
        )
        try {
            db.collection("group_messages").document(message.messageId).set(data, SetOptions.merge()).await()
            Log.d(TAG, "sendGroupMessage: Successfully sent group message ${message.messageId}")
        } catch (e: Exception) {
            Log.e(TAG, "sendGroupMessage: Failed to send group message: ${e.message}", e)
        }
    }

    fun getGroupMessages(groupId: String): Flow<List<GroupMessage>> = callbackFlow {
        Log.d(TAG, "getGroupMessages: Real-time group messages subscription for groupId: $groupId")
        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("group_messages")
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getGroupMessages: Snapshot listener failed for groupId $groupId: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            GroupMessage(
                                messageId = doc.id,
                                groupId = doc.getString("groupId") ?: "",
                                senderId = doc.getString("senderId") ?: "",
                                encryptedPayload = doc.getString("encryptedPayload") ?: "",
                                iv = doc.getString("iv") ?: "",
                                keyGeneration = doc.getLong("keyGeneration")?.toInt() ?: 1,
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                selfDestructSeconds = doc.getLong("selfDestructSeconds")?.toInt() ?: 0,
                                destroyed = doc.getBoolean("destroyed") ?: false
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "getGroupMessages: Error parsing group message ${doc.id}", e)
                            null
                        }
                    }.filter { !it.destroyed }
                     .sortedBy { it.timestamp }
                    Log.d(TAG, "getGroupMessages: Emitting of list for group $groupId, size: ${list.size}")
                    trySend(list)
                }
            }
        awaitClose {
            Log.d(TAG, "getGroupMessages: Unsubscribing message listener for groupId: $groupId")
            listener.remove()
        }
    }

    suspend fun createInviteLink(groupId: String, invite: InviteLink): String {
        Log.d(TAG, "createInviteLink: Creating linkId: ${invite.linkId} for groupId: $groupId")
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "linkId" to invite.linkId,
            "groupId" to groupId,
            "encryptedGroupKey" to invite.encryptedGroupKey,
            "inviteKeyBase64" to invite.inviteKeyBase64,
            "createdBy" to invite.createdBy,
            "createdAt" to invite.createdAt,
            "expiresAt" to invite.expiresAt,
            "maxUses" to invite.maxUses,
            "currentUses" to invite.currentUses,
            "active" to invite.active
        )
        try {
            db.collection("invite_links").document(invite.linkId).set(data, SetOptions.merge()).await()
            Log.d(TAG, "createInviteLink: Successfully wrote link: ${invite.linkId}")
        } catch (e: Exception) {
            Log.e(TAG, "createInviteLink: Failed to create invite link: ${e.message}", e)
        }
        return "void://group/${groupId}/${invite.linkId}"
    }

    fun getInviteLinks(groupId: String): Flow<List<InviteLink>> = callbackFlow {
        Log.d(TAG, "getInviteLinks: Registering invite link snapshots for group: $groupId")
        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("invite_links")
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getInviteLinks: Error on snapshots for group $groupId: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            InviteLink(
                                linkId = doc.id,
                                encryptedGroupKey = doc.getString("encryptedGroupKey") ?: "",
                                inviteKeyBase64 = doc.getString("inviteKeyBase64") ?: "",
                                createdBy = doc.getString("createdBy") ?: "",
                                createdAt = doc.getLong("createdAt") ?: 0L,
                                expiresAt = doc.getLong("expiresAt") ?: 0L,
                                maxUses = doc.getLong("maxUses")?.toInt() ?: 0,
                                currentUses = doc.getLong("currentUses")?.toInt() ?: 0,
                                active = doc.getBoolean("active") ?: true
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "getInviteLinks: Parsing error on ${doc.id}", e)
                            null
                        }
                    }
                    Log.d(TAG, "getInviteLinks: Emitting invite links count: ${list.size}")
                    trySend(list)
                }
            }
        awaitClose {
            Log.d(TAG, "getInviteLinks: Unsubscribing invite links listener for group: $groupId")
            listener.remove()
        }
    }

    suspend fun joinGroup(groupId: String, member: GroupMember) {
        Log.d(TAG, "joinGroup: Joining group: $groupId with member: ${member.displayId}")
        val db = FirebaseFirestore.getInstance()
        try {
            val doc = db.collection("groups").document(groupId).get().await()
            if (doc.exists()) {
                val currentMembers = doc.getString("members") ?: ""
                val membersList = currentMembers.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                if (!membersList.contains(member.displayId)) {
                    membersList.add(member.displayId)
                    val updatedCSV = membersList.joinToString(",")
                    db.collection("groups").document(groupId).update("members", updatedCSV).await()
                    Log.d(TAG, "joinGroup: Successfully updated group members list in Firestore: $updatedCSV")
                } else {
                    Log.d(TAG, "joinGroup: Member ${member.displayId} already exists in group CSV")
                }
            } else {
                Log.e(TAG, "joinGroup: Target group doc $groupId does not exist in groups collection")
            }
        } catch (e: Exception) {
            Log.e(TAG, "joinGroup: Exception joining group $groupId: ${e.message}", e)
        }
    }

    suspend fun leaveGroup(groupId: String, displayId: String) {
        Log.d(TAG, "leaveGroup: Removing displayId: $displayId from group: $groupId")
        val db = FirebaseFirestore.getInstance()
        try {
            val doc = db.collection("groups").document(groupId).get().await()
            if (doc.exists()) {
                val currentMembers = doc.getString("members") ?: ""
                val membersList = currentMembers.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                if (membersList.contains(displayId)) {
                    membersList.remove(displayId)
                    val updatedCSV = membersList.joinToString(",")
                    db.collection("groups").document(groupId).update("members", updatedCSV).await()
                    Log.d(TAG, "leaveGroup: Successfully updated group members. Removed $displayId")
                } else {
                    Log.d(TAG, "leaveGroup: displayId $displayId was not a member of group $groupId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "leaveGroup: Exception removing member from group: ${e.message}", e)
        }
    }

    suspend fun createNote(note: Note): String {
        Log.d(TAG, "createNote: Storing note: noteId = ${note.noteId}")
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "noteId" to note.noteId,
            "encryptedPayload" to note.encryptedPayload,
            "iv" to note.iv,
            "createdAt" to note.createdAt,
            "expiresAt" to note.expiresAt,
            "maxViews" to note.maxViews,
            "currentViews" to note.currentViews,
            "destroyed" to note.destroyed
        )
        try {
            db.collection("notes").document(note.noteId).set(data, SetOptions.merge()).await()
            Log.d(TAG, "createNote: Successfully wrote note ${note.noteId}")
        } catch (e: Exception) {
            Log.e(TAG, "createNote: Failed to write note: ${e.message}", e)
        }
        return note.noteId
    }

    suspend fun getNote(noteId: String): Note? {
        Log.d(TAG, "getNote: Retrieving note: noteId = $noteId")
        val db = FirebaseFirestore.getInstance()
        return try {
            val doc = db.collection("notes").document(noteId).get().await()
            if (doc.exists()) {
                val destroyed = doc.getBoolean("destroyed") ?: false
                if (destroyed) {
                    Log.d(TAG, "getNote: Note ID $noteId is marked as destroyed")
                    return null
                }
                val note = Note(
                    noteId = doc.id,
                    encryptedPayload = doc.getString("encryptedPayload") ?: "",
                    iv = doc.getString("iv") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0L,
                    expiresAt = doc.getLong("expiresAt") ?: 0L,
                    maxViews = doc.getLong("maxViews")?.toInt() ?: 1,
                    currentViews = doc.getLong("currentViews")?.toInt() ?: 0,
                    destroyed = destroyed
                )
                Log.d(TAG, "getNote: Successfully retrieved undestroyed note for ID: $noteId")
                note
            } else {
                Log.d(TAG, "getNote: No note found in Firestore with ID: $noteId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getNote: Exception retrieving note: ${e.message}", e)
            null
        }
    }

    suspend fun markNoteViewed(noteId: String) {
        Log.d(TAG, "markNoteViewed: Incrementing view count for noteId: $noteId")
        val db = FirebaseFirestore.getInstance()
        try {
            val doc = db.collection("notes").document(noteId).get().await()
            if (doc.exists()) {
                val currentViews = doc.getLong("currentViews")?.toInt() ?: 0
                val maxViews = doc.getLong("maxViews")?.toInt() ?: 1
                val updatedViews = currentViews + 1
                val destroyed = updatedViews >= maxViews
                db.collection("notes").document(noteId).update(
                    "currentViews", updatedViews,
                    "destroyed", destroyed
                ).await()
                Log.d(TAG, "markNoteViewed: Note $noteId view incremented. views = $updatedViews, destroyed = $destroyed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "markNoteViewed: Exception incrementing view count for note $noteId: ${e.message}", e)
        }
    }

    suspend fun deleteNote(noteId: String) {
        Log.d(TAG, "deleteNote: Deating noteId: $noteId (marking destroyed=true)")
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("notes").document(noteId).update("destroyed", true).await()
            Log.d(TAG, "deleteNote: Successfully marked destroyed=true on note $noteId")
        } catch (e: Exception) {
            Log.e(TAG, "deleteNote: Exception deleting note $noteId: ${e.message}", e)
        }
    }

    suspend fun createSupportChat(userDisplayId: String, supportDisplayId: String): String {
        Log.d(TAG, "createSupportChat: Initiating support ticket tunnel between userDisplayId=$userDisplayId and supportDisplayId=$supportDisplayId")
        val db = FirebaseFirestore.getInstance()
        val username = getUsernameByDisplayId(userDisplayId) ?: "void_operative"
        
        // Generate a standard deterministic chatId for this user <> support pairing.
        val chatId = if (userDisplayId < supportDisplayId) "${userDisplayId}_${supportDisplayId}" else "${supportDisplayId}_${userDisplayId}"
        
        val chatData = hashMapOf(
            "chatId" to chatId,
            "participantA" to userDisplayId,
            "participantB" to supportDisplayId,
            "participantA_username" to username,
            "participantB_username" to "Void Support",
            "publicKeyA" to "MOCK_KEY_A",
            "publicKeyB" to "MOCK_KEY_B",
            "keyExchangeComplete" to false,
            "createdAt" to System.currentTimeMillis(),
            "lastMessageAt" to System.currentTimeMillis(),
            "backgroundTheme" to "DEFAULT"
        )
        
        try {
            db.collection("chats").document(chatId).set(chatData, SetOptions.merge()).await()
            Log.d(TAG, "createSupportChat: Successfully created/merged support chat: $chatId")
        } catch (e: Exception) {
            Log.e(TAG, "createSupportChat: Failed to record support chat document: ${e.message}", e)
        }
        return chatId
    }

    suspend fun sendSupportMessage(chatId: String, message: Message) {
        Log.d(TAG, "sendSupportMessage: Sending message for chatId: $chatId, messageId: ${message.messageId}")
        val db = FirebaseFirestore.getInstance()
        val msgData = hashMapOf(
            "messageId" to message.messageId,
            "chatId" to message.chatId,
            "senderId" to message.senderId,
            "encryptedPayload" to message.encryptedPayload,
            "iv" to message.iv,
            "timestamp" to message.timestamp,
            "selfDestructSeconds" to message.selfDestructSeconds,
            "destroyed" to message.destroyed,
            "readAt" to message.readAt,
            "isRead" to message.isRead
        )
        try {
            // Write to chats/{chatId}/messages/{messageId} subcollection
            db.collection("chats").document(chatId)
                .collection("messages").document(message.messageId)
                .set(msgData, SetOptions.merge()).await()
            Log.d(TAG, "sendSupportMessage: Successfully wrote message to subcollection: ${message.messageId}")
            
            // Touch/update last message timestamp in chats
            db.collection("chats").document(chatId).update("lastMessageAt", message.timestamp).await()
            Log.d(TAG, "sendSupportMessage: Updated lastMessageAt for chatId: $chatId to ${message.timestamp}")
        } catch (e: Exception) {
            Log.e(TAG, "sendSupportMessage: Failed to send support message in chat $chatId: ${e.message}", e)
        }
    }

    suspend fun updateConfig(key: String, value: String) {
        Log.d(TAG, "updateConfig: Updating configuration key: '$key' with value '$value'")
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("config").document("global")
                .set(hashMapOf(key to value), SetOptions.merge()).await()
            Log.d(TAG, "updateConfig: Merged key: '$key' successfully into Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "updateConfig: Exception during configurator update: ${e.message}", e)
        }
    }

    fun listenForAnnouncements(callback: (title: String, body: String) -> Unit) {
        Log.d(TAG, "listenForAnnouncements: Initiating dynamic listener on 'config/global'")
        val db = FirebaseFirestore.getInstance()
        db.collection("config").document("global")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "listenForAnnouncements: Snapshot listener error: ${error.message}", error)
                    // fallback safely
                    callback("ESTABLISHING QUANTUM NOISE HANDSHAKE...", "Channel offline")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val title = snapshot.getString("announcement_title") ?: "ESTABLISHING QUANTUM NOISE HANDSHAKE..."
                    val body = snapshot.getString("announcement_body") ?: "Channel online"
                    Log.d(TAG, "listenForAnnouncements: Received update. announcement_title: '$title'")
                    callback(title, body)
                } else {
                    Log.d(TAG, "listenForAnnouncements: 'global' config doc does not exist or empty. Invoking standard fallback.")
                    callback(
                        "ESTABLISHING QUANTUM NOISE HANDSHAKE...",
                        "Welcome to the Void. All messages are ephemeral and self-destruct. Establish an E2E tunnel to start secure transmission."
                    )
                }
            }
    }

    suspend fun getBannedUsers(): List<String> {
        Log.d(TAG, "getBannedUsers: Querying complete banned user list")
        val db = FirebaseFirestore.getInstance()
        return try {
            val snapshot = db.collection("banned_users").get().await()
            val list = snapshot.documents.map { it.id }
            Log.d(TAG, "getBannedUsers: Successfully fetched banned list. Count: ${list.size}")
            list
        } catch (e: Exception) {
            Log.e(TAG, "getBannedUsers: Exception retrieving banned list: ${e.message}", e)
            emptyList()
        }
    }
}
