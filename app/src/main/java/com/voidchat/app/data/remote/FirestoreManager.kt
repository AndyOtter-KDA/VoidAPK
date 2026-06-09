package com.voidchat.app.data.remote

import com.voidchat.app.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

object FirestoreManager {
    // Simulated remote datastores for perfect, reliable, zero-config in-app execution
    private val configs = MutableStateFlow<Map<String, String>>(
        mapOf(
            "site_url" to "https://voidchat.app",
            "support_display_id" to "VOID-SUPP-CHAT-LINE",
            "announcement_id" to "ann-v1",
            "announcement_title" to "ESTABLISHING QUANTUM NOISE HANDSHAKE...",
            "announcement_body" to "Welcome to the Void. All messages are ephemeral and self-destruct. Establish an E2E tunnel to start secure transmission.",
            "monthly_cost" to "120.0",
            "monthly_donations_fiat" to "36.0",
            "funding_percentage" to "30"
        )
    )

    private val chats = MutableStateFlow<List<Chat>>(emptyList())
    private val messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private val groupChats = MutableStateFlow<List<GroupChat>>(emptyList())
    private val groupMessages = MutableStateFlow<Map<String, List<GroupMessage>>>(emptyMap())
    private val notes = MutableStateFlow<Map<String, Note>>(emptyMap())
    private val usernames = MutableStateFlow<Map<String, String>>(emptyMap()) // username -> displayId
    private val inviteLinks = MutableStateFlow<Map<String, List<InviteLink>>>(emptyMap()) // groupId -> links
    private val bannedUsers = MutableStateFlow<List<String>>(emptyList())

    suspend fun fetchConfig(): Map<String, String> {
        return configs.value
    }

    suspend fun createChat(chat: Chat): String {
        val list = chats.value.toMutableList()
        if (list.none { it.chatId == chat.chatId }) {
            list.add(chat)
            chats.value = list
        }
        return chat.chatId
    }

    fun getChats(userDisplayId: String): Flow<List<Chat>> {
        return chats.map { list ->
            list.filter { it.participantA == userDisplayId || it.participantB == userDisplayId }
        }
    }

    suspend fun sendMessage(chatId: String, message: Message) {
        val current = messages.value.toMutableMap()
        val list = current[chatId]?.toMutableList() ?: mutableListOf()
        list.add(message)
        current[chatId] = list
        messages.value = current
        
        // Update last message timestamp
        val chatList = chats.value.toMutableList()
        val index = chatList.indexOfFirst { it.chatId == chatId }
        if (index != -1) {
            chatList[index] = chatList[index].copy(lastMessageAt = message.timestamp)
            chats.value = chatList
        }
    }

    fun getMessages(chatId: String): Flow<List<Message>> {
        return messages.map { map ->
            (map[chatId] ?: emptyList()).filter { !it.destroyed }
        }
    }

    suspend fun markMessageRead(chatId: String, messageId: String) {
        val current = messages.value.toMutableMap()
        val list = current[chatId]?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.messageId == messageId }
        if (idx != -1) {
            list[idx] = list[idx].copy(isRead = true, readAt = System.currentTimeMillis())
            current[chatId] = list
            messages.value = current
        }
    }

    suspend fun destroyMessage(chatId: String, messageId: String) {
        val current = messages.value.toMutableMap()
        val list = current[chatId]?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.messageId == messageId }
        if (idx != -1) {
            list[idx] = list[idx].copy(destroyed = true)
            current[chatId] = list
            messages.value = current
        }
    }

    suspend fun registerUsername(username: String, displayId: String): Result<Boolean> {
        val normalized = username.lowercase().trim()
        val current = usernames.value.toMutableMap()
        if (current.containsKey(normalized)) {
            return Result.failure(Exception("Username already registered"))
        }
        current[normalized] = displayId
        usernames.value = current
        return Result.success(true)
    }

    suspend fun checkUsernameAvailability(username: String): Boolean {
        return !usernames.value.containsKey(username.lowercase().trim())
    }

    suspend fun getUsernameByDisplayId(displayId: String): String? {
        return usernames.value.entries.find { it.value == displayId }?.key
    }

    suspend fun createGroup(group: GroupChat): String {
        val list = groupChats.value.toMutableList()
        list.add(group)
        groupChats.value = list
        return group.groupId
    }

    fun getGroups(userDisplayId: String): Flow<List<GroupChat>> {
        return groupChats.map { list ->
            list.filter { it.members.contains(userDisplayId) }
        }
    }

    suspend fun sendGroupMessage(groupId: String, message: GroupMessage) {
        val current = groupMessages.value.toMutableMap()
        val list = current[groupId]?.toMutableList() ?: mutableListOf()
        list.add(message)
        current[groupId] = list
        groupMessages.value = current
    }

    fun getGroupMessages(groupId: String): Flow<List<GroupMessage>> {
        return groupMessages.map { map ->
            (map[groupId] ?: emptyList()).filter { !it.destroyed }
        }
    }

    suspend fun createInviteLink(groupId: String, invite: InviteLink): String {
        val current = inviteLinks.value.toMutableMap()
        val list = current[groupId]?.toMutableList() ?: mutableListOf()
        list.add(invite)
        current[groupId] = list
        inviteLinks.value = current
        return "void://group/${groupId}/${invite.linkId}"
    }

    fun getInviteLinks(groupId: String): Flow<List<InviteLink>> {
        return inviteLinks.map { it[groupId] ?: emptyList() }
    }

    suspend fun joinGroup(groupId: String, member: GroupMember) {
        val list = groupChats.value.toMutableList()
        val idx = list.indexOfFirst { it.groupId == groupId }
        if (idx != -1) {
            val currentGroup = list[idx]
            val membersList = currentGroup.members.split(",").toMutableList()
            if (!membersList.contains(member.displayId)) {
                membersList.add(member.displayId)
                list[idx] = currentGroup.copy(members = membersList.joinToString(","))
                groupChats.value = list
            }
        }
    }

    suspend fun leaveGroup(groupId: String, displayId: String) {
        val list = groupChats.value.toMutableList()
        val idx = list.indexOfFirst { it.groupId == groupId }
        if (idx != -1) {
            val currentGroup = list[idx]
            val membersList = currentGroup.members.split(",").toMutableList()
            membersList.remove(displayId)
            list[idx] = currentGroup.copy(members = membersList.joinToString(","))
            groupChats.value = list
        }
    }

    suspend fun createNote(note: Note): String {
        val current = notes.value.toMutableMap()
        current[note.noteId] = note
        notes.value = current
        return note.noteId
    }

    suspend fun getNote(noteId: String): Note? {
        val note = notes.value[noteId]
        if (note != null && !note.destroyed) {
            return note
        }
        return null
    }

    suspend fun markNoteViewed(noteId: String) {
        val current = notes.value.toMutableMap()
        val note = current[noteId] ?: return
        val updatedViews = note.currentViews + 1
        val destroyed = updatedViews >= note.maxViews
        current[noteId] = note.copy(currentViews = updatedViews, destroyed = destroyed)
        notes.value = current
    }

    suspend fun deleteNote(noteId: String) {
        val current = notes.value.toMutableMap()
        val note = current[noteId] ?: return
        current[noteId] = note.copy(destroyed = true)
        notes.value = current
    }

    suspend fun updateConfig(key: String, value: String) {
        val current = configs.value.toMutableMap()
        current[key] = value
        configs.value = current
    }

    fun listenForAnnouncements(callback: (title: String, body: String) -> Unit) {
        val conf = configs.value
        callback(
            conf["announcement_title"] ?: "ESTABLISHING QUANTUM NOISE HANDSHAKE...",
            conf["announcement_body"] ?: "Channel online"
        )
    }

    suspend fun getBannedUsers(): List<String> {
        return bannedUsers.value
    }
}
