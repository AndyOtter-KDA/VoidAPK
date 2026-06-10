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

    suspend fun fetchConfig(): Map<String, String> {
        Log.d(TAG, "fetchConfig: Fetching all config documents from Firestore")
        val db = FirebaseFirestore.getInstance()
        val map = mutableMapOf<String, String>()
        try {
            val snapshot = db.collection("config").get().await()
            for (doc in snapshot.documents) {
                Log.d(TAG, "fetchConfig: Read config doc ID '${doc.id}'")
                doc.data?.forEach { (key, value) ->
                    map[key] = value?.toString() ?: ""
                }
            }
            Log.d(TAG, "fetchConfig: Successfully fetched config docs with keys ${map.keys}")
        } catch (e: Exception) {
            Log.e(TAG, "fetchConfig failed: ${e.message}", e)
            throw e
        }
        return map
    }

    suspend fun createChat(chat: Chat) {
        Log.d(TAG, "createChat: Writing chat document chatId = ${chat.chatId}")
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("chats").document(chat.chatId).set(chat, SetOptions.merge()).await()
            Log.d(TAG, "createChat: Successfully created/merged chat with ID: ${chat.chatId}")
        } catch (e: Exception) {
            Log.e(TAG, "createChat failed for ID ${chat.chatId}: ${e.message}", e)
            throw e
        }
    }

    suspend fun uploadPublicKey(chatId: String, userDisplayId: String, publicKeyBase64: String) {
        Log.d(TAG, "uploadPublicKey: Preparing to upload key for user $userDisplayId in chat $chatId")
        val db = FirebaseFirestore.getInstance()
        try {
            val docRef = db.collection("chats").document(chatId)
            val doc = docRef.get().await()
            val updates = hashMapOf<String, Any>()
            if (doc.exists()) {
                val participantA = doc.getString("participantA") ?: ""
                val participantB = doc.getString("participantB") ?: ""
                Log.d(TAG, "uploadPublicKey: Chat exists. participantA=$participantA, participantB=$participantB")
                if (userDisplayId == participantA) {
                    updates["publicKeyA"] = publicKeyBase64
                } else if (userDisplayId == participantB) {
                    updates["publicKeyB"] = publicKeyBase64
                } else {
                    Log.w(TAG, "uploadPublicKey: user displayId $userDisplayId is not configured. Setting to empty slot.")
                    if (participantA.isEmpty() || participantA == userDisplayId) {
                        updates["publicKeyA"] = publicKeyBase64
                    } else {
                        updates["publicKeyB"] = publicKeyBase64
                    }
                }
                docRef.update(updates).await()
                Log.d(TAG, "uploadPublicKey: Key updated successfully on Firestore.")
            } else {
                Log.w(TAG, "uploadPublicKey: Chat document $chatId didn't exist yet. Creating doc.")
                updates["chatId"] = chatId
                updates["participantA"] = userDisplayId
                updates["publicKeyA"] = publicKeyBase64
                updates["keyExchangeComplete"] = false
                updates["createdAt"] = System.currentTimeMillis()
                updates["lastMessageAt"] = System.currentTimeMillis()
                updates["backgroundTheme"] = "DEFAULT"
                docRef.set(updates, SetOptions.merge()).await()
                Log.d(TAG, "uploadPublicKey: Chat document initialized with $userDisplayId as participantA & publicKeyA.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadPublicKey failed: ${e.message}", e)
            throw e
        }
    }

    fun listenForChats(userDisplayId: String): Flow<List<Chat>> = callbackFlow {
        Log.d(TAG, "listenForChats: Subscribing to real-time chats snapshot for displayId: $userDisplayId")
        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("chats")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "listenForChats: Snapshot listener error: ${error.message}", error)
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
                            Log.e(TAG, "listenForChats: Error parsing chat document ${doc.id}", e)
                            null
                        }
                    }.filter { it.participantA == userDisplayId || it.participantB == userDisplayId }
                    
                    Log.d(TAG, "listenForChats: Emitting updated chat list. Total matching: ${list.size}")
                    trySend(list)
                }
            }
        awaitClose {
            Log.d(TAG, "listenForChats: Closing snapshot listener for $userDisplayId")
            listener.remove()
        }
    }

    fun listenForMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        Log.d(TAG, "listenForMessages: Subscribing to subcollection chats/$chatId/messages")
        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "listenForMessages: Snapshot error for chatId $chatId: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            Message(
                                messageId = doc.id,
                                chatId = chatId,
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
                            Log.e(TAG, "listenForMessages: Error parsing message document ${doc.id}", e)
                            null
                        }
                    }.filter { !it.destroyed }
                     .sortedBy { it.timestamp }
                    
                    Log.d(TAG, "listenForMessages: Emitting active messages for $chatId, count: ${list.size}")
                    trySend(list)
                }
            }
        awaitClose {
            Log.d(TAG, "listenForMessages: Closing listener for chatId: $chatId")
            listener.remove()
        }
    }

    suspend fun sendMessage(chatId: String, message: Message) {
        Log.d(TAG, "sendMessage: Dispatched messageId=${message.messageId} in chatId=$chatId")
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
            db.collection("chats").document(chatId)
                .collection("messages").document(message.messageId)
                .set(msgData, SetOptions.merge()).await()
            Log.d(TAG, "sendMessage: Successfully wrote to subcollection of chatId=$chatId")

            db.collection("chats").document(chatId).update("lastMessageAt", message.timestamp).await()
            Log.d(TAG, "sendMessage: Updated lastMessageAt for chatId: $chatId to ${message.timestamp}")
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed in chat $chatId: ${e.message}", e)
            throw e
        }
    }

    suspend fun markMessageAsRead(chatId: String, messageId: String) {
        Log.d("VoidFirestore", "markMessageAsRead: Attempting to mark message read. chatId=$chatId, messageId=$messageId")
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(mapOf(
                    "isRead" to true,
                    "readAt" to System.currentTimeMillis()
                )).await()
            Log.d("VoidFirestore", "markMessageAsRead: Successfully marked message read in Firestore. messageId=$messageId")
        } catch (e: Exception) {
            Log.e("VoidFirestore", "markMessageAsRead: Failed to mark message read in Firestore. messageId=$messageId. Error: ${e.message}", e)
            throw e
        }
    }

    suspend fun registerUsername(username: String, displayId: String) {
        val normalized = username.lowercase().trim()
        Log.d(TAG, "registerUsername: Registering normalized username '$normalized' for displayId: $displayId")
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("usernames").document(normalized).set(
                hashMapOf(
                    "username" to normalized,
                    "displayId" to displayId,
                    "registeredAt" to System.currentTimeMillis()
                )
            ).await()
            Log.d(TAG, "registerUsername: Registration successful for '$normalized'")
        } catch (e: Exception) {
            Log.e(TAG, "registerUsername: Failed on '$normalized': ${e.message}", e)
            throw e
        }
    }

    suspend fun checkUsernameAvailability(username: String): Boolean {
        val normalized = username.lowercase().trim()
        Log.d(TAG, "checkUsernameAvailability: Checking availability for username '$normalized'")
        if (normalized.isEmpty()) return false
        val db = FirebaseFirestore.getInstance()
        return try {
            val doc = db.collection("usernames").document(normalized).get().await()
            val available = !doc.exists()
            Log.d(TAG, "checkUsernameAvailability: Username '$normalized' availability result: $available")
            available
        } catch (e: Exception) {
            Log.e(TAG, "checkUsernameAvailability failed for '$normalized': ${e.message}", e)
            throw e
        }
    }

    suspend fun createGroup(group: GroupChat) {
        Log.d(TAG, "createGroup: Writing new group: ID = ${group.groupId}")
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("groups").document(group.groupId).set(group, SetOptions.merge()).await()
            Log.d(TAG, "createGroup: Successfully created/merged group: ${group.groupId}")
        } catch (e: Exception) {
            Log.e(TAG, "createGroup failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun sendGroupMessage(groupId: String, message: GroupMessage) {
        Log.d(TAG, "sendGroupMessage: Transmitting group message. groupId = $groupId, messageId = ${message.messageId}")
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("groups").document(groupId)
                .collection("messages").document(message.messageId)
                .set(message, SetOptions.merge()).await()
            Log.d(TAG, "sendGroupMessage: Successfully wrote message ${message.messageId} to subcollection.")
        } catch (e: Exception) {
            Log.e(TAG, "sendGroupMessage failed: ${e.message}", e)
            throw e
        }
    }

    fun listenForGroupMessages(groupId: String): Flow<List<GroupMessage>> = callbackFlow {
        Log.d(TAG, "listenForGroupMessages: Real-time group messages subscription for groupId: $groupId")
        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("groups")
            .document(groupId)
            .collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "listenForGroupMessages: Snapshot listener failed for groupId $groupId: ${error.message}", error)
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
                            Log.e(TAG, "listenForGroupMessages: Parsing error on ${doc.id}", e)
                            null
                        }
                    }.filter { !it.destroyed }
                     .sortedBy { it.timestamp }
                    Log.d(TAG, "listenForGroupMessages: Emitting active group messages count: ${list.size}")
                    trySend(list)
                }
            }
        awaitClose {
            Log.d(TAG, "listenForGroupMessages: Closing listener for groupId: $groupId")
            listener.remove()
        }
    }

    fun getGroupMessages(groupId: String): Flow<List<GroupMessage>> {
        return listenForGroupMessages(groupId)
    }

    fun getGroups(userDisplayId: String): Flow<List<GroupChat>> = callbackFlow {
        Log.d(TAG, "getGroups: Subscribing to groups for user: $userDisplayId")
        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("groups")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
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
                            null
                        }
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun createInviteLink(groupId: String, invite: InviteLink): String {
        Log.d(TAG, "createInviteLink: Creating invite link code under group: $groupId")
        val db = FirebaseFirestore.getInstance()
        db.collection("groups").document(groupId)
            .collection("invites").document(invite.linkId)
            .set(invite, SetOptions.merge()).await()
        return "void://group/$groupId/${invite.linkId}"
    }

    suspend fun joinGroup(groupId: String, member: GroupMember) {
        Log.d(TAG, "joinGroup: Joining member ${member.displayId} to group $groupId")
        val db = FirebaseFirestore.getInstance()
        db.collection("groups").document(groupId)
            .collection("members").document(member.displayId)
            .set(member, SetOptions.merge()).await()
        
        // Update members list string if necessary, or let it merge
        val groupDoc = db.collection("groups").document(groupId).get().await()
        if (groupDoc.exists()) {
            val membersStr = groupDoc.getString("members") ?: ""
            if (!membersStr.contains(member.displayId)) {
                val updatedMembers = if (membersStr.isEmpty()) member.displayId else "$membersStr,${member.displayId}"
                db.collection("groups").document(groupId).update("members", updatedMembers).await()
            }
        }
    }

    suspend fun leaveGroup(groupId: String, userDisplayId: String) {
        Log.d(TAG, "leaveGroup: Removing member $userDisplayId from group $groupId")
        val db = FirebaseFirestore.getInstance()
        db.collection("groups").document(groupId)
            .collection("members").document(userDisplayId)
            .delete().await()

        val groupDoc = db.collection("groups").document(groupId).get().await()
        if (groupDoc.exists()) {
            val membersStr = groupDoc.getString("members") ?: ""
            val list = membersStr.split(",").filter { it != userDisplayId }
            db.collection("groups").document(groupId).update("members", list.joinToString(",")).await()
        }
    }

    suspend fun createNote(note: Note) {
        Log.d(TAG, "createNote: Storing note: noteId = ${note.noteId}")
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("notes").document(note.noteId).set(note, SetOptions.merge()).await()
            Log.d(TAG, "createNote: Successfully wrote note ${note.noteId}")
        } catch (e: Exception) {
            Log.e(TAG, "createNote failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun getNote(noteId: String): Note? {
        Log.d(TAG, "getNote: Retrieving note: noteId = $noteId")
        val db = FirebaseFirestore.getInstance()
        return try {
            val doc = db.collection("notes").document(noteId).get().await()
            if (doc.exists()) {
                val destroyed = doc.getBoolean("destroyed") ?: false
                if (destroyed) {
                    Log.d(TAG, "getNote: Note ID $noteId is already destroyed")
                    null
                } else {
                    Note(
                        noteId = doc.id,
                        encryptedPayload = doc.getString("encryptedPayload") ?: "",
                        iv = doc.getString("iv") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        expiresAt = doc.getLong("expiresAt") ?: 0L,
                        maxViews = doc.getLong("maxViews")?.toInt() ?: 1,
                        currentViews = doc.getLong("currentViews")?.toInt() ?: 0,
                        destroyed = destroyed
                    )
                }
            } else {
                Log.d(TAG, "getNote: No note found in Firestore with ID: $noteId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getNote failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun markNoteViewed(noteId: String) {
        Log.d(TAG, "markNoteViewed: Incrementing view count for noteId: $noteId")
        val db = FirebaseFirestore.getInstance()
        try {
            val docRef = db.collection("notes").document(noteId)
            val doc = docRef.get().await()
            if (doc.exists()) {
                val currentViews = doc.getLong("currentViews")?.toInt() ?: 0
                val maxViews = doc.getLong("maxViews")?.toInt() ?: 1
                val updatedViews = currentViews + 1
                val destroyed = updatedViews >= maxViews
                docRef.update(
                    mapOf(
                        "currentViews" to updatedViews,
                        "destroyed" to destroyed
                    )
                ).await()
                Log.d(TAG, "markNoteViewed: Note $noteId view incremented. views = $updatedViews, destroyed = $destroyed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "markNoteViewed error: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteNote(noteId: String) {
        Log.d(TAG, "deleteNote: Deleting absolute note from Firestore: noteId = $noteId")
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("notes").document(noteId).delete().await()
            Log.d(TAG, "deleteNote: Done deleting noteId: $noteId")
        } catch (e: Exception) {
            Log.e(TAG, "deleteNote failed: ${e.message}", e)
            throw e
        }
    }

    fun listenForAnnouncements(): Flow<Pair<Boolean, String>> = callbackFlow {
        Log.d(TAG, "listenForAnnouncements: Subscribing to config announcement_active and announcement_text")
        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("config").document("global")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "listenForAnnouncements: Error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val active = snapshot.getBoolean("announcement_active") ?: false
                    val text = snapshot.getString("announcement_text") ?: ""
                    Log.d(TAG, "listenForAnnouncements: Updated active=$active, text=$text")
                    trySend(Pair(active, text))
                } else {
                    Log.d(TAG, "listenForAnnouncements: Global config doc not found")
                    trySend(Pair(false, ""))
                }
            }
        awaitClose {
            Log.d(TAG, "listenForAnnouncements: Closing announcement listener")
            listener.remove()
        }
    }

    suspend fun getChat(chatId: String): Chat? {
        Log.d(TAG, "getChat: Fetching chat ID: $chatId")
        val db = FirebaseFirestore.getInstance()
        return try {
            val doc = db.collection("chats").document(chatId).get().await()
            if (doc.exists()) {
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
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getChat: Exception fetching chat $chatId: ${e.message}", e)
            throw e
        }
    }

    suspend fun markKeyExchangeComplete(chatId: String) {
        Log.d(TAG, "markKeyExchangeComplete: Mark completed for $chatId")
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("chats").document(chatId).update("keyExchangeComplete", true).await()
            Log.d(TAG, "markKeyExchangeComplete: Finished update on $chatId")
        } catch (e: Exception) {
            Log.e(TAG, "markKeyExchangeComplete failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun destroyMessage(chatId: String, messageId: String) {
        Log.d(TAG, "destroyMessage: Ephemeral trigger for message $messageId in chatId $chatId")
        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update("destroyed", true).await()
            Log.d(TAG, "destroyMessage: Successfully set destroyed = true on Firestore message $messageId")
        } catch (e: Exception) {
            Log.e(TAG, "destroyMessage failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun createSupportChat(userDisplayId: String, supportDisplayId: String): String {
        Log.d(TAG, "createSupportChat: Creating support chat document in Firestore. user=$userDisplayId Support=$supportDisplayId")
        val db = FirebaseFirestore.getInstance()
        val chatId = if (userDisplayId < supportDisplayId) "${userDisplayId}_${supportDisplayId}" else "${supportDisplayId}_${userDisplayId}"
        
        // Retrieve public key of identity to start key exchange properly (as participantA)
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

        val chatData = hashMapOf(
            "chatId" to chatId,
            "participantA" to userDisplayId,
            "participantB" to supportDisplayId,
            "publicKeyA" to myPublicKey,
            "publicKeyB" to "",
            "keyExchangeComplete" to false,
            "createdAt" to System.currentTimeMillis(),
            "lastMessageAt" to System.currentTimeMillis(),
            "backgroundTheme" to "DEFAULT"
        )
        try {
            db.collection("chats").document(chatId).set(chatData, SetOptions.merge()).await()
            Log.d(TAG, "createSupportChat: Successfully created/merged support chat: $chatId")
        } catch (e: Exception) {
            Log.e(TAG, "createSupportChat failed: ${e.message}", e)
            throw e
        }
        return chatId
    }
}
